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
}
