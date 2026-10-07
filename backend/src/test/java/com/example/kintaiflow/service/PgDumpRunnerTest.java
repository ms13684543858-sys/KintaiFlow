package com.example.kintaiflow.service;

import com.example.kintaiflow.config.BackupProperties;
import com.example.kintaiflow.exception.BackupException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** BAT-003 pg_dump の起動失敗の分類（E02）。実際の pg_dump は不要。 */
class PgDumpRunnerTest {

    @TempDir
    Path dir;

    @Test
    void missingExecutableIsE02() {
        BackupProperties props = new BackupProperties(dir.toString(), "kintai_", 7, "no-such-pg-dump-binary",
                "localhost", 5432, "kintaiflow", "kintaiflow", "secret-pw", 5, 1024);

        BackupException e = assertThrows(BackupException.class, () -> new PgDumpRunner(props).dump(dir.resolve("x.tmp")));

        assertEquals("E02", e.getCode());
        assertEquals(false, e.getMessage().contains("secret-pw"));
    }
}
