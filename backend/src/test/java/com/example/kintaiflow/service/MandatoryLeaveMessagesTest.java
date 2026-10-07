package com.example.kintaiflow.service;

import com.example.kintaiflow.dto.AlertBaseRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BAT-002 通知本文と重複判定キー（KF-DD-BAT-002 4.5 の例に対応）。 */
class MandatoryLeaveMessagesTest {

    private static final AlertBaseRow ROW =
            new AlertBaseRow(12L, "山田 太郎", LocalDate.parse("2025-10-01"), new BigDecimal("10.0"));

    @Test
    void keyDistinguishesUserIdsWithSamePrefix() {
        String k1 = MandatoryLeaveMessages.key(1L, LocalDate.parse("2025-10-01"));
        String k12 = MandatoryLeaveMessages.key(12L, LocalDate.parse("2025-10-01"));
        assertEquals("[対象者ID:12 付与日:2025-10-01]", k12);
        assertFalse(k12.startsWith(k1), "ID:1 のキーで ID:12 の通知を誤判定しない（] で閉じる）");
    }

    @Test
    void deadlineIsLastDayWithinOneYear() {
        assertEquals(LocalDate.parse("2026-09-30"), MandatoryLeaveMessages.deadline(LocalDate.parse("2025-10-01")));
    }

    @Test
    void selfMessage() {
        String m = MandatoryLeaveMessages.forSelf(ROW, new BigDecimal("2.5"), new BigDecimal("5.0"));
        assertEquals("[対象者ID:12 付与日:2025-10-01] 年5日取得義務：年次有給休暇の取得日数は2.5日です。"
                + "付与日から1年以内（2026/09/30まで）に5日以上取得する必要があります。"
                + "あと2.5日以上の取得を計画してください。", m);
    }

    @Test
    void adminMessage() {
        String m = MandatoryLeaveMessages.forAdmin(ROW, new BigDecimal("2.5"));
        assertEquals("[対象者ID:12 付与日:2025-10-01] 年5日取得義務：山田 太郎さんの年次有給休暇（付与日 2025/10/01）"
                + "の取得日数は2.5日です（期限 2026/09/30）。取得を促してください。", m);
        assertTrue(m.startsWith(MandatoryLeaveMessages.key(12L, ROW.grantedOn())), "本人宛て・管理者宛てとも同じキーで始まる");
    }
}
