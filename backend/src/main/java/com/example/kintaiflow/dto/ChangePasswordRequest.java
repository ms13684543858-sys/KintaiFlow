package com.example.kintaiflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** API-023 パスワード変更のリクエスト。 */
public record ChangePasswordRequest(
        @NotBlank(message = "現在のパスワードは必須入力です。")
        String currentPassword,

        @NotBlank(message = "新しいパスワードは必須入力です。")
        @Size(max = 72, message = "新しいパスワードは72文字以内で入力してください。")
        String newPassword) {
}
