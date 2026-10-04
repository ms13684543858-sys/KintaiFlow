package com.example.kintaiflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** ONL-005 申請作成のリクエスト。申請者IDは含めない（常にトークンから決定）。 */
public record CreateRequestRequest(
        @NotBlank(message = "申請種別は必須入力です。")
        @Pattern(regexp = "LEAVE|CLOCK_CORRECTION", message = "申請種別が正しくありません。")
        String requestType,
        Long leaveTypeId,
        LocalDate startDate,
        LocalDate endDate,
        @Pattern(regexp = "FULL|AM|PM", message = "取得単位が正しくありません。")
        String unit,
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "修正後の出勤時刻の形式が正しくありません。")
        String correctedClockIn,
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "修正後の退勤時刻の形式が正しくありません。")
        String correctedClockOut,
        @Size(max = 200, message = "理由は200文字以内で入力してください。")
        String reason,
        Boolean asDraft) {
}
