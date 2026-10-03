package com.example.kintaiflow.dto;

/** API-001 ログインのレスポンス。mustChangePassword=true の場合、画面はパスワード変更へ誘導する。 */
public record LoginResponse(String token, Long userId, String name, String role, boolean mustChangePassword) {
}
