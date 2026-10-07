package com.example.kintaiflow.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** BAT-003 の設定値バインドを有効にする（SchedulingConfig とは別。スケジューラを止めても run-once で使うため）。 */
@Configuration
@EnableConfigurationProperties(BackupProperties.class)
public class BackupConfig {
}
