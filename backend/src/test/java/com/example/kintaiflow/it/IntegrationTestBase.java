package com.example.kintaiflow.it;

import com.example.kintaiflow.entity.User;
import com.example.kintaiflow.repository.LeaveTypeRepository;
import com.example.kintaiflow.repository.UserRepository;
import com.example.kintaiflow.service.LeaveBalanceService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 結合テストの土台：実際の Spring コンテキスト（セキュリティのフィルタ、Flyway、JPA）を、使い捨ての PostgreSQL に対して動かす。
 * HTTP は MockMvc で、認証は本物のログイン API で取ったトークンを使う（認証・認可の設定ごと検証するため）。
 * 開発用のダミーデータ投入やバッチはテスト中は止める（手元の application-local.properties の設定に左右されない）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(DockerAvailableCondition.class)
public abstract class IntegrationTestBase {

    /** 結合テスト用の使い捨てユーザーのパスワード（12文字以上・英数字）。 */
    protected static final String PASSWORD = "It-Password-2026";

    @Autowired protected MockMvc mvc;
    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected LeaveTypeRepository leaveTypeRepository;
    @Autowired protected LeaveBalanceService leaveBalanceService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", SharedPostgres.INSTANCE::getJdbcUrl);
        r.add("spring.datasource.username", SharedPostgres.INSTANCE::getUsername);
        r.add("spring.datasource.password", SharedPostgres.INSTANCE::getPassword);
        // 署名鍵はテスト専用の固定値（32バイト以上の base64）
        r.add("kintaiflow.jwt.secret", () -> "aXQtb25seS1qd3Qtc2VjcmV0LWl0LW9ubHktand0LXNlY3JldC0wMTIzNDU2Nzg5YWJjZGVm");
        r.add("kintaiflow.dev.seed-enabled", () -> "false");
        r.add("kintaiflow.dev.seed-password", () -> "");
        r.add("kintaiflow.bootstrap.admin-email", () -> "");
        r.add("kintaiflow.bootstrap.admin-password", () -> "");
        r.add("kintaiflow.batch.scheduling-enabled", () -> "false");
    }

    // ------------------------------------------------------------ テストデータ

    /** 一意なメールアドレスのユーザーを作る（部署は 2 = システム開発事業部）。 */
    protected User createUser(String role, User manager) {
        User u = new User();
        u.setEmail(role.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@it.example.com");
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setName("テスト " + role);
        u.setRole(role);
        u.setDepartmentId(2L);
        u.setManagerId(manager == null ? null : manager.getId());
        u.setHireDate(LocalDate.of(2020, 4, 1));
        u.setStatus("ACTIVE");
        return userRepository.save(u);
    }

    protected Long annualLeaveTypeId() {
        return leaveTypeRepository.findByName("年次有給休暇").orElseThrow().getId();
    }

    /** 年次有給休暇を days 日付与する（付与日は常に有効になる 1 か月前）。 */
    protected void grantAnnualLeave(User user, String days) {
        leaveBalanceService.grant(user.getId(), annualLeaveTypeId(), LocalDate.now().minusMonths(1), new BigDecimal(days));
    }

    // ------------------------------------------------------------ HTTP

    /** 本物のログイン API を呼んで JWT を返す。 */
    protected String login(User user) throws Exception {
        return login(user.getEmail(), PASSWORD);
    }

    protected String login(String email, String password) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andReturn();
        if (res.getResponse().getStatus() != 200)
            throw new AssertionError("login failed: " + res.getResponse().getStatus() + " " + res.getResponse().getContentAsString());
        return JsonPath.read(res.getResponse().getContentAsString(), "$.token");
    }

    protected MockHttpServletRequestBuilder authGet(String url, String token) {
        return get(url).header("Authorization", "Bearer " + token);
    }

    protected MockHttpServletRequestBuilder authPost(String url, String token, String json) {
        MockHttpServletRequestBuilder b = post(url).header("Authorization", "Bearer " + token);
        return json == null ? b : b.contentType(MediaType.APPLICATION_JSON).content(json);
    }

    protected MockHttpServletRequestBuilder authPut(String url, String token, String json) {
        return put(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    protected static <T> T json(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
