package com.example.kintaiflow.dto;

import java.util.List;

public record MonthlyAttendanceResponse(List<AttendanceItem> records, int totalWorkMinutes, int totalOvertimeMinutes) {}
