package com.example.kintaiflow.it;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 結合テスト全体で共有する使い捨ての PostgreSQL（本番と同じメジャーバージョン）。
 * 最初に使われたときに1回だけ起動し、JVM の終了時に Testcontainers（Ryuk）が片付ける。
 * テストごとにコンテナを作ると遅いので共有するが、各テストは一意なメールアドレスのデータを自分で作る。
 */
final class SharedPostgres {

    static final PostgreSQLContainer INSTANCE = new PostgreSQLContainer("postgres:18")
            .withDatabaseName("kintaiflow_it")
            .withUsername("kintai_it")
            .withPassword("it-only-password");

    static {
        INSTANCE.start();
    }

    private SharedPostgres() {}
}
