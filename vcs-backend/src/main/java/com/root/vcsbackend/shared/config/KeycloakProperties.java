package com.root.vcsbackend.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code app.keycloak.*} block from application.properties.
 *
 * <ul>
 *   <li>{@code clientId}     — Keycloak client ID (e.g. {@code vcs-frontend}).
 *       Override via env var {@code KC_CLIENT_ID}.</li>
 *   <li>{@code redirectUri}  — URI Keycloak redirects the browser to after a
 *       successful login (the frontend callback page).
 *       Override via env var {@code KC_REDIRECT_URI}.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.keycloak")
public record KeycloakProperties(
        String clientId,
        String redirectUri
) {}

