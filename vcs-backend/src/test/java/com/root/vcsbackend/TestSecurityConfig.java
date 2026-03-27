package com.root.vcsbackend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

/**
 * Replaces the autoconfigured {@link JwtDecoder} in tests so the application
 * context can load without an active Keycloak / OIDC discovery endpoint.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    JwtDecoder testJwtDecoder() {
        // Returns a minimal, pre-built JWT for any token value.
        // No HTTP calls are made at startup or during tests.
        return token -> Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("00000000-0000-0000-0000-000000000001")
                .claim("email", "test@example.com")
                .claim("name", "Test User")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}

