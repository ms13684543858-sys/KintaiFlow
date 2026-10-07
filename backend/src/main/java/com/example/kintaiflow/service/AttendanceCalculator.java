package com.example.kintaiflow.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** 勤務時間・残業時間の計算（状態を持たない純粋な計算）。休憩は労基法34条に従い自動控除する。 */
@Component
public class AttendanceCalculator {

    private static final int STANDARD_MINUTES = 480;
    private static final int BREAK_OVER_6H = 45;
    private static final int BREAK_OVER_8H = 60;
    private static final int THRESHOLD_6H = 360;
    private static final int THRESHOLD_8H = 480;

    public record WorkTime(int workMinutes, int overtimeMinutes) {}

    /** 休憩・離席の1区間（kind は BREAK / AWAY。end が null の区間は計算対象外）。 */
    public record Span(String kind, LocalDateTime start, LocalDateTime end) {}

    public WorkTime calculate(LocalDateTime clockIn, LocalDateTime clockOut) {
        return calculate(clockIn, clockOut, java.util.List.of());
    }

    /**
     * 休憩・離席の打刻を反映した勤務時間。
     * ・日中離席（AWAY）は全て控除する。
     * ・休憩（BREAK）は、打刻した合計を控除する。休憩の打刻が無い日は法定休憩（労基法34条：6時間超45分・8時間超60分）を自動控除する。
     * ・各区間は出勤〜退勤の範囲に切り詰めて数える。
     */
    public WorkTime calculate(LocalDateTime clockIn, LocalDateTime clockOut, java.util.List<Span> spans) {
        LocalDateTime in = clockIn.truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime out = clockOut.truncatedTo(ChronoUnit.MINUTES);
        long gross = Duration.between(in, out).toMinutes();
        if (gross < 0) gross = 0;
        long recordedBreak = 0;
        long away = 0;
        for (Span sp : spans) {
            if (sp.end() == null) continue;
            LocalDateTime s = sp.start().truncatedTo(ChronoUnit.MINUTES);
            LocalDateTime e = sp.end().truncatedTo(ChronoUnit.MINUTES);
            if (s.isBefore(in)) s = in;
            if (e.isAfter(out)) e = out;
            long m = Duration.between(s, e).toMinutes();
            if (m <= 0) continue;
            if ("AWAY".equals(sp.kind())) away += m; else recordedBreak += m;
        }
        long net = gross - away;
        int statutory = net > THRESHOLD_8H ? BREAK_OVER_8H : (net > THRESHOLD_6H ? BREAK_OVER_6H : 0);
        // 休憩を1回でも打刻した日は打刻どおり控除する。打刻が無い日だけ法定休憩を自動控除する。
        long breakMinutes = recordedBreak > 0 ? recordedBreak : statutory;
        int work = (int) Math.max(0, net - breakMinutes);
        int overtime = Math.max(0, work - STANDARD_MINUTES);
        return new WorkTime(work, overtime);
    }
}
