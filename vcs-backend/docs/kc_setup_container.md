# Keycloak JWT Issuer Mismatch — Fully Containerised Setup

## The Problem

When **every service runs in Docker** (including `vcs-backend`), there is a
fundamental conflict between the URL browsers use to reach Keycloak and the URL
the backend needs to reach it through.

### Why it happens

Keycloak is configured with `KC_HOSTNAME: localhost` in `docker-compose.yml`.
This causes Keycloak to stamp every JWT it issues with:

```
"iss": "http://localhost:18080/realms/vcs"
```

(port 18080 because that is what `KC_HOSTNAME_PORT` or the request's
`X-Forwarded-Port` resolves to through the Docker port mapping `18080:8080`).

Spring Boot's `oauth2ResourceServer` does **two things** with `KC_ISSUER_URI`:

1. **Fetches the OIDC discovery document** from
   `{KC_ISSUER_URI}/.well-known/openid-configuration` on startup to obtain the
   JWKS endpoint and other metadata.
2. **Validates the `iss` claim** of every incoming JWT against `KC_ISSUER_URI`.

This creates a deadlock:

| `KC_ISSUER_URI` value | Startup discovery | `iss` claim validation |
|---|---|---|
| `http://keycloak:8080/realms/vcs` | ✅ reachable from inside Docker | ❌ tokens say `localhost:18080` |
| `http://localhost:18080/realms/vcs` | ❌ `localhost` inside a container is the container itself | ✅ matches tokens |

### Effect on the browser

The browser is **not directly affected** by either solution below. It always
authenticates through `http://localhost:18080` (the host-mapped port). The
mismatch is purely a **server-to-server** problem between the `vcs-backend`
container and the `keycloak` container.

---

## Solution A — Custom Hostname Alias (no code changes)

Pick a hostname that can be resolved to `127.0.0.1` on the **host machine** and
to the Docker host gateway inside **`vcs-backend`'s** container. This makes a
single URL work from both the browser and the backend.

### Step 1 — Add the hostname to the host's `/etc/hosts`

```
# /etc/hosts  (Linux / macOS)
127.0.0.1  auth.vcs.local
```

On Windows: `C:\Windows\System32\drivers\etc\hosts` (run editor as Administrator).

### Step 2 — Tell Keycloak to use that hostname as its canonical URL

In `docker-compose.yml`, replace `KC_HOSTNAME: localhost` with:

```yaml
keycloak:
  environment:
    # ...existing vars...
    KC_HOSTNAME_URL: "http://auth.vcs.local:18080"  # replaces KC_HOSTNAME
    KC_HTTP_ENABLED: "true"
    KC_HOSTNAME_STRICT: "false"
```

`KC_HOSTNAME_URL` overrides everything: Keycloak now stamps tokens with
`iss=http://auth.vcs.local:18080/realms/vcs`.

### Step 3 — Wire `vcs-backend` in `docker-compose.yml`

```yaml
vcs-backend:
  environment:
    KC_ISSUER_URI: http://auth.vcs.local:18080/realms/vcs   # matches iss claim ✅
    # ...other env vars unchanged...
  extra_hosts:
    # Resolve auth.vcs.local → Docker host gateway IP (e.g. 172.17.0.1)
    # The host has port 18080 mapped to keycloak:8080, so the roundtrip works.
    - "auth.vcs.local:host-gateway"
```

### How the data flows

```
Browser          →  auth.vcs.local:18080  →  /etc/hosts → 127.0.0.1:18080
                                                        → Docker port mapping
                                                        → keycloak:8080  ✅

vcs-backend      →  auth.vcs.local:18080  →  extra_hosts:host-gateway
container             (OIDC discovery +          → host bridge IP:18080
                       iss validation)           → Docker port mapping
                                                 → keycloak:8080  ✅
```

### Trade-offs

| | |
|---|---|
| ✅ No code changes | |
| ✅ Keycloak config is minimal | |
| ⚠️ Every developer must add the `/etc/hosts` entry | |
| ⚠️ Backend→Keycloak traffic makes a roundtrip through the host | |

---

