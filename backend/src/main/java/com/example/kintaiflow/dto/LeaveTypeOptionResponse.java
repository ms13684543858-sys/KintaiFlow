package com.example.kintaiflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** ONL-021 申請画面プルダウン用の休暇種別（5項目）。 */
public record LeaveTypeOptionResponse(
        Long id,
        String name,
        @JsonProperty("isPaid") Boolean isPaid,
        String maxDaysRule,
        Boolean allowHalfDay) {
}
