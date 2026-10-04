package com.example.kintaiflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** ONL-014 パスワード設定（管理者による初期化・再設定）のリクエスト。 */
public record PasswordResetRequest(
        @NotBlank(message = "新しいパスワードは必須入力です。")
        @Size(min = 8, max = 72, message = "新しいパスワードは8文字以上72文字以内で入力してください。")
        @Pattern(regexp = "^[\\x21-\\x7E]*$", message = "新しいパスワードは半角英数字・記号で入力してください。")
        String newPassword) {
}