## Solution B — Split JWK-set URI (custom `JwtDecoder` bean)

Decouple the two responsibilities of `KC_ISSUER_URI` into separate properties:
use the **public** issuer URI (`localhost:18080`) for `iss` claim validation, and
a separate **internal** URI (`keycloak:8080`) for fetching the JWKS on startup.

No `/etc/hosts` entry is required. Keycloak is left completely unchanged.

### Step 1 — Add `KC_JWK_SET_URI` to `application.properties`

```properties
# application.properties
# Internal JWKS endpoint used only by the backend to fetch signing keys.
# In docker-compose this is overridden to point at the keycloak service.
# The issuer-uri property is kept for iss-claim validation only (see SecurityConfig).
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=\
  ${KC_JWK_SET_URI:${spring.security.oauth2.resourceserver.jwt.issuer-uri}/protocol/openid-connect/certs}
```

The default falls back to deriving the JWKS URL from the issuer (works for
local dev where `localhost:18080` is reachable). In Docker, `KC_JWK_SET_URI`
overrides it to use the internal `keycloak:8080` address.

### Step 2 — Add a custom `JwtDecoder` bean to `SecurityConfig`

Spring Boot's autoconfiguration creates a `JwtDecoder` from **either**
`issuer-uri` **or** `jwk-set-uri`, not both. To keep issuer validation while
using a different key-fetching URL, declare an explicit bean:

```java
// SecurityConfig.java — add inside the @Configuration class

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
private String issuerUri;

@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
private String jwkSetUri;

/**
 * Custom decoder that fetches signing keys from the internal Keycloak URL
 * (jwk-set-uri) but still validates the "iss" claim against the public
 * issuer URI (issuer-uri). This decouples JWKS discovery from token validation,
 * allowing the backend to run fully containerised without /etc/hosts tricks.
 */
@Bean
JwtDecoder jwtDecoder() {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
    return decoder;
}
```

> **Note:** declaring this `@Bean` suppresses Spring Boot's autoconfigured
> `JwtDecoder`, so the `.jwt(jwt -> ...)` DSL in `filterChain` no longer needs
> `jwt.jwkSetUri(...)` — the bean is picked up automatically.

### Step 3 — Override env vars in `docker-compose.yml`

```yaml
vcs-backend:
  environment:
    # Issuer that matches the "iss" claim Keycloak stamps into tokens
    # (KC_HOSTNAME=localhost → iss=http://localhost:18080/realms/vcs)
    KC_ISSUER_URI: http://localhost:18080/realms/vcs

    # Internal endpoint for fetching the JWKS — reachable via Docker DNS
    KC_JWK_SET_URI: http://keycloak:8080/realms/vcs/protocol/openid-connect/certs
```

No `extra_hosts` needed. Keycloak's `KC_HOSTNAME` stays `localhost`.

### How the data flows

```
Browser          →  localhost:18080  →  Docker port mapping  →  keycloak:8080  ✅
                    (login, token)       KC_HOSTNAME=localhost
                    iss=http://localhost:18080/realms/vcs

vcs-backend      →  keycloak:8080    →  Docker DNS           →  keycloak:8080  ✅
container            (JWKS fetch,
                      startup only)

vcs-backend      validates iss claim: "http://localhost:18080/realms/vcs"
                 == KC_ISSUER_URI:     "http://localhost:18080/realms/vcs"  ✅
```

### Trade-offs

| | |
|---|---|
| ✅ No `/etc/hosts` changes on developer machines | |
| ✅ Keycloak config unchanged | |
| ✅ Clean separation of concerns | |
| ✅ Easiest to replicate on CI / staging | |
| ⚠️ Requires adding one `@Bean JwtDecoder` to `SecurityConfig` | |
| ⚠️ One extra env var (`KC_JWK_SET_URI`) to manage | |

---

## Recommendation

**Solution B** is the recommended approach for shared or CI environments — it
requires a small one-time code change and zero per-machine setup. **Solution A**
is useful if you cannot modify the application code (e.g., testing a pre-built
image) and are willing to manage `/etc/hosts` across the team.

