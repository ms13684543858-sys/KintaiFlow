package com.example.kintaiflow.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import net.minidev.json.JSONArray;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API ドキュメント（OpenAPI）の結合テスト。
 *  - 有効にしたときだけ公開される（既定は無効）
 *  - コードにある全エンドポイントが、設計書の API-ID 付きで文書化されている（足し忘れを検出）
 *  - 提出済みの仕様書 docs/api/openapi.json が、現在のコードから生成したものと一致する（古くなったら失敗）
 * 仕様書を更新するには:  ./mvnw test -Dtest=ApiDocsIT -Dopenapi.update=true
 */
@TestPropertySource(properties = "kintaiflow.api-docs.enabled=true")
class ApiDocsIT extends IntegrationTestBase {

    /** テストは backend/ で動くので、プロジェクトのルートは 1 つ上。 */
    private static final Path SPEC = Path.of("..", "docs", "api", "openapi.json");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    private String generatedSpec() throws Exception {
        MvcResult res = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        return res.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void docsArePublicWhenEnabledAndDeclareBearerAuth() throws Exception {
        String spec = generatedSpec();   // 未ログインで取得できる
        assertEquals("勤怠フロー API", JsonPath.read(spec, "$.info.title"));
        assertEquals("bearer", JsonPath.read(spec, "$.components.securitySchemes.bearerAuth.scheme"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void everyEndpointInTheCodeIsDocumentedWithItsDesignApiId() throws Exception {
        String spec = generatedSpec();

        // Spring に実際に登録されている /api/** のエンドポイント
        Set<String> inCode = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            if (info.getPathPatternsCondition() == null) continue;
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (!pattern.startsWith("/api/")) continue;
                for (RequestMethod m : info.getMethodsCondition().getMethods()) inCode.add(m + " " + pattern);
            }
        }
        // 文書にあるエンドポイントと、その説明
        Set<String> inDocs = new TreeSet<>();
        Map<String, Object> paths = JsonPath.read(spec, "$.paths");
        for (String path : paths.keySet()) {
            Map<String, Object> ops = JsonPath.read(spec, "$.paths['" + path + "']");
            for (String method : ops.keySet()) {
                String key = method.toUpperCase() + " " + path;
                inDocs.add(key);
                // summary が無いとき PathNotFoundException ではなく null を返す（失敗メッセージを分かりやすくするため）
                String summary = JsonPath.using(Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL))
                        .parse(spec).read("$.paths['" + path + "']." + method + ".summary");
                assertTrue(summary != null && summary.matches("API-\\d{3} .+"), "API-ID 付きの説明が無い: " + key);
                JSONArray tags = JsonPath.read(spec, "$.paths['" + path + "']." + method + ".tags");
                assertFalse(tags.isEmpty(), "グループ（タグ）が無い: " + key);
            }
        }
        assertEquals(inCode, inDocs, "コードのエンドポイントと API ドキュメントが一致しない。"
                + "OpenApiConfig.SUMMARIES に説明を足す（または消す）こと");
        assertEquals(38, inCode.size(), "エンドポイント数が変わった。設計書 KF-BD-011 と README も更新すること");
    }

    @Test
    void loginIsDocumentedAsNotRequiringAuthentication() throws Exception {
        JSONArray security = JsonPath.read(generatedSpec(), "$.paths['/api/auth/login'].post.security");
        assertTrue(security.isEmpty(), "ログインは認証不要のはず");
    }

    @Test
    void committedSpecMatchesTheCode() throws Exception {
        String generated = generatedSpec();
        ObjectMapper pretty = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(SPEC.getParent());
            Files.writeString(SPEC, pretty.writeValueAsString(pretty.readTree(generated)) + "\n", StandardCharsets.UTF_8);
            System.out.println("openapi.json updated: " + SPEC.toAbsolutePath().normalize());
            return;
        }
        assertTrue(Files.exists(SPEC), "docs/api/openapi.json が無い。次で生成する: ./mvnw test -Dtest=ApiDocsIT -Dopenapi.update=true");
        Object committed = JsonPath.parse(Files.readString(SPEC, StandardCharsets.UTF_8)).json();
        Object current = JsonPath.parse(generated).json();
        assertEquals(committed, current, "docs/api/openapi.json がコードと食い違っている。"
                + "更新する: ./mvnw test -Dtest=ApiDocsIT -Dopenapi.update=true");
    }
}
