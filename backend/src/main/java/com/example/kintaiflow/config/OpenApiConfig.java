package com.example.kintaiflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

/**
 * API ドキュメント（OpenAPI）の見出しと、各エンドポイントの説明。
 * 設計書 KF-BD-011（API 一覧）の API-ID と対応づける。Controller には手を入れず、ここに集約している。
 * 新しいエンドポイントを足したら、下の表にも足す（ApiDocsIT が「説明の無いエンドポイント」を検出して失敗する）。
 */
@Configuration
public class OpenApiConfig {

    /** "メソッド パス" → "API-ID 名称"（設計書 KF-BD-011 と同じ ID・名称）。 */
    static final Map<String, String> SUMMARIES = Map.ofEntries(
            entry("POST /api/auth/login", "API-001 ログイン"),
            entry("POST /api/auth/password", "API-023 パスワード変更"),
            entry("POST /api/attendance/clock-in", "API-002 出勤打刻"),
            entry("POST /api/attendance/clock-out", "API-003 退勤打刻"),
            entry("GET /api/attendance", "API-004 月次勤怠取得"),
            entry("POST /api/attendance/break-start", "API-024 休憩・離席の開始"),
            entry("POST /api/attendance/break-end", "API-025 休憩・離席の終了"),
            entry("POST /api/requests", "API-005 申請作成"),
            entry("GET /api/requests", "API-006 申請一覧取得"),
            entry("GET /api/requests/{id}", "API-007 申請詳細取得"),
            entry("POST /api/requests/{id}/withdraw", "API-008 申請取下げ"),
            entry("POST /api/requests/{id}/submit", "API-022 申請提出"),
            entry("GET /api/approvals/pending", "API-009 承認待ち一覧"),
            entry("POST /api/approvals/{id}/approve", "API-010 承認（{id} は承認ステップID）"),
            entry("POST /api/approvals/{id}/return", "API-011 差戻し（{id} は承認ステップID）"),
            entry("POST /api/approvals/{id}/reject", "API-012 却下（{id} は承認ステップID）"),
            entry("GET /api/leave-balances", "API-013 休暇残日数取得"),
            entry("GET /api/leave-types", "API-021 有効休暇種別一覧取得"),
            entry("GET /api/notifications", "API-019 通知一覧取得"),
            entry("POST /api/notifications/{id}/read", "API-019 通知の既読化"),
            entry("GET /api/departments", "API-026 部署一覧取得"),
            entry("GET /api/manager/subordinates/summary", "API-020 部下の勤怠・休暇の集計取得"),
            entry("GET /api/admin/users", "API-014 ユーザー一覧（検索）"),
            entry("POST /api/admin/users", "API-014 ユーザー登録"),
            entry("GET /api/admin/users/{id}", "API-014 ユーザー詳細"),
            entry("PUT /api/admin/users/{id}", "API-014 ユーザー更新"),
            entry("PUT /api/admin/users/{id}/password", "API-014 パスワードの設定（管理者による再設定）"),
            entry("GET /api/admin/leave-types", "API-015 休暇種別一覧"),
            entry("POST /api/admin/leave-types", "API-015 休暇種別の登録"),
            entry("GET /api/admin/leave-types/{id}", "API-015 休暇種別の詳細"),
            entry("PUT /api/admin/leave-types/{id}", "API-015 休暇種別の更新"),
            entry("GET /api/admin/holidays", "API-016 祝日一覧"),
            entry("POST /api/admin/holidays", "API-016 祝日の登録"),
            entry("DELETE /api/admin/holidays/{id}", "API-016 祝日の削除"),
            entry("GET /api/admin/leave-grants", "API-017 休暇付与の履歴"),
            entry("POST /api/admin/leave-grants", "API-017 休暇の手動付与"),
            entry("PUT /api/admin/leave-grants/{id}", "API-017 付与日数の調整"),
            entry("GET /api/admin/reports/monthly", "API-018 月次集計取得"));

    /** パスの接頭辞 → Swagger UI のグループ名（長い接頭辞を先に判定する）。 */
    private static final List<Map.Entry<String, String>> TAGS = List.of(
            entry("/api/admin/", "管理（管理者のみ）"),
            entry("/api/auth/", "認証"),
            entry("/api/attendance", "打刻・月次勤怠"),
            entry("/api/requests", "申請（休暇・打刻修正）"),
            entry("/api/approvals", "承認（上長・管理者）"),
            entry("/api/manager/", "上長向け集計"),
            entry("/api/leave-", "休暇の参照"),
            entry("/api/notifications", "通知"),
            entry("/api/departments", "部署の参照"));

    @Bean
    public OpenAPI kintaiFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("勤怠フロー API")
                        .version("1.0")
                        .description("""
                                打刻・休暇申請・承認を扱う REST API。設計書 KF-BD-011（API 一覧）の API-ID を、各エンドポイントの名前の先頭に付けています。

                                **認証**：`POST /api/auth/login` で JWT を取得し、画面右上の **Authorize** に貼り付けると、以降のリクエストに `Authorization: Bearer ...` が付きます。

                                **エラー**：失敗時は `{ "code": "E-xxx", "message": "...", "fieldErrors": [...] }` の形式で返します（コード一覧は設計書 KF-BD-006）。
                                401 は未認証、403 は権限なし、409 は状態の競合（二重打刻・二重処理など）、423 はアカウントのロックです。"""))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    /** 各エンドポイントに、設計書の API-ID 付きの説明とグループ名を付ける。 */
    @Bean
    public OpenApiCustomizer operationDocs() {
        return openApi -> {
            if (openApi.getPaths() == null) return;
            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, op) -> {
                String summary = SUMMARIES.get(method.name() + " " + path);
                if (summary != null) op.setSummary(summary);
                TAGS.stream().filter(t -> path.startsWith(t.getKey())).findFirst()
                        .ifPresent(t -> op.setTags(List.of(t.getValue())));
                if ("/api/auth/login".equals(path)) op.setSecurity(List.of());   // ログインは認証不要
            }));
        };
    }
}
