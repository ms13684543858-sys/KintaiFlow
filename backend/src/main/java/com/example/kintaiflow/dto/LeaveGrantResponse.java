package com.example.kintaiflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 付与1件の応答（ONL-017）。 */
public record LeaveGrantResponse(Long id, Long leaveTypeId, String leaveTypeName, LocalDate grantedOn,
                                 BigDecimal grantedDays, BigDecimal usedDays, BigDecimal remainingDays,
                                 LocalDate expiresOn, boolean expired) {
}
