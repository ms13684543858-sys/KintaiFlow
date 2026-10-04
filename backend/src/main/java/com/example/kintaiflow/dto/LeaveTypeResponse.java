package com.example.kintaiflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** ONL-015 休暇種別1件の応答。 */
public record LeaveTypeResponse(
        Long id,
        String name,
        String legalBasis,
        @JsonProperty("isPaid") Boolean isPaid,
        String maxDaysRule,
        Boolean allowHalfDay,
        @JsonProperty("isActive") Boolean isActive) {
}
