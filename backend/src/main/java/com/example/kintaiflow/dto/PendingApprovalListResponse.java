package com.example.kintaiflow.dto;

import java.util.List;

/** ONL-009 承認待ち一覧のレスポンス（0 件は空配列）。 */
public record PendingApprovalListResponse(List<PendingApprovalItem> items) {
}
