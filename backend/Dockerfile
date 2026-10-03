# バックエンド（Spring Boot）のコンテナイメージ。ビルド用と実行用の2段構成。
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
# ヘルスチェック用に curl を入れ、root 以外のユーザーで実行する（セキュリティ）
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 kintai
COPY --from=build /app/target/*.jar app.jar
USER kintai
ENV TZ=Asia/Tokyo
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
