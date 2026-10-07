package com.example.kintaiflow.service;

import com.example.kintaiflow.batch.BatchResult;
import com.example.kintaiflow.batch.BatchStatus;
import com.example.kintaiflow.config.AppTime;
import com.example.kintaiflow.entity.LeaveBalance;
import com.example.kintaiflow.entity.LeaveType;
import com.example.kintaiflow.entity.NotificationType;
import com.example.kintaiflow.entity.User;
import com.example.kintaiflow.repository.LeaveTypeRepository;
import com.example.kintaiflow.repository.RequestRepository;
import com.example.kintaiflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** BAT-002 年5日取得義務アラートの判定ロジック（DB なし）。実行時点は 2026-07-01 03:00 JST に固定。 */
class MandatoryLeaveAlertServiceTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-07-01");
    private static final BigDecimal FIVE = new BigDecimal("5.0");

    private final LeaveTypeRepository leaveTypeRepository = mock(LeaveTypeRepository.class);
    private final LeaveBalanceService leaveBalanceService = mock(LeaveBalanceService.class);
    private final RequestRepository requestRepository = mock(RequestRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-30T18:00:00Z"), AppTime.ZONE);   // 2026-07-01 03:00 JST

    // 本物の UnitService を使い、通知の宛先・件数・重複判定を NotificationService のモックで確認する
    private final MandatoryLeaveAlertUnitService unit = new MandatoryLeaveAlertUnitService(notificationService);
    private final MandatoryLeaveAlertService service = new MandatoryLeaveAlertService(
            leaveTypeRepository, leaveBalanceService, requestRepository, userRepository, unit, clock, FIVE, 9);

    private static LeaveType annual() {
        LeaveType t = new LeaveType();
        ReflectionTestUtils.setField(t, "id", 1L);
        t.setName(LeaveBalanceService.ANNUAL_LEAVE_NAME);
        t.setIsActive(true);
        t.setMaxDaysRule("LIMITED");
        return t;
    }

    private static User user(long id, String name) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        u.setName(name);
        return u;
    }

    private static LeaveBalance balance(long userId, String grantedOn, String days) {
        LeaveBalance b = new LeaveBalance();
        b.setUserId(userId);
        b.setGrantedOn(LocalDate.parse(grantedOn));
        b.setGrantedDays(new BigDecimal(days));
        return b;
    }

    private void givenBase(List<LeaveBalance> rows, List<User> admins, List<User> names) {
        when(leaveTypeRepository.findByName(LeaveBalanceService.ANNUAL_LEAVE_NAME)).thenReturn(Optional.of(annual()));
        when(leaveBalanceService.findAlertBaseRows(TODAY)).thenReturn(rows);
        when(userRepository.findByRoleAndStatus("ADMIN", "ACTIVE")).thenReturn(admins);
        when(userRepository.findAllById(any())).thenReturn(names);
    }

    private void givenTaken(long userId, String taken) {
        when(requestRepository.sumApprovedLeaveDays(eq(userId), eq(1L), any(), any()))
                .thenReturn(taken == null ? null : new BigDecimal(taken));
    }

    @Test
    void notifiesSelfAndAdmins_whenBelowFiveAfterNineMonths() {
        // 付与 2025-10-01 → 9か月後の 2026-07-01 に到達。取得 2.5 日 < 5
        givenBase(List.of(balance(12, "2025-10-01", "10.0")), List.of(user(100, "管理者A"), user(101, "管理者B")),
                List.of(user(12, "山田 太郎")));
        givenTaken(12, "2.5");

        BatchResult r = service.execute(TODAY);

        assertEquals(BatchStatus.SUCCESS, r.status());
        assertEquals(1, r.targets());
        assertEquals(1, r.succeeded());
        assertTrue(r.summary().endsWith("compliant=0 notifications=3"));   // 本人1 + 管理者2
        for (long to : new long[]{12, 100, 101})
            verify(notificationService).notify(eq(to), eq(NotificationType.LEAVE_ALERT), anyString(), eq(null));
        // 管理者宛て本文には対象者の氏名が入り、本人宛てには入らない
        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(notificationService, times(3)).notify(anyLong(), anyString(), msg.capture(), any());
        assertTrue(msg.getAllValues().stream().filter(m -> m.contains("山田 太郎さん")).count() == 2);
    }

    @Test
    void nothingBeforeNineMonths() {
        // 付与 2025-10-02 → 9か月後は 2026-07-02 で、今日(07-01)はまだ
        givenBase(List.of(balance(12, "2025-10-02", "10.0")), List.of(user(100, "管理者A")), List.of(user(12, "山田 太郎")));

        BatchResult r = service.execute(TODAY);

        assertEquals(0, r.targets());
        verify(notificationService, never()).notify(anyLong(), anyString(), anyString(), any());
        verify(requestRepository, never()).sumApprovedLeaveDays(anyLong(), anyLong(), any(), any());
    }

    @Test
    void compliantWhenFiveOrMore() {
        givenBase(List.of(balance(12, "2025-10-01", "10.0")), List.of(user(100, "管理者A")), List.of(user(12, "山田 太郎")));
        givenTaken(12, "5.0");   // ちょうど5日は達成済み

        BatchResult r = service.execute(TODAY);

        assertEquals(0, r.targets());
        assertTrue(r.summary().endsWith("compliant=1 notifications=0"));
        verify(notificationService, never()).notify(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void nullTakenIsTreatedAsZero() {
        givenBase(List.of(balance(12, "2025-10-01", "10.0")), List.of(), List.of(user(12, "山田 太郎")));
        givenTaken(12, null);   // 取得実績なし（sum が null）

        BatchResult r = service.execute(TODAY);

        assertEquals(1, r.targets());
        verify(notificationService).notify(eq(12L), eq(NotificationType.LEAVE_ALERT), anyString(), eq(null));   // 管理者0名でも本人には送る
    }

    @Test
    void usesOnlyLatestGrantPerUser() {
        // findAlertBaseRows は granted_on 降順。先頭（最新）だけを基準行にする → 古い行は無視
        givenBase(List.of(balance(12, "2026-04-01", "11.0"), balance(12, "2025-10-01", "10.0")),
                List.of(), List.of(user(12, "山田 太郎")));

        BatchResult r = service.execute(TODAY);

        assertEquals(1, r.scanned());
        assertEquals(0, r.targets());   // 最新の付与(2026-04-01)はまだ9か月未満
    }

    @Test
    void adminApplicantGetsSingleNotification() {
        // 対象者本人が ADMIN のとき、本人向けの1件だけにする（管理者向けの重複は送らない）
        givenBase(List.of(balance(100, "2025-10-01", "10.0")), List.of(user(100, "管理者A"), user(101, "管理者B")),
                List.of(user(100, "管理者A")));
        givenTaken(100, "0.0");

        BatchResult r = service.execute(TODAY);

        assertTrue(r.summary().endsWith("notifications=2"));   // 本人(100) + 他の管理者(101)
        verify(notificationService, times(1)).notify(eq(100L), anyString(), anyString(), any());
        verify(notificationService, times(1)).notify(eq(101L), anyString(), anyString(), any());
    }

    @Test
    void skipsRecipientsAlreadyNotifiedThisMonth() {
        givenBase(List.of(balance(12, "2025-10-01", "10.0")), List.of(user(100, "管理者A")), List.of(user(12, "山田 太郎")));
        givenTaken(12, "1.0");
        String key = MandatoryLeaveMessages.key(12L, LocalDate.parse("2025-10-01"));
        when(notificationService.existsMonthly(12L, NotificationType.LEAVE_ALERT, key, YearMonth.of(2026, 7))).thenReturn(true);

        BatchResult r = service.execute(TODAY);

        verify(notificationService, never()).notify(eq(12L), anyString(), anyString(), any());   // 本人は送信済み
        verify(notificationService).notify(eq(100L), eq(NotificationType.LEAVE_ALERT), anyString(), eq(null));   // 管理者は未送信
        assertEquals(1, r.succeeded());
    }

    @Test
    void allAlreadyNotifiedIsSkipped() {
        givenBase(List.of(balance(12, "2025-10-01", "10.0")), List.of(user(100, "管理者A")), List.of(user(12, "山田 太郎")));
        givenTaken(12, "1.0");
        when(notificationService.existsMonthly(anyLong(), anyString(), anyString(), any(YearMonth.class))).thenReturn(true);

        BatchResult r = service.execute(TODAY);

        assertEquals(1, r.skipped());
        assertEquals(0, r.succeeded());
        assertEquals(BatchStatus.SUCCESS, r.status());
        verify(notificationService, never()).notify(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void oneFailureDoesNotStopOthers() {
        givenBase(List.of(balance(12, "2025-10-01", "10.0"), balance(13, "2025-10-01", "10.0")),
                List.of(), List.of(user(12, "山田 太郎"), user(13, "鈴木 花子")));
        givenTaken(12, "1.0");
        givenTaken(13, "1.0");
        org.mockito.Mockito.doThrow(new IllegalStateException("db down"))
                .when(notificationService).notify(eq(12L), anyString(), anyString(), any());

        BatchResult r = service.execute(TODAY);

        assertEquals(2, r.targets());
        assertEquals(1, r.failed());
        assertEquals(1, r.succeeded());
        assertEquals(BatchStatus.PARTIAL, r.status());
        verify(notificationService).notify(eq(13L), anyString(), anyString(), any());   // 失敗の次の人も処理された
    }

    @Test
    void failsWhenAnnualLeaveTypeMissing() {
        when(leaveTypeRepository.findByName(LeaveBalanceService.ANNUAL_LEAVE_NAME)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> service.execute(TODAY));
    }
}
