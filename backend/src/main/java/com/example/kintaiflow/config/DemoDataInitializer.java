package com.example.kintaiflow.config;

import com.example.kintaiflow.dto.CreateRequestRequest;
import com.example.kintaiflow.dto.PendingApprovalItem;
import com.example.kintaiflow.entity.AttendanceRecord;
import com.example.kintaiflow.entity.LeaveType;
import com.example.kintaiflow.entity.RequestType;
import com.example.kintaiflow.entity.User;
import com.example.kintaiflow.repository.AttendanceRecordRepository;
import com.example.kintaiflow.repository.LeaveTypeRepository;
import com.example.kintaiflow.repository.RequestRepository;
import com.example.kintaiflow.repository.UserRepository;
import com.example.kintaiflow.service.ApprovalService;
import com.example.kintaiflow.service.AttendanceCalculator;
import com.example.kintaiflow.service.HolidayService;
import com.example.kintaiflow.service.RequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * デモ用：画面が空にならないよう、代表アカウントに直近の出勤記録と、状態の異なる申請を作る。
 * DevDataInitializer / DevLeaveBalanceInitializer の後に、出勤記録も申請も1件も無いときだけ1回動く（kintaiflow.dev.seed-enabled=true のときのみ）。
 * 申請と承認は直接 INSERT せず、実際の業務サービス（RequestService / ApprovalService）を通して作る。
 * 本番では使用しない。
 */
