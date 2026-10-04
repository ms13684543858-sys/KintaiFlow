package com.example.kintaiflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** ONL-015 休暇種別の登録・変更リクエスト（同一 DTO。変更は全項目置換）。 */
public record LeaveTypeRequest(
        @NotBlank(message = "名称は必須入力です。")
        @Size(max = 100, message = "名称は100文字以内で入力してください。")
        String name,

        @Size(max = 100, message = "法的根拠は100文字以内で入力してください。")
        String legalBasis,

        @JsonProperty("isPaid")
        @NotNull(message = "有給・無給は必須入力です。")
        Boolean isPaid,

        @NotBlank(message = "日数ルールは必須入力です。")
        @Pattern(regexp = "LIMITED|UNLIMITED", message = "日数ルールの指定が正しくありません。")
        String maxDaysRule,

        @NotNull(message = "半日取得可は必須入力です。")
        Boolean allowHalfDay,

        @JsonProperty("isActive")
        @NotNull(message = "有効は必須入力です。")
        Boolean isActive) {
}
