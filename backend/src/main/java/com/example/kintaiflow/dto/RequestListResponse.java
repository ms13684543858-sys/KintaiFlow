package com.example.kintaiflow.dto;

import java.util.List;

/** ONL-006 申請一覧のレスポンス。 */
public record RequestListResponse(List<RequestSummary> requests) {
}
