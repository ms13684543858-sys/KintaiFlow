package com.example.kintaiflow.dto;

import java.util.List;

/** 上長の部下一覧（ONL-020）の応答。 */
public record SubordinateSummaryResponse(String month, List<MonthlyReportRow> rows) {}
