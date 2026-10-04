package com.example.kintaiflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** ONL-014 ユーザー登録のリクエスト。 */
public record UserCreateRequest(
        @NotBlank(message = "氏名は必須入力です。")
        @Size(max = 100, message = "氏名は100文字以内で入力してください。")
        String name,

        @NotBlank(message = "メールアドレスは必須入力です。")
        @Size(max = 255, message = "メールアドレスは255文字以内で入力してください。")
        @jakarta.validation.constraints.Email(message = "メールアドレスの形式が正しくありません。")
        String email,

        @NotBlank(message = "初期パスワードは必須入力です。")
        @Size(min = 8, max = 72, message = "初期パスワードは8文字以上72文字以内で入力してください。")
        @Pattern(regexp = "^[\\x21-\\x7E]*$", message = "初期パスワードは半角英数字・記号で入力してください。")
        String initialPassword,

        @NotBlank(message = "ロールは必須入力です。")
        @Pattern(regexp = "EMPLOYEE|MANAGER|ADMIN", message = "ロールの指定が正しくありません。")
        String role,

        Long departmentId,

        Long managerId,

        @NotNull(message = "入社日は必須入力です。")
        LocalDate hireDate) {
}
