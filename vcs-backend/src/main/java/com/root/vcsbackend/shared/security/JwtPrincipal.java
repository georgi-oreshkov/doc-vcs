package com.root.vcsbackend.shared.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public record JwtPrincipal(UUID userId, String email, String name) {

    public static JwtPrincipal from(Jwt jwt) {
        return new JwtPrincipal(
            UUID.fromString(jwt.getSubject()),
            jwt.getClaimAsString("email"),
            jwt.getClaimAsString("name")
        );
    }
}
