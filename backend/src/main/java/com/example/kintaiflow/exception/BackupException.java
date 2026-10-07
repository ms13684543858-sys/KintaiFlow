package com.example.kintaiflow.exception;

/**
 * BAT-003 バックアップ失敗。分類コード: E01 pg_dump 失敗・タイムアウト／E02 起動不可／E03 検証失敗／E04 I/O 失敗。
 * メッセージにパスワードを含めないこと。
 */
public class BackupException extends RuntimeException {

    private final String code;

    public BackupException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
