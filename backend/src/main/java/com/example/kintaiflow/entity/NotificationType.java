package com.example.kintaiflow.entity;

import java.util.List;

/** 通知種別（notifications.type）。 */
public final class NotificationType {
    public static final String REQUEST_SUBMITTED = "REQUEST_SUBMITTED";   // 承認依頼（承認者宛）
    public static final String REQUEST_APPROVED = "REQUEST_APPROVED";     // 承認（申請者宛）
    public static final String REQUEST_RETURNED = "REQUEST_RETURNED";     // 差戻し（申請者宛）
    public static final String REQUEST_REJECTED = "REQUEST_REJECTED";     // 却下（申請者宛）
    public static final String REQUEST_WITHDRAWN = "REQUEST_WITHDRAWN";   // 取下げ（承認者宛）
    public static final String LEAVE_GRANTED = "LEAVE_GRANTED";           // 休暇付与（本人宛）
    public static final String LEAVE_ALERT = "LEAVE_ALERT";               // 年5日取得義務アラート（BAT-002）

    public static final List<String> ALL = List.of(REQUEST_SUBMITTED, REQUEST_APPROVED, REQUEST_RETURNED,
            REQUEST_REJECTED, REQUEST_WITHDRAWN, LEAVE_GRANTED, LEAVE_ALERT);
    public static final List<String> REQUEST_BOUND = List.of(REQUEST_SUBMITTED, REQUEST_APPROVED,
            REQUEST_RETURNED, REQUEST_REJECTED, REQUEST_WITHDRAWN);

    private NotificationType() {}
}
