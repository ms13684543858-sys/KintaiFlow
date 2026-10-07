package com.example.kintaiflow.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** KF-DD-ONL-003 6.6 の計算例をそのままテストにしたもの。 */
class AttendanceCalculatorTest {

    private final AttendanceCalculator calc = new AttendanceCalculator();

    @ParameterizedTest
    @CsvSource({
            "09:00,09:00,0,0",
            "09:00,15:00,360,0",
            "09:00,15:01,316,0",
            "09:00,17:00,435,0",
            "09:00,17:01,421,0",
            "09:00,18:00,480,0",
            "09:00,18:01,481,1",
            "09:00,18:30,510,30",
            "10:00,09:00,0,0"
    })
    void calculate(String in, String out, int work, int overtime) {
        var r = calc.calculate(at(in), at(out));
        assertEquals(work, r.workMinutes());
        assertEquals(overtime, r.overtimeMinutes());
    }

    @Test
    void secondsAreTruncated() {
        var r = calc.calculate(LocalDateTime.of(2026, 10, 3, 9, 0, 59), LocalDateTime.of(2026, 10, 3, 18, 0, 0));
        assertEquals(480, r.workMinutes());
        assertEquals(0, r.overtimeMinutes());
    }

    private static LocalDateTime at(String hhmm) {
        return LocalDateTime.of(2026, 10, 3, Integer.parseInt(hhmm.substring(0, 2)), Integer.parseInt(hhmm.substring(3)));
    }

    private static LocalDateTime t(String hm) { return LocalDateTime.of(2026, 10, 5, Integer.parseInt(hm.substring(0, 2)), Integer.parseInt(hm.substring(3))); }

    private static AttendanceCalculator.Span span(String kind, String a, String b) {
        return new AttendanceCalculator.Span(kind, t(a), b == null ? null : t(b));
    }

    @Test
    void recordedLunch_equalToStatutory() {
        // 9:00-18:00、昼休み 12:00-13:00（60分）→ 540 - 60 = 480
        var wt = calc.calculate(t("09:00"), t("18:00"), java.util.List.of(span("BREAK", "12:00", "13:00")));
        assertEquals(480, wt.workMinutes());
        assertEquals(0, wt.overtimeMinutes());
    }

    @Test
    void recordedBreak_shorterThanStatutory_recordedApplies() {
        // 昼休みを30分だけ打刻した日は、打刻どおり30分を控除する（法定休憩の自動控除は休憩の打刻が無い日だけ）
        var wt = calc.calculate(t("09:00"), t("18:00"), java.util.List.of(span("BREAK", "12:00", "12:30")));
        assertEquals(510, wt.workMinutes());
        assertEquals(30, wt.overtimeMinutes());
    }

    @Test
    void noBreakRecorded_statutoryApplies() {
        var wt = calc.calculate(t("09:00"), t("18:00"), java.util.List.of(span("AWAY", "15:00", "15:30")));
        // 離席30分を引いた 510 分は8時間超なので法定休憩60分を自動控除 → 450
        assertEquals(450, wt.workMinutes());
    }

    @Test
    void recordedBreak_longerThanStatutory_recordedApplies() {
        // 昼休み 90分 → 540 - 90 = 450
        var wt = calc.calculate(t("09:00"), t("18:00"), java.util.List.of(span("BREAK", "12:00", "13:30")));
        assertEquals(450, wt.workMinutes());
    }

    @Test
    void away_isDeductedInAddition() {
        // 昼休み60分 + 離席30分 → 540 - 60 - 30 = 450
        var wt = calc.calculate(t("09:00"), t("18:00"),
                java.util.List.of(span("BREAK", "12:00", "13:00"), span("AWAY", "15:00", "15:30")));
        assertEquals(450, wt.workMinutes());
    }

    @Test
    void openSpan_isIgnored_andSpanIsClippedToClockRange() {
        var wt = calc.calculate(t("09:00"), t("18:00"), java.util.List.of(
                span("AWAY", "17:00", null), span("BREAK", "08:00", "10:00")));
        // 継続中は無視。休憩は 9:00〜10:00 の60分に切り詰め → 540 - 60 = 480
        assertEquals(480, wt.workMinutes());
    }

    @Test
    void awayShortensGrossBeforeStatutoryRule() {
        // 9:00-16:00（420分）のうち離席90分 → 330分。330 は6時間以下なので法定休憩なし → 330
        var wt = calc.calculate(t("09:00"), t("16:00"), java.util.List.of(span("AWAY", "10:00", "11:30")));
        assertEquals(330, wt.workMinutes());
    }
}
