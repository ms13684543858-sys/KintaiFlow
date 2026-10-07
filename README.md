# 勤怠フロー（KintaiFlow）
Spring Boot 4.1.1 + Vue 3 + PostgreSQL。学習用プロジェクト（和式ウォーターフォール＋AI支援開発）。

## ドキュメント（docs/）
- 01_要件定義
- 02_基本設計
  - 01_機能・画面一覧 / 02_画面設計書（17本）/ 03_データ設計 / 04_API・外部IF / 05_権限・メッセージ・帳票 / 06_バッチ（基本）/ 07_非機能
- 03_詳細設計
  - 01_オンライン設計 … KF-DD-001（総覧）＋ KF-DD-ONL-001〜021（1処理1ファイルの詳細設計書）
  - 02_バッチ設計 / 03_テーブル設計 / 04_クラス・内部IF設計 / 05_開発規約
- 04_テスト（後工程）
- 99_旧版（削除可）

## 実装
- backend/ … Spring Boot（schema.sql＝DDL＋初期データ。pgAdmin で手動実行）
- frontend/ … Vue 3（作成予定）
- 機密設定は backend/src/main/resources/application-local.properties（Git 管理外）に置く

## バッチ（Spring @Scheduled。詳細は docs/03_詳細設計/02_バッチ設計）
| ID | 内容 | 実行契機（JST） |
|---|---|---|
| BAT-001 | 年次有給休暇の自動付与（入社6か月後、以降1年ごと） | 毎日 02:00 |
| BAT-002 | 年5日取得義務アラート（本人と管理者へ通知） | 毎月1日 03:00 |
| BAT-003 | DB バックアップ（pg_dump、7世代） | 毎日 03:30 |

- 2台目以降のインスタンスでは `kintaiflow.batch.scheduling-enabled=false` にする。
- 手動で1回だけ実行する（管理 API は無い）。終了コードは 0:正常 1:一部失敗 2:異常。
  ```
  ./mvnw spring-boot:run -Dspring-boot.run.arguments="--kintaiflow.batch.run-once=BAT-001 --kintaiflow.batch.scheduling-enabled=false --server.port=0"
  ```
  `--kintaiflow.batch.target-date=yyyy-MM-dd` で基準日を指定できる（BAT-003 ではファイル名の日付になる）。
- BAT-003 は `pg_dump`（PostgreSQL 18 以上のクライアント）が必要。Docker 構成ではイメージに同梱し、`backupdata` volume の `/backup` に出力する。
  ローカルで動かすときは `pg_dump` を PATH に通すか `kintaiflow.batch.backup.pg-dump-path` で指定する。
  出力先は `kintaiflow.batch.backup.dir`（既定 `backup/`、Git 管理外）。個人情報を含むので、本番は DB と別ディスクに置き、オフサイトへのコピーは運用で行う。
- 復元は `psql -f backup/kintai_yyyyMMdd.sql <DB>`（`--clean --if-exists` 付きなので既存 DB にも流せる）。復元できるか定期的に別 DB で確認すること。
