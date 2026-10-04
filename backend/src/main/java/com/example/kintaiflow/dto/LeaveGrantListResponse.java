package com.example.kintaiflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 付与履歴一覧の応答（ONL-017 GET）。 */
public record LeaveGrantListResponse(Long userId, String userName, LocalDate hireDate, LocalDate nextGrantDate,
                                     BigDecimal nextStatutoryDays, List<LeaveGrantResponse> grants) {
}
