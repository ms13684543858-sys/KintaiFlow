package com.example.kintaiflow.dto;

/** ONL-014 ユーザー1件の応答（password_hash は含めない）。 */
public record UserResponse(
        Long id,
        String name,
        String email,
        String role,
        Long departmentId,
        String departmentName,
        Long managerId,
        String managerName,
        String hireDate,
        String status) {
}
