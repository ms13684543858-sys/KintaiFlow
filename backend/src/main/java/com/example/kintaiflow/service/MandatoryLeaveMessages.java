package com.example.kintaiflow.service;

import com.example.kintaiflow.dto.AlertBaseRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** BAT-002 通知本文と、同月重複判定用の先頭キーの書式を1か所に集約する。 */
public final class MandatoryLeaveMessages {

    private static final DateTimeFormatter MSG_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private MandatoryLeaveMessages() {}

    /** 本文の先頭に付ける重複判定キー。"]" で閉じるので ID:1 と ID:12 を区別できる。 */
    public static String key(Long userId, LocalDate grantedOn) {
        return "[対象者ID:" + userId + " 付与日:" + grantedOn + "]";
    }

    /** 期限 = 付与日から1年以内の最終日。 */
    public static LocalDate deadline(LocalDate grantedOn) {
        return grantedOn.plusYears(1).minusDays(1);
    }

    public static String forSelf(AlertBaseRow row, BigDecimal taken, BigDecimal threshold) {
        BigDecimal remain = threshold.subtract(taken);
        return key(row.userId(), row.grantedOn())
                + " 年5日取得義務：年次有給休暇の取得日数は" + taken + "日です。付与日から1年以内（"
                + deadline(row.grantedOn()).format(MSG_DATE) + "まで）に" + threshold.stripTrailingZeros().toPlainString()
                + "日以上取得する必要があります。あと" + remain + "日以上の取得を計画してください。";
    }

    public static String forAdmin(AlertBaseRow row, BigDecimal taken) {
        return key(row.userId(), row.grantedOn())
                + " 年5日取得義務：" + row.userName() + "さんの年次有給休暇（付与日 "
                + row.grantedOn().format(MSG_DATE) + "）の取得日数は" + taken + "日です（期限 "
                + deadline(row.grantedOn()).format(MSG_DATE) + "）。取得を促してください。";
    }
}
