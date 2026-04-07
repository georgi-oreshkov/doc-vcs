package com.root.vcsbackend.shared.web;

import com.root.vcsbackend.api.AuthApi;
import com.root.vcsbackend.shared.config.KeycloakProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Implements the {@code GET /auth/login} endpoint defined in the OpenAPI spec.
 *
 * <p>Since the backend is a pure JWT resource server (not an OAuth2 client itself),
 * this endpoint acts as a browser entry-point: it builds the Keycloak Authorization
 * Code URL and returns a {@code 302 Found} redirect so the browser lands on the
 * Keycloak login page. After a successful login Keycloak redirects the browser to
 * {@code app.keycloak.redirect-uri} (the frontend callback) carrying the auth code.
 *
 * <p>Configuration (application.properties / env vars):
 * <ul>
 *   <li>{@code spring.security.oauth2.resourceserver.jwt.issuer-uri} /
 *       {@code KC_ISSUER_URI} — Keycloak realm issuer, e.g.
 *       {@code http://localhost:18080/realms/vcs}</li>
 *   <li>{@code app.keycloak.client-id} / {@code KC_CLIENT_ID} — Keycloak client,
 *       e.g. {@code vcs-frontend}</li>
 *   <li>{@code app.keycloak.redirect-uri} / {@code KC_REDIRECT_URI} — frontend
 *       callback URI, e.g. {@code http://localhost:5173/callback}</li>
 * </ul>
 */
@RestController
public class AuthController implements AuthApi {

    private final String issuerUri;
    private final KeycloakProperties keycloakProperties;

    public AuthController(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            KeycloakProperties keycloakProperties) {
        this.issuerUri = issuerUri;
        this.keycloakProperties = keycloakProperties;
    }

    /**
     * Redirects the browser to the Keycloak Authorization Code login page.
     *
     * <p>The authorization endpoint is derived from the configured issuer URI by
     * appending {@code /protocol/openid-connect/auth} — the standard Keycloak path.
     */
    @Override
    public ResponseEntity<Void> authLogin() {
        // Normalize issuer URI (strip trailing slash) then append KC auth path
        String baseIssuer = issuerUri.endsWith("/")
                ? issuerUri.substring(0, issuerUri.length() - 1)
                : issuerUri;

        URI authorizationUri = UriComponentsBuilder
                .fromUriString(baseIssuer + "/protocol/openid-connect/auth")
                .queryParam("client_id",     keycloakProperties.clientId())
                .queryParam("response_type", "code")
                .queryParam("redirect_uri",  keycloakProperties.redirectUri())
                .queryParam("scope",         "openid profile email")
                .build()
                .toUri();

        return ResponseEntity
                .status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authorizationUri.toString())
                .build();
    }
}

