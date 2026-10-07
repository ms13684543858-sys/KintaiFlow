package com.example.kintaiflow.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BAT-001 応当日の判定・付与日数（KF-DD-BAT-001 の 6.5 応当日の具体例に対応）。 */
class LeaveGrantCalculatorTest {

    private static LocalDate d(String s) { return LocalDate.parse(s); }

    @ParameterizedTest
    @CsvSource({
            // 入社日, 判定日, 期待する付与回数（-1 は応当日でない）
            "2026-04-01, 2026-10-01, 0",
            "2026-04-01, 2027-10-01, 1",
            "2026-04-01, 2030-10-01, 4",
            "2026-04-01, 2026-09-30, -1",   // 初回の前日
            "2026-04-01, 2026-10-02, -1",   // 初回の翌日
            "2026-04-01, 2026-04-01, -1",   // 入社日当日は初回より前
            "2026-08-31, 2027-02-28, 0",    // 月末入社は月末へ丸める
            "2026-08-31, 2028-02-28, 1",    // 2028 はうるう年だが丸めは累積しない（2/29 ではない）
            "2023-08-31, 2024-02-29, 0",    // 初回がうるう日
            "2023-08-31, 2025-02-28, 1",    // 翌年は 2/28
            "2023-08-31, 2028-02-29, 4",    // 4年後に元の 2/29 へ戻る
            "2024-02-29, 2024-08-29, 0",    // 入社日が 2/29 でも 6か月後は丸め不要
            "2025-12-31, 2026-06-30, 0"     // 30日の月へ
    })
    void indexOf(String hire, String date, int expected) {
        OptionalInt r = LeaveGrantCalculator.indexOf(d(hire), d(date));
        if (expected < 0) assertTrue(r.isEmpty(), "応当日ではないはず");
        else assertEquals(expected, r.orElseThrow());
    }

    @Test
    void dueDates_catchUpWindowIncludesBothEnds() {
        // 入社 2026-04-01 の初回は 2026-10-01。今日=10/03・遡り7日なら 9/26〜10/03 に含まれる
        var due = LeaveGrantCalculator.dueDates(d("2026-04-01"), d("2026-09-26"), d("2026-10-03"));
        assertEquals(1, due.size());
        assertEquals(d("2026-10-01"), due.get(0).grantDate());
        assertEquals(0, due.get(0).index());
        // 窓の端（from == 応当日 / to == 応当日）も含む
        assertEquals(1, LeaveGrantCalculator.dueDates(d("2026-04-01"), d("2026-10-01"), d("2026-10-08")).size());
        assertEquals(1, LeaveGrantCalculator.dueDates(d("2026-04-01"), d("2026-09-24"), d("2026-10-01")).size());
        // 窓の外は空
        assertEquals(List.of(), LeaveGrantCalculator.dueDates(d("2026-04-01"), d("2026-10-02"), d("2026-10-09")));
    }

    @ParameterizedTest
    @CsvSource({"0,10.0", "1,11.0", "2,12.0", "3,14.0", "4,16.0", "5,18.0", "6,20.0", "7,20.0", "20,20.0"})
    void grantDays(int index, String expected) {
        assertEquals(new BigDecimal(expected), LeaveGrantCalculator.grantDays(index));
    }

    @Test
    void label() {
        assertEquals("6か月", LeaveGrantCalculator.label(0));
        assertEquals("1年6か月", LeaveGrantCalculator.label(1));
        assertEquals("6年6か月", LeaveGrantCalculator.label(6));
    }
}
