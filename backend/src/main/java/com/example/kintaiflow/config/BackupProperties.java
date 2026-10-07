package com.example.kintaiflow.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** BAT-003 kintaiflow.batch.backup.* の設定値。パスワードは toString に出さない。 */
@ConfigurationProperties("kintaiflow.batch.backup")
@Validated
public record BackupProperties(
        @DefaultValue("backup") String dir,
        @DefaultValue("kintai_") String filePrefix,
        @DefaultValue("7") @Min(1) int retention,
        @DefaultValue("pg_dump") String pgDumpPath,
        @DefaultValue("localhost") String dbHost,
        @DefaultValue("5432") int dbPort,
        @DefaultValue("kintaiflow") String dbName,
        @DefaultValue("kintaiflow") String dbUser,
        @DefaultValue("") String dbPassword,
        @DefaultValue("600") @Positive long timeoutSeconds,
        @DefaultValue("1024") long minSizeBytes) {

    @Override
    public String toString() {
        return "BackupProperties[dir=" + dir + ", retention=" + retention + ", pgDumpPath=" + pgDumpPath
                + ", dbHost=" + dbHost + ", dbPort=" + dbPort + ", dbName=" + dbName + ", dbUser=" + dbUser
                + ", dbPassword=***]";   // パスワードは常にマスク
    }
}
