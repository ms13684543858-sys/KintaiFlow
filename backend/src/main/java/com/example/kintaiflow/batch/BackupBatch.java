package com.example.kintaiflow.batch;

import com.example.kintaiflow.service.BackupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

/** BAT-003 データバックアップ（毎日 03:30 JST。BAT-001/002 の更新を含めて保存する）。 */
@Component
public class BackupBatch {

    private static final Logger log = LoggerFactory.getLogger(BackupBatch.class);

    private final BackupService backupService;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public BackupBatch(BackupService backupService, Clock clock) {
        this.backupService = backupService;
        this.clock = clock;
    }

    @Scheduled(cron = "${kintaiflow.batch.backup.cron:0 30 3 * * *}", zone = "${kintaiflow.batch.zone:Asia/Tokyo}")
    public void run() {
        if (!running.compareAndSet(false, true)) {
            log.warn("BAT-003 skipped: previous run is still active");
            return;
        }
        LocalDate today = LocalDate.now(clock);
        try {
            BatchResult r = backupService.execute(today);   // START / 成功時の END ログは execute 内で出す
            if (r.status() != BatchStatus.SUCCESS) log.error(r.summary());
        } catch (Exception e) {
            log.error("BAT-003 END status=FAILED targetDate={} reason=fatal", today, e);
        } finally {
            running.set(false);
        }
    }
}
