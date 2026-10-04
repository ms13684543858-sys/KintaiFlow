package com.example.kintaiflow.dto;

import java.util.List;

/** ONL-021 有効休暇種別一覧の応答。 */
public record LeaveTypeOptionListResponse(List<LeaveTypeOptionResponse> leaveTypes) {
}
