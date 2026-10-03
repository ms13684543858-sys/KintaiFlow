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
