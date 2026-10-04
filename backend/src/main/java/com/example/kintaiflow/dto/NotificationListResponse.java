package com.example.kintaiflow.dto;

import java.util.List;

/** 通知一覧取得（ONL-019）の応答。 */
public record NotificationListResponse(List<NotificationResponse> notifications) {}
