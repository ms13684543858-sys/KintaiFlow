package com.example.kintaiflow.controller;

import com.example.kintaiflow.dto.NotificationListResponse;
import com.example.kintaiflow.dto.NotificationResponse;
import com.example.kintaiflow.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** ONL-019 通知一覧取得（GET）/ 既読化（POST /{id}/read）。対象は常にログイン本人の通知。 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationListResponse list(Authentication auth) {
        return notificationService.getNotifications(Long.valueOf(auth.getName()));
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markAsRead(@PathVariable("id") Long id, Authentication auth) {
        return notificationService.markAsRead(Long.valueOf(auth.getName()), id);
    }
}
