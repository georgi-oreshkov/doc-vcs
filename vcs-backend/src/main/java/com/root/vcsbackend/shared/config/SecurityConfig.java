package com.root.vcsbackend.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.root.vcsbackend.shared.security.UserProfileSyncFilter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
@org.springframework.boot.context.properties.EnableConfigurationProperties(KeycloakProperties.class)
public class SecurityConfig {

    private final UserProfileSyncFilter userProfileSyncFilter;

    public SecurityConfig(UserProfileSyncFilter userProfileSyncFilter) {
        this.userProfileSyncFilter = userProfileSyncFilter;
    }

    /**
     * Comma-separated list of allowed origins.
     * Override via env var CORS_ALLOWED_ORIGINS, e.g.:
     *   CORS_ALLOWED_ORIGINS=http://localhost:3000,https://app.example.com
     * Defaults to localhost:3000 for local development.
     */
    @Value("${cors.allowed-origins:http://localhost:3000}")
    private List<String> allowedOrigins;

    /** Public issuer URI — must match the {@code iss} claim in Keycloak-issued JWTs. */
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    /**
     * JWKS endpoint used to fetch Keycloak's signing keys.
     * In a fully-containerised setup this points to the Docker-internal address
     * ({@code http://keycloak:8080/…}) while {@link #issuerUri} stays at the
     * public address ({@code http://localhost:18080/…}).
     * @see <a href="docs/kc_setup_container.md">Keycloak container setup guide</a>
     */
    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    /**
     * Custom {@link JwtDecoder} that decouples key-fetching from issuer validation.
     * <ul>
     *   <li>Fetches JWKS from {@link #jwkSetUri} (reachable inside Docker via service DNS).</li>
     *   <li>Validates the {@code iss} claim against {@link #issuerUri} (the public URL that
     *       Keycloak stamps into tokens).</li>
     * </ul>
     * Declaring this bean suppresses Spring Boot's autoconfigured {@code JwtDecoder}.
     */
    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
        return decoder;
    }

    /**
     * Dedicated security filter chain for the MinIO webhook endpoint.
     * NO JWT processing - uses custom token validation in controller.
     * Must be @Order(1) to take precedence over the main filter chain.
     */
    @Bean
    @org.springframework.core.annotation.Order(1)
    SecurityFilterChain webhookFilterChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/internal/webhook/minio")
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .build();
    }

    /**
     * Main security filter chain for all other endpoints - requires JWT authentication.
     */
    @Bean
    @org.springframework.core.annotation.Order(2)
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/login").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            .addFilterAfter(userProfileSyncFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // pre-flight cache: 1 hour

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        // Map Keycloak realm_access.roles → GrantedAuthority("ROLE_XXX")
        var grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("realm_access.roles");
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return converter;
    }
}
