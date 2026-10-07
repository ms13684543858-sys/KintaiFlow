# バックエンド（Spring Boot）のコンテナイメージ。ビルド用と実行用の2段構成。
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
# ヘルスチェック用の curl と、BAT-003 バックアップ用の pg_dump（PostgreSQL 18 と同じメジャーバージョン以上が必要）を入れる。
# pg_dump は公式の PGDG リポジトリから取得する。root 以外のユーザーで実行する（セキュリティ）
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates gnupg \
 && install -d /usr/share/postgresql-common/pgdg \
 && curl -fsSL -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc https://www.postgresql.org/media/keys/ACCC4CF8.asc \
 && . /etc/os-release \
 && echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] https://apt.postgresql.org/pub/repos/apt ${VERSION_CODENAME}-pgdg main" > /etc/apt/sources.list.d/pgdg.list \
 && apt-get update && apt-get install -y --no-install-recommends postgresql-client-18 \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 kintai \
 && mkdir /backup && chown kintai /backup
COPY --from=build /app/target/*.jar app.jar
USER kintai
ENV TZ=Asia/Tokyo
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
