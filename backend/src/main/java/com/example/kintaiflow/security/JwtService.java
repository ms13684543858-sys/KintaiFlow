package com.example.kintaiflow.security;

import com.example.kintaiflow.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** ログイン成功時に JWT（署名付きトークン）を発行する。 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final long expirationHours;

    public JwtService(JwtEncoder encoder,
                      @Value("${kintaiflow.jwt.expiration-hours}") long expirationHours) {
        this.encoder = encoder;
        this.expirationHours = expirationHours;
    }

    public String issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("kintaiflow")
                .issuedAt(now)
                .expiresAt(now.plus(expirationHours, ChronoUnit.HOURS))
                .subject(String.valueOf(user.getId()))   // 誰のトークンか
                .claim("role", user.getRole())           // ロール（権限チェックに使う）
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
