package com.example.kintaiflow.batch;

import com.example.kintaiflow.service.LeaveGrantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

/** BAT-001 年次有給休暇 自動付与（毎日 02:00 JST）。スケジュールの起点のみで業務判断は持たない。 */
@Component
public class LeaveGrantBatch {

    private static final Logger log = LoggerFactory.getLogger(LeaveGrantBatch.class);

    private final LeaveGrantService leaveGrantService;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public LeaveGrantBatch(LeaveGrantService leaveGrantService, Clock clock) {
        this.leaveGrantService = leaveGrantService;
        this.clock = clock;
    }

    @Scheduled(cron = "${kintaiflow.batch.leave-grant.cron:0 0 2 * * *}", zone = "${kintaiflow.batch.zone:Asia/Tokyo}")
    public void run() {
        if (!running.compareAndSet(false, true)) {
            log.warn("BAT-001 skipped: previous run is still active");
            return;
        }
        LocalDate today = LocalDate.now(clock);
        try {
            log.info("BAT-001 START targetDate={} catchUpDays={}", today, leaveGrantService.catchUpDays());
            BatchResult r = leaveGrantService.execute(today);
            switch (r.status()) {
                case SUCCESS -> log.info(r.summary());
                case PARTIAL -> log.warn(r.summary());
                case FAILED -> log.error(r.summary());
            }
        } catch (Exception e) {
            log.error("BAT-001 END status=FAILED targetDate={} reason=fatal", today, e);
        } finally {
            running.set(false);
        }
    }
}
