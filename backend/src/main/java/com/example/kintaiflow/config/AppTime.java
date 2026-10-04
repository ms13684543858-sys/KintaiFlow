package com.example.kintaiflow.config;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 時刻まわりの共通部品。業務の基準タイムゾーンは日本時間（Asia/Tokyo）。 */
public final class AppTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    private AppTime() {}

    /** DB の壁時計時刻（JST）を「2026-10-03T09:00:00+09:00」形式の文字列にする。null は null。 */
    public static String toIso(LocalDateTime t) {
        if (t == null) return null;
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(t.atZone(ZONE));
    }
}
