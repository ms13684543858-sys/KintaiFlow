package com.example.kintaiflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 休憩開始／離席開始のリクエスト。 */
public record BreakStartRequest(
        @NotBlank(message = "種別は必須入力です。")
        @Pattern(regexp = "BREAK|AWAY", message = "種別が正しくありません。")
        String kind) {}