@ConditionalOnProperty(name = "kintaiflow.dev.seed-enabled", havingValue = "true")
@Component
@Order(4)
public class DemoDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    private static final int HISTORY_DAYS = 45;
    /** 出勤記録を作る代表アカウント（存在するものだけ）。dev003〜dev008 は月次集計の表を埋めるため。 */
    private static final List<String> ATTENDANCE_EMAILS = List.of("taro@example.com", "hanako@example.com",
            "manager@example.com", "admin@example.com",
            "dev003@example.com", "dev004@example.com", "dev005@example.com",
            "dev006@example.com", "dev007@example.com", "dev008@example.com");

    private final UserRepository userRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final RequestRepository requestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final AttendanceCalculator calculator;
    private final HolidayService holidayService;
    private final RequestService requestService;
    private final ApprovalService approvalService;
    private final Clock clock;

    public DemoDataInitializer(UserRepository userRepository, AttendanceRecordRepository attendanceRepository,
                               RequestRepository requestRepository, LeaveTypeRepository leaveTypeRepository,
                               AttendanceCalculator calculator, HolidayService holidayService,
                               RequestService requestService, ApprovalService approvalService, Clock clock) {
        this.userRepository = userRepository;
        this.attendanceRepository = attendanceRepository;
        this.requestRepository = requestRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.calculator = calculator;
        this.holidayService = holidayService;
        this.requestService = requestService;
        this.approvalService = approvalService;
        this.clock = clock;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0 || attendanceRepository.count() > 0 || requestRepository.count() > 0) {
            return;
        }
        try {
            int rows = seedAttendance();
            int reqs = seedRequests();
            log.info("Demo seed: attendance rows={}, requests={}", rows, reqs);
        } catch (Exception e) {
            // デモデータの失敗でアプリの起動を止めない
            log.warn("Demo seed skipped: {}", e.toString());
        }
    }

    private int seedAttendance() {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(HISTORY_DAYS);
        Set<LocalDate> holidays = holidayService.getHolidayDates(from, today);
        int rows = 0;
        for (String email : ATTENDANCE_EMAILS) {
            User u = userRepository.findByEmail(email).orElse(null);
            if (u == null) continue;
            Random rnd = new Random(u.getId() * 31L + 7);   // ユーザーごとに毎回同じ内容になる
            for (LocalDate d = from; d.isBefore(today); d = d.plusDays(1)) {   // 当日は空けて、デモで出勤打刻できるようにする
                if (!isBusinessDay(d, holidays)) continue;
                int inMinute = 8 * 60 + 45 + rnd.nextInt(30);                          // 8:45〜9:14
                int roll = rnd.nextInt(100);
                int outMinute = roll < 75 ? 18 * 60 + rnd.nextInt(60)                   // 18:00〜18:59（定時）
                        : 19 * 60 + 30 + rnd.nextInt(120);                              // 19:30〜21:29（残業）
                LocalDateTime in = d.atTime(LocalTime.of(inMinute / 60, inMinute % 60));
                LocalDateTime out = d.atTime(LocalTime.of(outMinute / 60, outMinute % 60));
                AttendanceRecord rec = new AttendanceRecord(u.getId(), d);
                rec.setClockIn(in);
                rec.setClockOut(out);
                AttendanceCalculator.WorkTime wt = calculator.calculate(in, out);
                rec.setWorkMinutes(wt.workMinutes());
                rec.setOvertimeMinutes(wt.overtimeMinutes());
                attendanceRepository.save(rec);
                rows++;
            }
        }
        return rows;
    }

    /** 状態の異なる申請を作る：承認済み／上長の承認待ち／最終承認待ち／打刻修正の承認待ち。 */
    private int seedRequests() {
        LocalDate today = LocalDate.now(clock);
        Set<LocalDate> holidays = holidayService.getHolidayDates(today, today.plusDays(60));
        User taro = userRepository.findByEmail("taro@example.com").orElse(null);
        User hanako = userRepository.findByEmail("hanako@example.com").orElse(null);
        User manager = userRepository.findByEmail("manager@example.com").orElse(null);
        User admin = userRepository.findByEmail("admin@example.com").orElse(null);
        LeaveType annual = leaveTypeRepository.findByName("年次有給休暇").orElse(null);
        if (taro == null || hanako == null || manager == null || admin == null || annual == null) return 0;
        int n = 0;

        // ① 山田 太郎：承認済みの年休（上長 → 管理者の2段承認）
        LocalDate d1 = nextBusinessDay(today.plusDays(7), holidays);
        Long r1 = createLeave(taro, annual, d1, d1, "FULL", "家族の用事のため");
        if (r1 != null) { approve(manager, r1); approve(admin, r1); n++; }

        // ② 山田 太郎：上長の承認待ちの年休（連休）
        LocalDate d2 = nextBusinessDay(today.plusDays(21), holidays);
        LocalDate d2end = nextBusinessDay(d2.plusDays(1), holidays);
        if (createLeave(taro, annual, d2, d2end, "FULL", "旅行のため") != null) n++;

        // ③ 鈴木 花子：最終承認待ち（上長は承認済み）の半日休
        LocalDate d3 = nextBusinessDay(today.plusDays(10), holidays);
        Long r3 = createLeave(hanako, annual, d3, d3, "AM", "通院のため");
        if (r3 != null) { approve(manager, r3); n++; }

        // ④ 鈴木 花子：上長の承認待ちの年休
        LocalDate d4 = nextBusinessDay(today.plusDays(28), holidays);
        if (createLeave(hanako, annual, d4, d4, "FULL", "私用のため") != null) n++;

        // ⑤ 山田 太郎：直近の営業日の打刻修正申請（退勤の打刻漏れ）
        LocalDate past = today.minusDays(1);
        while (!isBusinessDay(past, holidayService.getHolidayDates(past.minusDays(7), today))) past = past.minusDays(1);
        try {
            requestService.create(taro.getId(), new CreateRequestRequest(RequestType.CLOCK_CORRECTION, null,
                    past, null, null, "09:00", "18:30", null, null, null, "退勤の打刻を忘れたため", false));
            n++;
        } catch (Exception e) {
            log.warn("Demo seed: clock correction skipped: {}", e.toString());
        }
        return n;
    }

    private Long createLeave(User who, LeaveType type, LocalDate start, LocalDate end, String unit, String reason) {
        try {
            return requestService.create(who.getId(), new CreateRequestRequest(RequestType.LEAVE, type.getId(),
                    start, end, unit, null, null, null, null, null, reason, false)).requestId();
        } catch (Exception e) {
            log.warn("Demo seed: leave request skipped: {}", e.toString());
            return null;
        }
    }

    private void approve(User approver, Long requestId) {
        try {
            for (PendingApprovalItem it : approvalService.listPending(approver.getId(), null, null).items()) {
                if (it.requestId().equals(requestId)) {
                    approvalService.approve(it.stepId(), approver.getId(), "承認します");
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("Demo seed: approval skipped: {}", e.toString());
        }
    }

    private static boolean isBusinessDay(LocalDate d, Set<LocalDate> holidays) {
        DayOfWeek w = d.getDayOfWeek();
        return w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY && !holidays.contains(d);
    }

    private static LocalDate nextBusinessDay(LocalDate d, Set<LocalDate> holidays) {
        while (!isBusinessDay(d, holidays)) d = d.plusDays(1);
        return d;
    }
}
