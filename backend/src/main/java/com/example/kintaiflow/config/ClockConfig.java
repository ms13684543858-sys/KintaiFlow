package com.example.kintaiflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 業務時刻は必ずこの Clock から取得する（テストで固定できる。サーバーが UTC でも JST で動く）。 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(AppTime.ZONE);
    }
}
