package com.example.kintaiflow.dto;

import com.example.kintaiflow.config.AppTime;
import com.example.kintaiflow.entity.Notification;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 通知 1 件分の応答（ONL-019。一覧・既読化の両方で使用）。 */
public record NotificationResponse(Long id, String type, String message, Long requestId, String createdAt,
                                   @JsonProperty("isRead") boolean isRead) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getMessage(),
                n.getRequestId(),
                n.getCreatedAt() == null ? null : AppTime.toIso(n.getCreatedAt()),
                n.isRead());
    }
}
