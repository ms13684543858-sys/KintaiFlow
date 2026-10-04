package com.example.kintaiflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** ONL-006 申請一覧の1件。日時は AppTime#toIso の文字列。 */
public record RequestSummary(
        Long requestId,
        String requestType,
        String leaveTypeName,
        LocalDate startDate,
        LocalDate endDate,
        String unit,
        BigDecimal days,
        String status,
        String submittedAt,
        String createdAt) {
}
