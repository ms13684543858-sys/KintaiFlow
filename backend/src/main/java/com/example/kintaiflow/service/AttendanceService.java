package com.example.kintaiflow.service;

import com.example.kintaiflow.config.AppTime;
import com.example.kintaiflow.dto.*;
import com.example.kintaiflow.entity.AttendanceRecord;
import com.example.kintaiflow.exception.BusinessException;
import com.example.kintaiflow.repository.AttendanceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** ONL-002 出勤打刻 / ONL-003 退勤打刻 / ONL-004 月次勤怠取得。時刻は常にサーバー（JST）で決める。 */
@Service
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);

    private final AttendanceRecordRepository repository;
    private final AttendanceCalculator calculator;
    private final Clock clock;

    public AttendanceService(AttendanceRecordRepository repository, AttendanceCalculator calculator, Clock clock) {
        this.repository = repository;
        this.calculator = calculator;
        this.clock = clock;
    }

    @Transactional
    public ClockInResponse clockIn(Long userId) {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);

        AttendanceRecord rec = repository.findWithLockByUserIdAndWorkDate(userId, today).orElse(null);
        if (rec != null && rec.getClockIn() != null) {
            throw alreadyStamped();
        }
        if (rec == null) {
            rec = new AttendanceRecord(userId, today);
        }
        rec.setClockIn(now);
        try {
            rec = repository.saveAndFlush(rec);
        } catch (DataIntegrityViolationException e) {
            // 同時リクエストで先に INSERT された（uq_attendance_records_1）
            throw alreadyStamped();
        }
        log.info("Clock-in: userId={}, workDate={}", userId, today);
        return ClockInResponse.from(rec);
    }

    @Transactional
    public ClockOutResponse clockOut(Long userId) {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);

        AttendanceRecord rec = repository.findWithLockByUserIdAndWorkDate(userId, today).orElse(null);
        if (rec == null || rec.getClockIn() == null) {
            throw new BusinessException("E-007", "出勤打刻がないため、退勤打刻できません。", HttpStatus.CONFLICT);
        }
        if (rec.getClockOut() != null) {
            throw alreadyStamped();
        }
        LocalDateTime out = now.isBefore(rec.getClockIn()) ? rec.getClockIn() : now;
        AttendanceCalculator.WorkTime wt = calculator.calculate(rec.getClockIn(), out);
        rec.setClockOut(out);
        rec.setWorkMinutes(wt.workMinutes());
        rec.setOvertimeMinutes(wt.overtimeMinutes());
        rec = repository.saveAndFlush(rec);
        log.info("Clock-out: userId={}, workDate={}, workMinutes={}, overtimeMinutes={}",
                userId, today, wt.workMinutes(), wt.overtimeMinutes());
        return ClockOutResponse.from(rec);
    }

    @Transactional(readOnly = true)
    public MonthlyAttendanceResponse getMonthly(Long userId, String month) {
        YearMonth ym = parseMonth(month);
        List<AttendanceItem> items = repository
                .findByUserIdAndWorkDateBetweenOrderByWorkDateAsc(userId, ym.atDay(1), ym.atEndOfMonth())
                .stream().map(AttendanceService::toItem).toList();
        int work = items.stream().mapToInt(i -> i.workMinutes() == null ? 0 : i.workMinutes()).sum();
        int overtime = items.stream().mapToInt(i -> i.overtimeMinutes() == null ? 0 : i.overtimeMinutes()).sum();
        return new MonthlyAttendanceResponse(items, work, overtime);
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) throw badMonth("対象月は必須入力です。");
        String m = month.trim();
        if (!m.matches("^\\d{4}-(0[1-9]|1[0-2])$")) throw badMonth("対象月は yyyy-MM 形式で入力してください。");
        YearMonth ym = YearMonth.parse(m);
        if (ym.isAfter(YearMonth.from(LocalDate.now(clock)))) throw badMonth("対象月に未来の月は指定できません。");
        return ym;
    }

    private static AttendanceItem toItem(AttendanceRecord r) {
        return new AttendanceItem(r.getWorkDate().toString(), AppTime.toIso(r.getClockIn()),
                AppTime.toIso(r.getClockOut()), r.getWorkMinutes(), r.getOvertimeMinutes());
    }

    private static BusinessException alreadyStamped() {
        return new BusinessException("E-006", "本日は既に打刻済みです。", HttpStatus.CONFLICT);
    }

    private static BusinessException badMonth(String msg) {
        return new BusinessException("E-002", msg, HttpStatus.BAD_REQUEST);
    }
}
