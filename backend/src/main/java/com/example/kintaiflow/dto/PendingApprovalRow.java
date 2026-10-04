package com.example.kintaiflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** ONL-009 承認待ち検索の1行（ApprovalStepRepository#findPending のコンストラクタ式。引数の並び・型は JPQL と一致させる）。 */
public record PendingApprovalRow(
        Long stepId, Integer stepNo, Long requestId, String requestType,
        Long applicantId, String applicantName, String departmentName,
        LocalDate startDate, LocalDate endDate, BigDecimal days,
        LocalDateTime submittedAt, LocalDateTime step1ApprovedAt) {
}
