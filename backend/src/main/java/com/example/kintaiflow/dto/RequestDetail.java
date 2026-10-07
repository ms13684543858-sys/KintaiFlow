package com.example.kintaiflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** ONL-007 申請詳細の申請内容部分。correctedClockIn/Out は HH:mm、日時は AppTime#toIso。 */
public record RequestDetail(
        Long requestId,
        String requestType,
        Long applicantId,
        String applicantName,
        String departmentName,
        Long leaveTypeId,
        String leaveTypeName,
        LocalDate startDate,
        LocalDate endDate,
        String unit,
        BigDecimal days,
        String correctedClockIn,
        String correctedClockOut,
        String breakKind,
        String breakStart,
        String breakEnd,
        String reason,
        String status,
        Integer currentStep,
        String submittedAt,
        String createdAt) {
}
