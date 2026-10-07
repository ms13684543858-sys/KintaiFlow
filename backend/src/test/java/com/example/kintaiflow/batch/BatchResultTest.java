package com.example.kintaiflow.batch;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** バッチ結果の状態判定（終了コード 0/1/2）とサマリ書式。 */
class BatchResultTest {

    private static BatchResult r(int succeeded, int skipped, int failed, String detail) {
        return new BatchResult("BAT-001", LocalDate.parse("2026-10-01"), 10, succeeded + skipped + failed,
                succeeded, skipped, failed, 12, detail);
    }

    @Test
    void successWhenNoFailure() {
        assertEquals(BatchStatus.SUCCESS, r(2, 1, 0, "").status());
        assertEquals(BatchStatus.SUCCESS, r(0, 0, 0, "").status());   // 0 件も正常
        assertEquals(BatchStatus.SUCCESS, r(0, 3, 0, "").status());   // 全件スキップも正常
        assertEquals(0, r(2, 0, 0, "").exitCode());
    }

    @Test
    void partialWhenSomeFailed() {
        assertEquals(BatchStatus.PARTIAL, r(1, 0, 1, "").status());
        assertEquals(BatchStatus.PARTIAL, r(0, 1, 1, "").status());
        assertEquals(1, r(1, 0, 1, "").exitCode());
    }

    @Test
    void failedWhenAllFailed() {
        assertEquals(BatchStatus.FAILED, r(0, 0, 2, "").status());
        assertEquals(2, r(0, 0, 2, "").exitCode());
    }

    @Test
    void summaryFormat() {
        assertEquals("BAT-001 END status=SUCCESS targetDate=2026-10-01 scanned=10 targets=2 succeeded=2 skipped=0 failed=0 elapsedMs=12",
                r(2, 0, 0, "").summary());
        assertTrue(r(2, 0, 0, "compliant=3 notifications=4").summary().endsWith(" compliant=3 notifications=4"));
    }
}
