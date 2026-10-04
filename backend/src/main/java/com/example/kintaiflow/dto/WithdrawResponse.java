package com.example.kintaiflow.dto;

/** ONL-008 申請取下げのレスポンス（status は常に WITHDRAWN）。 */
public record WithdrawResponse(String status) {
}
