package com.example.kintaiflow.service;

import com.example.kintaiflow.dto.NotificationListResponse;
import com.example.kintaiflow.dto.NotificationResponse;
import com.example.kintaiflow.entity.Notification;
import com.example.kintaiflow.entity.NotificationType;
import com.example.kintaiflow.exception.BusinessException;
import com.example.kintaiflow.repository.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/** 画面内通知（ONL-005 で notify を定義。一覧・既読は ONL-019 が追加する）。 */
@Service
public class NotificationService {

    static final int MESSAGE_MAX = 500;

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** 宛先ユーザーへ通知を1件登録する。呼出し元のトランザクションに参加する（REQUIRES_NEW にしない）。 */
    @Transactional(propagation = Propagation.REQUIRED)
    public void notify(Long userId, String type, String message, Long requestId) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (type == null || !NotificationType.ALL.contains(type))
            throw new IllegalArgumentException("unknown notification type: " + type);
        if (NotificationType.REQUEST_BOUND.contains(type) && requestId == null)
            throw new IllegalArgumentException("requestId is required for " + type);
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message is required");
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setRequestId(requestId);
        n.setMessage(message.length() > MESSAGE_MAX ? message.substring(0, MESSAGE_MAX) : message);
        n.setRead(false);
        notificationRepository.save(n);
    }

    /** ONL-019 本人宛の通知を新しい順に最大50件返す（既読化はしない）。 */
    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(Long userId) {
        List<Notification> rows = notificationRepository.findTop50ByUserIdOrderByCreatedAtDescIdDesc(userId);
        List<NotificationResponse> list = rows.stream().map(NotificationResponse::from).toList();
        return new NotificationListResponse(list);
    }

    /** ONL-019 本人の通知を既読にする（冪等）。不存在・他人の通知は区別せず E-010。 */
    @Transactional
    public NotificationResponse markAsRead(Long userId, Long notificationId) {
        Notification n = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException("E-010", "この操作を行う権限がありません。", HttpStatus.FORBIDDEN));
        if (!n.isRead()) {
            notificationRepository.markAsRead(n.getId(), userId);
            // markAsRead は clearAutomatically=true のため n は管理対象外。応答用に値だけ更新する
            n.setRead(true);
        }
        return NotificationResponse.from(n);
    }

    /** ONL-019 指定ユーザー・種別の通知が指定年月（JST）に既にあるか。 */
    @Transactional(readOnly = true)
    public boolean existsMonthly(Long userId, String type, YearMonth yearMonth) {
        LocalDateTime from = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime to = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
        return notificationRepository
                .existsByUserIdAndTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(userId, type, from, to);
    }

    /** ONL-019 existsMonthly の本文前方一致キー付き版（対象者・付与日を区別する用途）。 */
    @Transactional(readOnly = true)
    public boolean existsMonthly(Long userId, String type, String keyPrefix, YearMonth yearMonth) {
        LocalDateTime from = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime to = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
        return notificationRepository
                .existsByUserIdAndTypeAndMessageStartingWithAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        userId, type, keyPrefix, from, to);
    }
}
