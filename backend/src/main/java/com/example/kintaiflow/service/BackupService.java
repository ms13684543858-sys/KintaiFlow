package com.example.kintaiflow.service;

import com.example.kintaiflow.batch.BatchResult;
import com.example.kintaiflow.config.BackupProperties;
import com.example.kintaiflow.exception.BackupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * BAT-003 データバックアップの本体。一時ファイルへダンプ → 検証 → 最終名へ原子的に移動 → 古い世代の整理。
 * 失敗しても例外は投げず BatchResult(failed=1) を返し、その場合は既存の世代を一切削除しない。DB は更新しない。
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final String COMPLETE_MARKER = "-- PostgreSQL database dump complete";

    private final BackupProperties props;
    private final PgDumpRunner pgDumpRunner;

    public BackupService(BackupProperties props, PgDumpRunner pgDumpRunner) {
        this.props = props;
        this.pgDumpRunner = pgDumpRunner;
    }

    public BatchResult execute(LocalDate today) {
        long t0 = System.nanoTime();
        Path dir = Paths.get(props.dir()).toAbsolutePath();
        String name = props.filePrefix() + today.format(DateTimeFormatter.BASIC_ISO_DATE) + ".sql";   // kintai_20261003.sql
        Path target = dir.resolve(name);
        Path tmp = dir.resolve(name + ".tmp");
        log.info("BAT-003 START targetDate={} file={} keep={}", today, name, props.retention());
        try {
            prepareDir(dir);
            pgDumpRunner.dump(tmp);
            verify(tmp);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);   // 同日の再実行は上書き
            restrict(target);
            long size = Files.size(target);
            int deleted = rotate(dir, props.filePrefix(), props.retention());
            BatchResult r = result(today, t0, 1, 0, "file=" + name + " sizeBytes=" + size + " deleted=" + deleted);
            log.info(r.summary());
            return r;
        } catch (BackupException e) {
            deleteQuietly(tmp);
            log.error("BAT-003 FAILED code={} reason={}", e.getCode(), e.getMessage());   // パスワードは含まれない
            return result(today, t0, 0, 1, "code=" + e.getCode());
        } catch (IOException e) {
            deleteQuietly(tmp);
            log.error("BAT-003 FAILED code=E04 reason={}", e.toString());
            return result(today, t0, 0, 1, "code=E04");
        }
    }

    private static BatchResult result(LocalDate today, long t0, int succeeded, int failed, String detail) {
        long ms = (System.nanoTime() - t0) / 1_000_000;
        return new BatchResult("BAT-003", today, 0, 1, succeeded, 0, failed, ms, detail);   // targets=1 固定
    }

    private void prepareDir(Path dir) {
        try {
            Files.createDirectories(dir);
            if (!Files.isWritable(dir)) throw new IOException("directory is not writable: " + dir);
            try {
                Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));   // 個人情報を含むため 700
            } catch (UnsupportedOperationException | IOException ignored) { /* Windows など POSIX でない環境は無視 */ }
        } catch (IOException e) {
            throw new BackupException("E04", "cannot prepare backup directory: " + e.getMessage());
        }
    }

    private void restrict(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));   // 600
        } catch (UnsupportedOperationException | IOException ignored) { /* POSIX でない環境は無視 */ }
    }

    /** ディスクフル等で途中切れしたダンプを検出する（終了コード 0 でも起こりうる）。 */
    void verify(Path p) {
        try {
            if (!Files.isRegularFile(p) || Files.size(p) < props.minSizeBytes())
                throw new BackupException("E03", "dump file missing or too small");
            try (RandomAccessFile f = new RandomAccessFile(p.toFile(), "r")) {   // 末尾 1KB を読む
                byte[] buf = new byte[(int) Math.min(1024, f.length())];
                f.seek(f.length() - buf.length);
                f.readFully(buf);
                if (!new String(buf, StandardCharsets.UTF_8).contains(COMPLETE_MARKER))
                    throw new BackupException("E03", "dump completion marker not found");
            }
        } catch (IOException e) {
            throw new BackupException("E04", "cannot read dump file: " + e.getMessage());
        }
    }

    /** 世代整理。名前が「接頭辞+8桁+.sql」に完全一致するものだけを対象に、新しい keep 件を残して削除する。 */
    int rotate(Path dir, String prefix, int keep) throws IOException {
        Pattern pat = Pattern.compile("^" + Pattern.quote(prefix) + "\\d{8}\\.sql$");
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> pat.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())   // 日付降順（桁数固定なので文字列比較でよい）
                    .toList();
        }
        int deleted = 0;
        for (Path old : files.stream().skip(keep).toList()) {
            try {
                Files.delete(old);
                deleted++;
            } catch (IOException e) {
                log.warn("BAT-003 old backup delete failed file={}", old.getFileName());   // 継続して次へ
            }
        }
        return deleted;
    }

    private static void deleteQuietly(Path p) {
        try { Files.deleteIfExists(p); } catch (IOException ignored) { /* 削除失敗は無視 */ }
    }
}
