package com.example.kintaiflow.batch;

/** バッチの終了状態。終了コード（0:正常 1:一部失敗 2:異常）とログレベルを決める。 */
public enum BatchStatus {
    SUCCESS(0), PARTIAL(1), FAILED(2);

    private final int exitCode;

    BatchStatus(int exitCode) { this.exitCode = exitCode; }

    public int exitCode() { return exitCode; }
}
