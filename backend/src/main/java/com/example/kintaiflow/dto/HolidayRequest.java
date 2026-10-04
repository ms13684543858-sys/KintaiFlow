package com.example.kintaiflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** ONL-016 祝日登録のリクエスト。 */
public record HolidayRequest(
        @NotNull(message = "日付は必須入力です。")
        LocalDate holidayDate,

        @NotBlank(message = "名称は必須入力です。")
        @Size(max = 100, message = "名称は100文字以内で入力してください。")
        String name) {
}
