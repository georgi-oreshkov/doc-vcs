package com.root.vcsbackend.shared.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public record JwtPrincipal(UUID userId, String email, String name) {

    public static JwtPrincipal from(Jwt jwt) {
        // TODO: implement
        return null;
    }
}

