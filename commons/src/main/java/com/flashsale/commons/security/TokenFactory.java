package com.flashsale.commons.security;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.List;

@Component
public class TokenFactory {

    private final SecretKey key;
    private final Clock clock;

    public TokenFactory(@Value("${app.jwt.secret}") String secret, Clock clock) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.clock = clock;
    }

    /** Short-lived token for service-to-service calls, carrying the caller's tenant. */
    public String serviceToken(String tenantId) {
        return token("internal-service", tenantId, List.of(JwtClaims.ROLE_SERVICE), Duration.ofSeconds(60));
    }

    public String token(String subject, String tenantId, List<String> roles, Duration ttl) {
        var now = clock.instant();
        return Jwts.builder()
                .subject(subject)
                .claim(JwtClaims.TENANT_ID, tenantId)
                .claim(JwtClaims.ROLES, roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }
}
