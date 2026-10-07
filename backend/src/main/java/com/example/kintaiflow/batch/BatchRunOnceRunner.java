package com.example.kintaiflow.batch;

import com.example.kintaiflow.service.LeaveGrantService;
import com.example.kintaiflow.service.MandatoryLeaveAlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 指定バッチを手動で1回だけ実行し、終了コード（0:正常 1:一部失敗 2:異常）で JVM を終了する（管理 API の代わり）。
 * 例: --kintaiflow.batch.run-once=BAT-001 --kintaiflow.batch.target-date=2026-10-01
 *     --kintaiflow.batch.scheduling-enabled=false --server.port=0
 * （SecurityConfig が Web 環境を要求するため web-application-type=none は使えない。ポートは衝突しないよう 0 にする）
 * kintaiflow.batch.run-once が未指定の通常起動では Bean 自体が作られない。
 */
@Component
@ConditionalOnProperty(name = "kintaiflow.batch.run-once")
public class BatchRunOnceRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchRunOnceRunner.class);

    private final Environment env;
    private final Clock clock;
    private final ApplicationContext applicationContext;
    private final LeaveGrantService leaveGrantService;
    private final MandatoryLeaveAlertService mandatoryLeaveAlertService;

    public BatchRunOnceRunner(Environment env, Clock clock, ApplicationContext applicationContext,
                              LeaveGrantService leaveGrantService, MandatoryLeaveAlertService mandatoryLeaveAlertService) {
        this.env = env;
        this.clock = clock;
        this.applicationContext = applicationContext;
        this.leaveGrantService = leaveGrantService;
        this.mandatoryLeaveAlertService = mandatoryLeaveAlertService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String id = env.getProperty("kintaiflow.batch.run-once");
        int code;
        try {
            String d = env.getProperty("kintaiflow.batch.target-date");
            LocalDate date = d == null || d.isBlank() ? LocalDate.now(clock) : LocalDate.parse(d);   // yyyy-MM-dd
            BatchResult r = switch (id) {
                case "BAT-001" -> leaveGrantService.execute(date);
                case "BAT-002" -> mandatoryLeaveAlertService.execute(date);
                default -> throw new IllegalArgumentException("unknown batch id: " + id);
            };
            log.info(r.summary());
            code = r.exitCode();
        } catch (Exception e) {
            log.error("{} END status=FAILED reason=fatal", id, e);
            code = 2;
        }
        int exit = code;
        System.exit(SpringApplication.exit(applicationContext, () -> exit));   // コンテキストを閉じてから終了
    }
}
