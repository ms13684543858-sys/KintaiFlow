package com.example.kintaiflow.dto;

import java.util.List;

/** ONL-014 ユーザー一覧の応答。 */
public record UserListResponse(List<UserResponse> users, Integer total) {
}
