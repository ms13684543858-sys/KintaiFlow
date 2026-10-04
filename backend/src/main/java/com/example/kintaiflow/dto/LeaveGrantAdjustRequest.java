package com.example.kintaiflow.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 付与日数調整リクエスト（ONL-017 PUT）。 */
public record LeaveGrantAdjustRequest(
        @NotNull(message = "付与日数は必須入力です。") BigDecimal grantedDays,
        @Size(max = 200, message = "調整理由は200文字以内で入力してください。") String note) {
}
