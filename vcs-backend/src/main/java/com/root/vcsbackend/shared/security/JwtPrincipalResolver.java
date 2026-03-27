package com.root.vcsbackend.shared.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves the raw JWT from the SecurityContext into a typed JwtPrincipal.
 * Referenced by the @CurrentUser annotation expression: "@jwtPrincipalResolver.resolve(#this)"
 */
@Component("jwtPrincipalResolver")
public class JwtPrincipalResolver {

    public JwtPrincipal resolve(Object principal) {
        if (principal instanceof Jwt jwt) {
            return JwtPrincipal.from(jwt);
        }
        return null;
    }
}

