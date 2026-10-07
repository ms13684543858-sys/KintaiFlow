package com.example.kintaiflow.service;

import com.example.kintaiflow.config.BackupProperties;
import com.example.kintaiflow.exception.BackupException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** BAT-003 pg_dump を外部プロセスとして起動する。引数は配列で渡しシェルを経由しない。テストでは差し替える。 */
@Component
public class PgDumpRunner {

    private static final int STDERR_MAX = 4096;

    private final BackupProperties props;

    public PgDumpRunner(BackupProperties props) {
        this.props = props;
    }

    /**
     * プレーン SQL を outFile に書き出す。
     * @throws BackupException E01: 終了コード≠0／タイムアウト、E02: 実行ファイルが無い・起動不可
     */
    public void dump(Path outFile) {
        List<String> cmd = List.of(
                props.pgDumpPath(),
                "--host=" + props.dbHost(), "--port=" + props.dbPort(),
                "--username=" + props.dbUser(), "--dbname=" + props.dbName(),
                "--format=plain", "--no-owner", "--no-privileges", "--clean", "--if-exists",
                "--encoding=UTF8", "--no-password",
                "--file=" + outFile.toAbsolutePath());   // 引数にパスワードを含めない
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (props.dbPassword() != null && !props.dbPassword().isEmpty())
            pb.environment().put("PGPASSWORD", props.dbPassword());   // 子プロセスの環境変数にだけ設定（.pgpass 方式も可）
        pb.environment().put("PGCONNECT_TIMEOUT", "30");

        Path err;
        try {
            err = Files.createTempFile("pgdump-", ".err");   // 標準エラー(+標準出力)の受け皿
        } catch (IOException e) {
            throw new BackupException("E04", "cannot create temp file: " + e.getMessage());
        }
        pb.redirectErrorStream(true).redirectOutput(err.toFile());
        try {
            Process p;
            try {
                p = pb.start();
            } catch (IOException e) {
                throw new BackupException("E02", "cannot start pg_dump: " + e.getMessage());
            }
            try {
                if (!p.waitFor(props.timeoutSeconds(), TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    throw new BackupException("E01", "pg_dump timeout " + props.timeoutSeconds() + "s");
                }
            } catch (InterruptedException e) {
                p.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new BackupException("E01", "interrupted");
            }
            if (p.exitValue() != 0)
                throw new BackupException("E01", "pg_dump exit=" + p.exitValue() + " stderr=" + readHead(err));
        } finally {
            try { Files.deleteIfExists(err); } catch (IOException ignored) { /* 一時ファイルの掃除失敗は無視 */ }
        }
    }

    /** 先頭 4KB を読み、パスワードが含まれていれば *** に置換する（失敗原因の表示用。読めなければ空文字）。 */
    private String readHead(Path f) {
        try {
            String s = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
            if (s.length() > STDERR_MAX) s = s.substring(0, STDERR_MAX);
            String pw = props.dbPassword();
            return pw == null || pw.isEmpty() ? s : s.replace(pw, "***");
        } catch (IOException e) {
            return "";
        }
    }
}
