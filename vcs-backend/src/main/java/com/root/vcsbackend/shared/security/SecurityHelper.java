package com.root.vcsbackend.shared.security;

import com.root.vcsbackend.shared.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves the current authenticated user from the SecurityContext.
 * Used by controllers that override generated API interfaces and therefore
 * cannot add @CurrentUser as an extra parameter on @Override methods.
 */
@Component
public class SecurityHelper {

    public JwtPrincipal currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt jwt) {
            return JwtPrincipal.from(jwt);
        }
        throw new AppException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }
}

