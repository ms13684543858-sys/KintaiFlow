package com.example.kintaiflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 付与履歴の検索結果行（ONL-017。LeaveBalanceRepository#findGrantRows のコンストラクタ式で使用）。 */
public record LeaveGrantRow(Long id, Long leaveTypeId, String leaveTypeName, LocalDate grantedOn,
                            BigDecimal grantedDays, BigDecimal usedDays, LocalDate expiresOn) {
}
