package com.example.kintaiflow.service;

import com.example.kintaiflow.config.AppTime;
import com.example.kintaiflow.entity.AttendanceRecord;
import com.example.kintaiflow.exception.BusinessException;
import com.example.kintaiflow.repository.AttendanceRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** KF-DD-ONL-002/003/004 の単体テスト観点の主要部分。時刻は 2026-10-03 09:00 JST に固定。 */
class AttendanceServiceTest {

    private final AttendanceRecordRepository repo = mock(AttendanceRecordRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"), AppTime.ZONE);
    private final AttendanceService service = new AttendanceService(repo, new AttendanceCalculator(), clock);
    private final LocalDate today = LocalDate.of(2026, 10, 3);

    private void stubSave() {
        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void clockIn_newRow() {
        when(repo.findWithLockByUserIdAndWorkDate(1L, today)).thenReturn(Optional.empty());
        stubSave();
        var res = service.clockIn(1L);
        assertEquals("2026-10-03", res.workDate());
        assertEquals("2026-10-03T09:00:00+09:00", res.clockIn());
    }

    @Test
    void clockIn_twice_isConflict() {
        var rec = new AttendanceRecord(1L, today);
        rec.setClockIn(LocalDateTime.of(2026, 10, 3, 8, 50));
        when(repo.findWithLockByUserIdAndWorkDate(1L, today)).thenReturn(Optional.of(rec));
        var e = assertThrows(BusinessException.class, () -> service.clockIn(1L));
        assertEquals("E-006", e.getCode());
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void clockIn_uniqueViolation_isConflict() {
        when(repo.findWithLockByUserIdAndWorkDate(1L, today)).thenReturn(Optional.empty());
        when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));
        assertEquals("E-006", assertThrows(BusinessException.class, () -> service.clockIn(1L)).getCode());
    }

    @Test
    void clockOut_withoutClockIn() {
        when(repo.findWithLockByUserIdAndWorkDate(1L, today)).thenReturn(Optional.empty());
        assertEquals("E-007", assertThrows(BusinessException.class, () -> service.clockOut(1L)).getCode());
    }

    @Test
    void clockOut_twice() {
        var rec = new AttendanceRecord(1L, today);
        rec.setClockIn(LocalDateTime.of(2026, 10, 3, 0, 0));
        rec.setClockOut(LocalDateTime.of(2026, 10, 3, 8, 0));
        when(repo.findWithLockByUserIdAndWorkDate(1L, today)).thenReturn(Optional.of(rec));
        assertEquals("E-006", assertThrows(BusinessException.class, () -> service.clockOut(1L)).getCode());
    }

    @Test
    void clockOut_beforeClockIn_isCorrected() {
        var rec = new AttendanceRecord(1L, today);
        rec.setClockIn(LocalDateTime.of(2026, 10, 3, 18, 31));
        when(repo.findWithLockByUserIdAndWorkDate(1L, today)).thenReturn(Optional.of(rec));
        stubSave();
        var res = service.clockOut(1L);
        assertEquals(0, res.workMinutes());
        assertEquals(0, res.overtimeMinutes());
    }

    @Test
    void monthly_validation() {
        assertEquals("E-002", assertThrows(BusinessException.class, () -> service.getMonthly(1L, null)).getCode());
        assertEquals("E-002", assertThrows(BusinessException.class, () -> service.getMonthly(1L, "2026-13")).getCode());
        assertEquals("E-002", assertThrows(BusinessException.class, () -> service.getMonthly(1L, "2026-11")).getCode());
        when(repo.findByUserIdAndWorkDateBetweenOrderByWorkDateAsc(any(), any(), any())).thenReturn(java.util.List.of());
        var res = service.getMonthly(1L, "2026-10");
        assertEquals(0, res.totalWorkMinutes());
        assertTrue(res.records().isEmpty());
    }
}
