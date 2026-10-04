package com.example.kintaiflow.dto;

import com.example.kintaiflow.config.AppTime;
import com.example.kintaiflow.entity.AttendanceRecord;

public record ClockOutResponse(String clockOut, Integer workMinutes, Integer overtimeMinutes) {
    public static ClockOutResponse from(AttendanceRecord rec) {
        return new ClockOutResponse(AppTime.toIso(rec.getClockOut()), rec.getWorkMinutes(), rec.getOvertimeMinutes());
    }
}
