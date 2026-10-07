package com.example.kintaiflow.service;

import com.example.kintaiflow.config.AppTime;
import com.example.kintaiflow.dto.*;
import com.example.kintaiflow.entity.AttendanceBreak;
import com.example.kintaiflow.entity.AttendanceRecord;
import com.example.kintaiflow.repository.AttendanceBreakRepository;
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
    private final AttendanceBreakRepository breakRepository;
    private final AttendanceCalculator calculator;
    private final Clock clock;

    public AttendanceService(AttendanceRecordRepository repository, AttendanceBreakRepository breakRepository,
                             AttendanceCalculator calculator, Clock clock) {
        this.repository = repository;
        this.breakRepository = breakRepository;
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
        if (rec.getId() != null && breakRepository.findFirstByAttendanceRecordIdAndEndAtIsNull(rec.getId()).isPresent()) {
            throw new BusinessException("E-022", "休憩中・離席中は退勤できません。先に休憩（離席）を終了してください。", HttpStatus.CONFLICT);
        }
        LocalDateTime out = now.isBefore(rec.getClockIn()) ? rec.getClockIn() : now;
        AttendanceCalculator.WorkTime wt = calculator.calculate(rec.getClockIn(), out, spansOf(rec));
        rec.setClockOut(out);
        rec.setWorkMinutes(wt.workMinutes());
        rec.setOvertimeMinutes(wt.overtimeMinutes());
        rec = repository.saveAndFlush(rec);
        log.info("Clock-out: userId={}, workDate={}, workMinutes={}, overtimeMinutes={}",
                userId, today, wt.workMinutes(), wt.overtimeMinutes());
        return ClockOutResponse.from(rec);
    }

    /** 休憩・離席の開始。出勤済みで未退勤、かつ他の休憩・離席が継続中でないときだけ受け付ける。 */
    @Transactional
    public BreakResponse startBreak(Long userId, String kind) {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        AttendanceRecord rec = lockWorkingRecord(userId, today);
        if (breakRepository.findFirstByAttendanceRecordIdAndEndAtIsNull(rec.getId()).isPresent()) {
            throw new BusinessException("E-020", "既に休憩中または離席中です。先に終了してください。", HttpStatus.CONFLICT);
        }
        try {
            breakRepository.saveAndFlush(new AttendanceBreak(rec.getId(), kind, now));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("E-020", "既に休憩中または離席中です。先に終了してください。", HttpStatus.CONFLICT);
        }
        log.info("Break-start: userId={}, workDate={}, kind={}", userId, today, kind);
        return toBreakResponse(rec);
    }

    /** 継続中の休憩・離席を終了する。 */
    @Transactional
    public BreakResponse endBreak(Long userId) {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        AttendanceRecord rec = lockWorkingRecord(userId, today);
        AttendanceBreak open = breakRepository.findFirstByAttendanceRecordIdAndEndAtIsNull(rec.getId())
                .orElseThrow(() -> new BusinessException("E-021", "休憩中・離席中ではありません。", HttpStatus.CONFLICT));
        open.setEndAt(now.isBefore(open.getStartAt()) ? open.getStartAt() : now);
        breakRepository.saveAndFlush(open);
        log.info("Break-end: userId={}, workDate={}, kind={}", userId, today, open.getKind());
        return toBreakResponse(rec);
    }

    /** 本日の行を行ロック付きで取得する。出勤打刻が無い（E-007）／退勤済み（E-006）なら拒否。 */
    private AttendanceRecord lockWorkingRecord(Long userId, LocalDate today) {
        AttendanceRecord rec = repository.findWithLockByUserIdAndWorkDate(userId, today).orElse(null);
        if (rec == null || rec.getClockIn() == null) {
            throw new BusinessException("E-007", "出勤打刻がないため、休憩・離席の打刻はできません。", HttpStatus.CONFLICT);
        }
        if (rec.getClockOut() != null) {
            throw alreadyStamped();
        }
        return rec;
    }

    /**
     * 休憩・離席の修正申請の検証（提出時・最終承認時に共通）。
     * 対象日に出勤打刻があること、開始 < 終了、出勤〜退勤の範囲内、他の区間と重ならないこと。
     */
    @Transactional(readOnly = true)
    public void validateBreakCorrection(Long userId, LocalDate workDate, String kind,
                                        LocalDateTime start, LocalDateTime end) {
        AttendanceRecord rec = repository.findByUserIdAndWorkDateBetweenOrderByWorkDateAsc(userId, workDate, workDate)
                .stream().findFirst().orElse(null);
        checkBreakCorrection(rec, kind, start, end);
    }

    /** 最終承認で呼ばれる。進行中の同種別の区間（終了を押し忘れたもの）があればそれを修正し、無ければ追加する。 */
    @Transactional
    public void applyBreakCorrection(Long userId, LocalDate workDate, String kind,
                                     LocalDateTime start, LocalDateTime end) {
        AttendanceRecord rec = repository.findWithLockByUserIdAndWorkDate(userId, workDate).orElse(null);
        checkBreakCorrection(rec, kind, start, end);
        AttendanceBreak open = breakRepository.findFirstByAttendanceRecordIdAndEndAtIsNull(rec.getId()).orElse(null);
        if (open != null && kind.equals(open.getKind())) {
            open.setStartAt(start);
            open.setEndAt(end);
            breakRepository.saveAndFlush(open);
        } else {
            breakRepository.saveAndFlush(new AttendanceBreak(rec.getId(), kind, start, end));
        }
        if (rec.getClockOut() != null) {   // 退勤済みの日は勤務・残業時間を再計算する
            AttendanceCalculator.WorkTime wt = calculator.calculate(rec.getClockIn(), rec.getClockOut(), spansOf(rec));
            rec.setWorkMinutes(wt.workMinutes());
            rec.setOvertimeMinutes(wt.overtimeMinutes());
            repository.saveAndFlush(rec);
        }
        log.info("Break-correction applied: userId={}, workDate={}, kind={}", userId, workDate, kind);
    }

    private void checkBreakCorrection(AttendanceRecord rec, String kind, LocalDateTime start, LocalDateTime end) {
        if (rec == null || rec.getClockIn() == null) {
            throw new BusinessException("E-003", "対象日に出勤打刻がないため、休憩・離席を修正できません。", HttpStatus.BAD_REQUEST);
        }
        if (!end.isAfter(start)) {
            throw new BusinessException("E-003", "終了時刻は開始時刻より後の時刻を指定してください。", HttpStatus.BAD_REQUEST);
        }
        if (start.isBefore(rec.getClockIn()) || (rec.getClockOut() != null && end.isAfter(rec.getClockOut()))) {
            String range = "出勤 " + rec.getClockIn().toLocalTime().truncatedTo(ChronoUnit.MINUTES)
                    + (rec.getClockOut() == null ? " 以降" : "〜退勤 " + rec.getClockOut().toLocalTime().truncatedTo(ChronoUnit.MINUTES));
            throw new BusinessException("E-003", "休憩・離席の時刻は" + range + "の範囲内で指定してください。", HttpStatus.BAD_REQUEST);
        }
        for (AttendanceBreak b : breakRepository.findByAttendanceRecordIdOrderByStartAtAsc(rec.getId())) {
            boolean sameOpen = b.getEndAt() == null && kind.equals(b.getKind());   // 修正対象そのもの
            if (sameOpen) continue;
            LocalDateTime bEnd = b.getEndAt() == null ? LocalDateTime.MAX : b.getEndAt();
            if (start.isBefore(bEnd) && end.isAfter(b.getStartAt())) {
                throw new BusinessException("E-005", "他の休憩・離席の時間帯と重なっています。", HttpStatus.CONFLICT);
            }
        }
    }

    private BreakResponse toBreakResponse(AttendanceRecord rec) {
        List<BreakItem> items = breakRepository.findByAttendanceRecordIdOrderByStartAtAsc(rec.getId()).stream()
                .map(AttendanceService::toBreakItem).toList();
        String open = items.stream().filter(b -> b.endAt() == null).map(BreakItem::kind).findFirst().orElse(null);
        return new BreakResponse(rec.getWorkDate().toString(), open, items);
    }

    private List<AttendanceCalculator.Span> spansOf(AttendanceRecord rec) {
        if (rec.getId() == null) return List.of();
        return breakRepository.findByAttendanceRecordIdOrderByStartAtAsc(rec.getId()).stream()
                .map(b -> new AttendanceCalculator.Span(b.getKind(), b.getStartAt(), b.getEndAt())).toList();
    }

    private static BreakItem toBreakItem(AttendanceBreak b) {
        return new BreakItem(b.getKind(), AppTime.toIso(b.getStartAt()), AppTime.toIso(b.getEndAt()));
    }

    @Transactional(readOnly = true)
    public MonthlyAttendanceResponse getMonthly(Long userId, String month) {
        YearMonth ym = parseMonth(month);
        List<AttendanceRecord> recs = repository
                .findByUserIdAndWorkDateBetweenOrderByWorkDateAsc(userId, ym.atDay(1), ym.atEndOfMonth());
        // 休憩・離席は月分をまとめて取得する（1日ごとに問い合わせない）
        java.util.Map<Long, List<BreakItem>> breaks = recs.isEmpty() ? java.util.Map.of()
                : breakRepository.findByAttendanceRecordIdInOrderByStartAtAsc(recs.stream().map(AttendanceRecord::getId).toList())
                        .stream().collect(java.util.stream.Collectors.groupingBy(AttendanceBreak::getAttendanceRecordId,
                                java.util.stream.Collectors.mapping(AttendanceService::toBreakItem, java.util.stream.Collectors.toList())));
        List<AttendanceItem> items = recs.stream()
                .map(r -> toItem(r, breaks.getOrDefault(r.getId(), List.of()))).toList();
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

    private static AttendanceItem toItem(AttendanceRecord r, List<BreakItem> breaks) {
        return new AttendanceItem(r.getWorkDate().toString(), AppTime.toIso(r.getClockIn()),
                AppTime.toIso(r.getClockOut()), r.getWorkMinutes(), r.getOvertimeMinutes(), breaks);
    }

    private static BusinessException alreadyStamped() {
        return new BusinessException("E-006", "本日は既に打刻済みです。", HttpStatus.CONFLICT);
    }

    private static BusinessException badMonth(String msg) {
        return new BusinessException("E-002", msg, HttpStatus.BAD_REQUEST);
    }
}
