package com.example.kintaiflow.service;

import com.example.kintaiflow.dto.AlertBaseRow;
import com.example.kintaiflow.entity.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/** BAT-002 対象者1人分の通知（本人＋管理者）。1人分は同一 Tx（REQUIRES_NEW）でコミット／ロールバックする。 */
@Service
public class MandatoryLeaveAlertUnitService {

    /** created = 新規登録した通知数、existing = 同月に送信済みで省いた宛先数。 */
    public record AlertOutcome(int created, int existing) {}

    private static final Logger log = LoggerFactory.getLogger(MandatoryLeaveAlertUnitService.class);

    private final NotificationService notificationService;

    public MandatoryLeaveAlertUnitService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * @param adminIds 通知先の ACTIVE な管理者（対象者本人が含まれていても、本人宛ては1件にまとめる）
     * @param month    同月重複の判定月（実行時点の暦月。run-once の target-date は使わない）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AlertOutcome alertOne(AlertBaseRow row, BigDecimal taken, BigDecimal threshold,
                                 List<Long> adminIds, YearMonth month) {
        String key = MandatoryLeaveMessages.key(row.userId(), row.grantedOn());
        int created = 0, existing = 0;

        if (send(row.userId(), key, MandatoryLeaveMessages.forSelf(row, taken, threshold), month)) created++;
        else existing++;
        String adminMessage = MandatoryLeaveMessages.forAdmin(row, taken);
        for (Long adminId : adminIds) {
            if (adminId.equals(row.userId())) continue;   // 対象者本人が ADMIN なら本人向けの1件だけ
            if (send(adminId, key, adminMessage, month)) created++;
            else existing++;
        }
        log.info("BAT-002 alerted userId={} grantedOn={} taken={} created={} existing={}",
                row.userId(), row.grantedOn(), taken, created, existing);
        return new AlertOutcome(created, existing);
    }

    private boolean send(Long toUserId, String key, String message, YearMonth month) {
        if (notificationService.existsMonthly(toUserId, NotificationType.LEAVE_ALERT, key, month)) return false;
        notificationService.notify(toUserId, NotificationType.LEAVE_ALERT, message, null);
        return true;
    }
}
