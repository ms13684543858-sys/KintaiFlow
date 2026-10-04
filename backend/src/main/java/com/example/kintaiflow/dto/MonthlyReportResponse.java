package com.example.kintaiflow.dto;

import java.util.List;

/** 月次集計取得（ONL-018）の JSON 応答。 */
public record MonthlyReportResponse(String month, Long departmentId, List<MonthlyReportRow> rows) {}
