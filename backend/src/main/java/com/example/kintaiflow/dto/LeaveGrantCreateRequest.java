package com.example.kintaiflow.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 休暇付与リクエスト（ONL-017 POST）。範囲検証（E-003/E-014）は Service で行う。 */
public record LeaveGrantCreateRequest(
        @NotNull(message = "従業員は必須入力です。") Long userId,
        @NotNull(message = "休暇種別は必須入力です。") Long leaveTypeId,
        @NotNull(message = "付与日は必須入力です。") LocalDate grantedOn,
        @NotNull(message = "付与日数は必須入力です。") BigDecimal grantedDays,
        @Size(max = 200, message = "調整理由は200文字以内で入力してください。") String note) {
}
