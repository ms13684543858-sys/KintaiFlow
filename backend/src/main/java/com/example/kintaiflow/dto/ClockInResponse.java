package com.example.kintaiflow.dto;

import com.example.kintaiflow.config.AppTime;
import com.example.kintaiflow.entity.AttendanceRecord;

public record ClockInResponse(String workDate, String clockIn) {
    public static ClockInResponse from(AttendanceRecord rec) {
        return new ClockInResponse(rec.getWorkDate().toString(), AppTime.toIso(rec.getClockIn()));
    }
}
