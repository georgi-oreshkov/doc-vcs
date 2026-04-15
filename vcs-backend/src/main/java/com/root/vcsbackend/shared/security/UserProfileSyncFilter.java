package com.root.vcsbackend.shared.security;

import com.root.vcsbackend.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ensures a {@code user_profiles} row exists for every authenticated Keycloak user
 * before any controller code runs.
 *
 * <p>Runs once per request, after the JWT authentication filter has populated the
 * {@link SecurityContextHolder}. Uses {@link UserService#getOrCreateProfile} which
 * is idempotent — a single {@code SELECT} when the row already exists.
 */
@Component
public class UserProfileSyncFilter extends OncePerRequestFilter {

    private final UserService userService;

    public UserProfileSyncFilter(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt jwt) {
            userService.getOrCreateProfile(JwtPrincipal.from(jwt));
        }

        filterChain.doFilter(request, response);
    }
}
