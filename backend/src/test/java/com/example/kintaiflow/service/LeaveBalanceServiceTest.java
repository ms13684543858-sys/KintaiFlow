package com.example.kintaiflow.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** ONL-013 / ONL-017 の純粋関数の単体テスト。 */
class LeaveBalanceServiceTest {

    @ParameterizedTest
    @CsvSource({
            "2026-04-01, 2026-04-15, 2026-10-01",  // 入社直後 → 初回(6か月後)
            "2026-04-01, 2026-10-01, 2027-10-01",  // 付与日当日は次回に含めない
            "2026-04-01, 2026-09-30, 2026-10-01",  // 付与日前日
            "2026-04-01, 2027-10-01, 2028-10-01",
            "2020-04-01, 2026-10-05, 2027-10-01",
            "2026-10-05, 2026-10-05, 2027-04-05",  // 入社当日
            "2023-08-29, 2025-01-01, 2025-02-28",  // うるう日起点: 2024-02-29 → 2025-02-28
            "2023-08-29, 2024-02-29, 2025-02-28"
    })
    void calcNextGrantDate(String hire, String asOf, String expected) {
        assertEquals(LocalDate.parse(expected),
                LeaveBalanceService.calcNextGrantDate(LocalDate.parse(hire), LocalDate.parse(asOf)));
    }

    @ParameterizedTest
    @CsvSource({"0,0.0", "5,0.0", "6,10.0", "17,10.0", "18,11.0", "29,11.0", "30,12.0", "41,12.0",
            "42,14.0", "53,14.0", "54,16.0", "65,16.0", "66,18.0", "77,18.0", "78,20.0", "200,20.0"})
    void statutoryDays(long months, String expected) {
        assertEquals(new BigDecimal(expected), LeaveBalanceService.statutoryDays(months));
    }

    @ParameterizedTest
    @CsvSource({
            "2026-04-01, 2026-09-30, 0",
            "2026-04-01, 2026-10-01, 1",
            "2026-04-01, 2027-09-30, 1",
            "2026-04-01, 2027-10-01, 2",
            "2020-04-01, 2026-10-05, 7"
    })
    void regularGrantCount(String hire, String asOf, int expected) {
        assertEquals(expected,
                LeaveBalanceService.regularGrantCount(LocalDate.parse(hire), LocalDate.parse(asOf)));
    }

    @Test
    void nextStatutoryDaysFromGrantCount() {
        LocalDate hire = LocalDate.parse("2024-04-01");
        LocalDate today = LocalDate.parse("2026-10-05");  // 付与済み3回(2024-10, 2025-10, 2026-10) → 次回付与時点で勤続42か月
        int count = LeaveBalanceService.regularGrantCount(hire, today);
        assertEquals(3, count);
        assertEquals(new BigDecimal("14.0"), LeaveBalanceService.statutoryDays(6L + 12L * count));
    }
}
