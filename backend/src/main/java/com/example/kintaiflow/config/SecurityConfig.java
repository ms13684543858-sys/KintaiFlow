package com.example.kintaiflow.config;

import com.example.kintaiflow.entity.User;
import com.example.kintaiflow.repository.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;

/** 認証・認可の設定（KF-BD-004 権限設計書、KF-DD-002 共通仕様）。 */
@Configuration
@EnableMethodSecurity   // @PreAuthorize を有効にする
public class SecurityConfig {

    /** パスワードは BCrypt でハッシュ化して保存・照合する（平文は保存しない）。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** JWT の署名鍵。application-local.properties の base64 文字列から作る。 */
    @Bean
    public SecretKey jwtSecretKey(@Value("${kintaiflow.jwt.secret}") String base64Secret) {
        byte[] bytes = Base64.getDecoder().decode(base64Secret.trim());
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** トークンの "role" を Spring Security の権限（ROLE_ADMIN など）に変換する。 */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter converter,
                                                   UserRepository userRepository) throws Exception {
        http
                .csrf(csrf -> csrf.disable())                       // トークン方式なので CSRF は不要
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")   // 管理系 API は管理者のみ（多重防御）
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        // トークン無し／不正／期限切れ → 401、権限不足 → 403 を、共通のエラー形式(JSON)で返す
                        .authenticationEntryPoint((req, res, ex) ->
                                writeError(res, 401, "E-001", "認証が必要です。再度ログインしてください。"))
                        .accessDeniedHandler((req, res, ex) ->
                                writeError(res, 403, "E-010", "この操作を行う権限がありません。")));
        // 初期パスワードのままのトークンは、パスワード変更 API 以外を拒否する（E-019）
        http.addFilterAfter(new ActiveUserFilter(userRepository), BearerTokenAuthenticationFilter.class);
        http.addFilterAfter(new PasswordChangeRequiredFilter(), ActiveUserFilter.class);
        return http.build();
    }

    /**
     * 発行済みトークンでも、ユーザーが無効化・削除されていたり、トークン発行後にパスワードが変更・再設定されていたら
     * 401 にする（退職者のトークンが期限まで使えてしまうのを防ぐ）。1リクエストにつき主キー検索1回。
     */
    static class ActiveUserFilter extends OncePerRequestFilter {
        private final UserRepository userRepository;

        ActiveUserFilter(UserRepository userRepository) {
            this.userRepository = userRepository;
        }

        @Override
        protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest req,
                                        jakarta.servlet.http.HttpServletResponse res,
                                        jakarta.servlet.FilterChain chain) throws jakarta.servlet.ServletException, java.io.IOException {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken token) {
                User user = null;
                try {
                    user = userRepository.findById(Long.valueOf(token.getName())).orElse(null);
                } catch (NumberFormatException ignored) {
                    // sub が数値でない = 不正なトークン
                }
                boolean valid = user != null && "ACTIVE".equals(user.getStatus());
                if (valid && user.getPasswordChangedAt() != null && token.getToken().getIssuedAt() != null) {
                    java.time.Instant changed = user.getPasswordChangedAt().atZone(AppTime.ZONE).toInstant()
                            .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
                    valid = !token.getToken().getIssuedAt().isBefore(changed);   // パスワード変更前に発行されたトークンは無効
                }
                if (!valid) {
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                    writeError(res, 401, "E-001", "認証が必要です。再度ログインしてください。");
                    return;
                }
            }
            chain.doFilter(req, res);
        }
    }

    /** mcp=true（要パスワード変更）のトークンで、パスワード変更以外の API を呼ぶと 403 を返す。 */
    static class PasswordChangeRequiredFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest req,
                                        jakarta.servlet.http.HttpServletResponse res,
                                        jakarta.servlet.FilterChain chain) throws jakarta.servlet.ServletException, java.io.IOException {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken token
                    && Boolean.TRUE.equals(token.getToken().getClaimAsBoolean("mcp"))
                    && !"/api/auth/password".equals(req.getRequestURI())) {
                writeError(res, 403, "E-019", "初期パスワードのままです。先にパスワードを変更してください。");
                return;
            }
            chain.doFilter(req, res);
        }
    }

    private static void writeError(jakarta.servlet.http.HttpServletResponse res, int status,
                                   String code, String message) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"fieldErrors\":[]}");
    }

    /** 開発中のフロントエンド（Vite: 5173）からの呼び出しを許可する。 */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${kintaiflow.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
