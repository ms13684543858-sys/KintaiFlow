package com.example.kintaiflow.it;

import com.example.kintaiflow.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 認証・認可の結合テスト：未ログイン、ロール別アクセス、ログイン失敗とロック、初期パスワード、トークンの失効。 */
class SecurityAccessIT extends IntegrationTestBase {

    private static final String MONTH = YearMonth.now().toString();

    // ---------------------------------------------------------------- 未認証・不正トークン

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/requests")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/attendance?month=" + MONTH)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = login(createUser("EMPLOYEE", null));
        mvc.perform(authGet("/api/requests", token)).andExpect(status().isOk());
        // 末尾を書き換える（署名が合わなくなる）
        String forged = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");
        mvc.perform(authGet("/api/requests", forged)).andExpect(status().isUnauthorized());
        mvc.perform(authGet("/api/requests", "not-a-jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void apiDocsAreNotExposedUnlessExplicitlyEnabled() throws Exception {
        // 既定（kintaiflow.api-docs.enabled=false）では、API の一覧を外に見せない。有効化したときの動作は ApiDocsIT
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- ロール別アクセス

    /** 期待するステータス：管理 API（管理者のみ）／承認待ち（上長・管理者）／部下の集計（上長のみ）。 */
    @ParameterizedTest(name = "{0}: admin={1} approvals={2} subordinates={3}")
    @CsvSource({
            "EMPLOYEE, 403, 403, 403",
            "MANAGER,  403, 200, 200",
            "ADMIN,    200, 200, 403"
    })
    void roleBasedAccess(String role, int admin, int approvals, int subordinates) throws Exception {
        String token = login(createUser(role, null));
        mvc.perform(authGet("/api/admin/users", token)).andExpect(status().is(admin));
        mvc.perform(authGet("/api/approvals/pending", token)).andExpect(status().is(approvals));
        mvc.perform(authGet("/api/manager/subordinates/summary?month=" + MONTH, token)).andExpect(status().is(subordinates));
    }

    // ---------------------------------------------------------------- ログイン

    @Test
    void wrongPasswordAndUnknownUserGetTheSameResponse() throws Exception {
        User u = createUser("EMPLOYEE", null);
        String wrongPassword = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"Wrong-Password-1\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("E-001"))
                .andReturn().getResponse().getContentAsString();
        String unknownUser = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody-" + System.nanoTime() + "@it.example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("E-001"))
                .andReturn().getResponse().getContentAsString();
        // ユーザーの存在を推測できないよう、メッセージも同じ
        assertEquals(wrongPassword, unknownUser);
    }

    @Test
    void emailIsNormalizedOnLogin() throws Exception {
        User u = createUser("EMPLOYEE", null);
        String sloppy = "  " + u.getEmail().toUpperCase() + "  ";
        assertTrue(login(sloppy, PASSWORD).length() > 20);
    }

    @Test
    void inactiveUserCannotLogin() throws Exception {
        User u = createUser("EMPLOYEE", null);
        u.setStatus("INACTIVE");
        userRepository.save(u);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("E-001"));
    }

    @Test
    void accountIsLockedAfterFiveFailuresEvenWithCorrectPassword() throws Exception {
        User u = createUser("EMPLOYEE", null);
        String bad = "{\"email\":\"" + u.getEmail() + "\",\"password\":\"Wrong-Password-1\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isUnauthorized());
        }
        // 6 回目は正しいパスワードでも 423 Locked（総当たりを防ぐ）
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("E-017"));
    }

    @Test
    void successfulLoginResetsTheFailureCounter() throws Exception {
        User u = createUser("EMPLOYEE", null);
        String bad = "{\"email\":\"" + u.getEmail() + "\",\"password\":\"Wrong-Password-1\"}";
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isUnauthorized());
        }
        login(u);   // 4 回失敗のあと成功 → カウンターが 0 に戻る
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isUnauthorized());
        }
        login(u);   // 4 回失敗しても、まだロックされていない
    }

    // ---------------------------------------------------------------- 初期パスワード・トークン失効

    @Test
    void userWithInitialPasswordMustChangeItBeforeUsingTheApi() throws Exception {
        User u = createUser("EMPLOYEE", null);
        u.setMustChangePassword(true);
        userRepository.save(u);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true));
        String token = login(u);

        // パスワード変更が済むまで、ほかの API は使えない
        mvc.perform(authGet("/api/requests", token)).andExpect(status().isForbidden());
        // 弱いパスワードは拒否される
        mvc.perform(authPost("/api/auth/password", token, "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"short1\"}"))
                .andExpect(status().isBadRequest());
        // ポリシーを満たす新しいパスワードに変更できる
        String newPassword = "Brand-New-Pass-2027";
        mvc.perform(authPost("/api/auth/password", token, "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().is2xxSuccessful());

        // 変更後は新しいパスワードでログインでき、通常どおり API を使える。古いパスワードは使えない
        String newToken = login(u.getEmail(), newPassword);
        mvc.perform(authGet("/api/requests", newToken)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenIssuedBeforeAPasswordResetStopsWorking() throws Exception {
        User employee = createUser("EMPLOYEE", null);
        String adminToken = login(createUser("ADMIN", null));
        String oldToken = login(employee);
        mvc.perform(authGet("/api/requests", oldToken)).andExpect(status().isOk());

        Thread.sleep(1100);   // トークンの発行時刻は秒単位。リセットを必ず後の秒にする
        mvc.perform(authPut("/api/admin/users/" + employee.getId() + "/password", adminToken,
                "{\"newPassword\":\"Reset-By-Admin-2027\"}")).andExpect(status().is2xxSuccessful());

        // 奪われたかもしれない古いトークンは、パスワードを変えた時点で使えなくなる
        mvc.perform(authGet("/api/requests", oldToken)).andExpect(status().isUnauthorized());
    }
}
