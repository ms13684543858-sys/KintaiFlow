package com.example.kintaiflow.service;

import com.example.kintaiflow.batch.BatchResult;
import com.example.kintaiflow.batch.BatchStatus;
import com.example.kintaiflow.config.BackupProperties;
import com.example.kintaiflow.exception.BackupException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/** BAT-003 の判定ロジック。pg_dump は PgDumpRunner のモックに差し替え、ファイル操作は一時ディレクトリで確認する。 */
class BackupServiceTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-03");
    private static final String VALID_BODY = "-- dump\n" + "x".repeat(2000) + "\n-- PostgreSQL database dump complete\n";

    @TempDir
    Path dir;

    private final PgDumpRunner runner = mock(PgDumpRunner.class);

    private BackupService service(int retention) {
        BackupProperties props = new BackupProperties(dir.toString(), "kintai_", retention, "pg_dump",
                "localhost", 5432, "kintaiflow", "kintaiflow", "secret-pw", 600, 1024);
        return new BackupService(props, runner);
    }

    private void dumpWrites(String body) throws IOException {
        doAnswer(inv -> {
            Files.writeString(inv.<Path>getArgument(0), body, StandardCharsets.UTF_8);
            return null;
        }).when(runner).dump(any());
    }

    private List<String> names() throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private void touch(String name) throws IOException {
        Files.writeString(dir.resolve(name), "old");
    }

    @Test
    void success_createsFileAndLeavesNoTmp() throws IOException {
        dumpWrites(VALID_BODY);

        BatchResult r = service(7).execute(TODAY);

        assertEquals(BatchStatus.SUCCESS, r.status());
        assertEquals(1, r.succeeded());
        assertEquals(List.of("kintai_20261003.sql"), names());   // .tmp は残らない
        assertTrue(r.summary().contains("file=kintai_20261003.sql"));
        assertTrue(r.summary().contains("deleted=0"));
    }

    @Test
    void rotation_keepsNewestSevenAndIgnoresOtherNames() throws IOException {
        for (int day = 20; day <= 29; day++) touch("kintai_202609" + day + ".sql");   // 10 世代
        touch("notes.txt");               // 名前が一致しないものは決して消さない
        touch("kintai_2026.sql");
        touch("other_20260101.sql");
        dumpWrites(VALID_BODY);

        BatchResult r = service(7).execute(TODAY);

        assertTrue(r.summary().contains("deleted=4"));   // 10 + 今回 1 = 11 件のうち古い 4 件
        List<String> kept = names().stream().filter(n -> n.matches("kintai_\\d{8}\\.sql")).toList();
        assertEquals(List.of("kintai_20260924.sql", "kintai_20260925.sql", "kintai_20260926.sql",
                "kintai_20260927.sql", "kintai_20260928.sql", "kintai_20260929.sql", "kintai_20261003.sql"), kept);
        assertTrue(names().containsAll(List.of("notes.txt", "kintai_2026.sql", "other_20260101.sql")));
    }

    @Test
    void sameDayRerunReplacesFileWithoutAddingGeneration() throws IOException {
        dumpWrites(VALID_BODY);
        service(7).execute(TODAY);
        dumpWrites(VALID_BODY + "-- second\n-- PostgreSQL database dump complete\n");

        service(7).execute(TODAY);

        assertEquals(List.of("kintai_20261003.sql"), names());
        assertTrue(Files.readString(dir.resolve("kintai_20261003.sql")).contains("-- second"));
    }

    @Test
    void pgDumpFailure_isFailedAndKeepsOldGenerations() throws IOException {
        for (int day = 20; day <= 29; day++) touch("kintai_202609" + day + ".sql");
        doThrow(new BackupException("E01", "pg_dump exit=1")).when(runner).dump(any());

        BatchResult r = service(7).execute(TODAY);

        assertEquals(BatchStatus.FAILED, r.status());
        assertEquals(2, r.exitCode());
        assertTrue(r.summary().contains("code=E01"));
        assertEquals(10, names().size());   // 失敗時は世代を一切削除しない
    }

    @Test
    void tooSmallDump_isE03AndTmpRemoved() throws IOException {
        dumpWrites("-- PostgreSQL database dump complete\n");   // マーカーはあるがサイズ不足

        BatchResult r = service(7).execute(TODAY);

        assertEquals(BatchStatus.FAILED, r.status());
        assertTrue(r.summary().contains("code=E03"));
        assertEquals(List.of(), names());
    }

    @Test
    void truncatedDumpWithoutMarker_isE03() throws IOException {
        dumpWrites("x".repeat(5000));   // ディスクフル等の途中切れ（終了コード 0 でも起こりうる）

        BatchResult r = service(7).execute(TODAY);

        assertEquals(BatchStatus.FAILED, r.status());
        assertTrue(r.summary().contains("code=E03"));
        assertEquals(List.of(), names());
    }

    @Test
    void failedRunNeverTouchesExistingFileOfSameDay() throws IOException {
        Files.writeString(dir.resolve("kintai_20261003.sql"), "previous good backup");
        dumpWrites("x".repeat(5000));   // 検証に失敗する

        service(7).execute(TODAY);

        assertEquals("previous good backup", Files.readString(dir.resolve("kintai_20261003.sql")));
    }

    @Test
    void propertiesToStringMasksPassword() {
        BackupProperties p = new BackupProperties("backup", "kintai_", 7, "pg_dump",
                "localhost", 5432, "kintaiflow", "kintaiflow", "secret-pw", 600, 1024);
        assertFalse(p.toString().contains("secret-pw"));
        assertTrue(p.toString().contains("dbPassword=***"));
    }
}
