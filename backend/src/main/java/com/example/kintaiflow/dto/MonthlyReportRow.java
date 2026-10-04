package com.example.kintaiflow.dto;

import java.math.BigDecimal;

/** 社員 1 行分の月次集計（ONL-018 / ONL-020 共用）。時間は分、leaveDays は小数第1位。 */
public record MonthlyReportRow(Long userId, String userName, String departmentName,
                               long workDays, long workMinutes, long overtimeMinutes, BigDecimal leaveDays) {}
