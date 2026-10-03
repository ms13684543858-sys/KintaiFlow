package com.example.kintaiflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** API-001 ログインのリクエスト。メッセージは E-002 の書式に合わせる。 */
public record LoginRequest(
        @NotBlank(message = "メールアドレスは必須入力です。")
        @Size(max = 255, message = "メールアドレスは255文字以内で入力してください。")
        String email,

        @NotBlank(message = "パスワードは必須入力です。")
        String password) {
}
