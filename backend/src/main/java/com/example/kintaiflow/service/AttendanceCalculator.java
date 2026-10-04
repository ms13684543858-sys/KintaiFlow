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

    public WorkTime calculate(LocalDateTime clockIn, LocalDateTime clockOut) {
        long gross = Duration.between(
                clockIn.truncatedTo(ChronoUnit.MINUTES),
                clockOut.truncatedTo(ChronoUnit.MINUTES)).toMinutes();
        if (gross < 0) gross = 0;
        int breakMinutes = gross > THRESHOLD_8H ? BREAK_OVER_8H : (gross > THRESHOLD_6H ? BREAK_OVER_6H : 0);
        int work = (int) (gross - breakMinutes);
        int overtime = Math.max(0, work - STANDARD_MINUTES);
        return new WorkTime(work, overtime);
    }
}
