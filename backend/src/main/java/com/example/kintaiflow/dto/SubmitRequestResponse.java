package com.example.kintaiflow.dto;

/** ONL-022 申請提出のレスポンス（status は常に PENDING）。 */
public record SubmitRequestResponse(Long requestId, String status) {
}
