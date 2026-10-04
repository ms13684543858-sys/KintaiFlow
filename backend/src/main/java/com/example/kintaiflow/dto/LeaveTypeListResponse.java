package com.example.kintaiflow.dto;

import java.util.List;

/** ONL-015 休暇種別一覧の応答。 */
public record LeaveTypeListResponse(List<LeaveTypeResponse> leaveTypes) {
}
