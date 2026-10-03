-- 開発用：データベースを空に戻す（全テーブル・Flyway の履歴・関数を削除）。本番では絶対に実行しない。
-- 実行後にバックエンドを起動すると、Flyway が V1 から作り直し、開発用ユーザー100名が投入される。
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
