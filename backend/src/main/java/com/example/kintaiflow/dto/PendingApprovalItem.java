package com.example.kintaiflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** ONL-009 承認待ち一覧の1件。日付は yyyy-MM-dd、日時は +09:00 付き ISO 8601 文字列。 */
public record PendingApprovalItem(
        Long stepId, Integer stepNo, Long requestId, String requestType,
        Long applicantId, String applicantName, String departmentName,
        LocalDate startDate, LocalDate endDate, BigDecimal days,
        String submittedAt, String step1ApprovedAt) {
}
