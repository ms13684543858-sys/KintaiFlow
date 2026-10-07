package com.example.kintaiflow.batch;

import com.example.kintaiflow.service.MandatoryLeaveAlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

/** BAT-002 年5日取得義務アラート（毎月1日 03:00 JST。同日 02:00 の BAT-001 の後）。 */
@Component
public class MandatoryLeaveAlertBatch {

    private static final Logger log = LoggerFactory.getLogger(MandatoryLeaveAlertBatch.class);

    private final MandatoryLeaveAlertService service;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public MandatoryLeaveAlertBatch(MandatoryLeaveAlertService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(cron = "${kintaiflow.batch.mandatory-alert.cron:0 0 3 1 * *}", zone = "${kintaiflow.batch.zone:Asia/Tokyo}")
    public void run() {
        if (!running.compareAndSet(false, true)) {
            log.warn("BAT-002 skipped: previous run is still active");
            return;
        }
        LocalDate today = LocalDate.now(clock);
        try {
            log.info("BAT-002 START targetDate={}", today);
            BatchResult r = service.execute(today);
            switch (r.status()) {
                case SUCCESS -> log.info(r.summary());
                case PARTIAL -> log.warn(r.summary());
                case FAILED -> log.error(r.summary());
            }
        } catch (Exception e) {
            log.error("BAT-002 END status=FAILED targetDate={} reason=fatal", today, e);
        } finally {
            running.set(false);
        }
    }
}
