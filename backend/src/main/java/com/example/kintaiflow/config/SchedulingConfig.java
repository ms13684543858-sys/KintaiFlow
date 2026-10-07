package com.example.kintaiflow.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @Scheduled を有効化する。kintaiflow.batch.scheduling-enabled=false でスケジューラ全体を止められる（テスト・2台目以降）。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "kintaiflow.batch.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
