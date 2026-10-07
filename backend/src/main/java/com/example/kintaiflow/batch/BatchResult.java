package com.example.kintaiflow.batch;

import java.time.LocalDate;

/** バッチ実行結果（BAT-001〜003 共通）。detail には BAT 固有の件数を付ける。 */
public record BatchResult(String batchId, LocalDate targetDate, int scanned, int targets,
                          int succeeded, int skipped, int failed, long elapsedMs, String detail) {

    public BatchStatus status() {
        if (failed == 0) return BatchStatus.SUCCESS;   // 0 件・全件スキップも正常
        return (succeeded + skipped) > 0 ? BatchStatus.PARTIAL : BatchStatus.FAILED;
    }

    public int exitCode() { return status().exitCode(); }

    public String summary() {
        return String.format("%s END status=%s targetDate=%s scanned=%d targets=%d succeeded=%d skipped=%d failed=%d elapsedMs=%d%s",
                batchId, status(), targetDate, scanned, targets, succeeded, skipped, failed, elapsedMs,
                (detail == null || detail.isEmpty()) ? "" : " " + detail);
    }
}
