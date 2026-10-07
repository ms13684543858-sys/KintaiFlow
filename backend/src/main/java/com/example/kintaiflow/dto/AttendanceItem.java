package com.example.kintaiflow.dto;

import java.util.List;

/** 月次勤怠の1日分。breaks は休憩・日中離席の区間（開始順）。 */
public record AttendanceItem(String workDate, String clockIn, String clockOut,
                             Integer workMinutes, Integer overtimeMinutes, List<BreakItem> breaks) {}
