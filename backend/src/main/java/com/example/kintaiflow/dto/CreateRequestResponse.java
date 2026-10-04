package com.example.kintaiflow.dto;

/** ONL-005 申請作成のレスポンス（status は DRAFT / PENDING）。 */
public record CreateRequestResponse(Long requestId, String status) {
}
