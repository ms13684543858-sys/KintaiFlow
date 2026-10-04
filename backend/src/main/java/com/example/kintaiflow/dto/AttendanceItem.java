package com.example.kintaiflow.dto;

public record AttendanceItem(String workDate, String clockIn, String clockOut,
                             Integer workMinutes, Integer overtimeMinutes) {}
