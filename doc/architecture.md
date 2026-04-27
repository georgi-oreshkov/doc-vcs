# VCS Backend — Architecture & Data-Flow Reference

> **Stack (vcs-backend):** Spring Boot 4.0.3 · Java 25 · Spring Modulith 2.0.3 · Spring Security (OAuth2 Resource Server / Keycloak JWT) · Spring Data JPA (PostgreSQL) · Spring Data Redis (Redis Streams) · Flyway · AWS SDK v2 (`software.amazon.awssdk:s3` + `netty-nio-client`) · OpenAPI Generator 7.20.0 (interface-only, `spring` generator) · MapStruct 1.6.3 · Lombok · SpringDoc (`springdoc-openapi-starter-webmvc-ui:2.8.6`) · Spring Retry 2.0.11

> **Stack (vcs-backend-worker):** Spring Boot 4.0.5 · Java 25 · GraalVM Native Image · Spring Data Redis (Redis Streams via `XREADGROUP`) · AWS SDK v2 (`s3` + `url-connection-client`) · Spring JDBC · java-diff-utils 4.12 · Lombok

---

## 1. Guiding Principles

| Principle | Why it matters here |
|---|---|
| **Module-first, not layer-first** | Spring Modulith enforces boundaries at the top-level package. Grouping by feature keeps cohesion high and makes security rules obvious per feature. |
| **Three distinct model layers** | OpenAPI DTOs, JPA Entities, and (where needed) internal command/query objects must never bleed into each other. |
| **Controller is dumb, Service is smart** | Controllers only translate HTTP ↔ Service calls. All business rules, authorization checks, and event publishing live in the Service. |
| **Security is declarative** | A minimal `SecurityConfig` handles transport-level auth. Fine-grained rules use `@PreAuthorize` directly on Service methods so they are enforced regardless of how the service is called. |
| **SSE via ApplicationEvents** | Services publish domain events; the notification module listens and pushes to SSE emitters. No direct coupling. |

---

## 2. Package Structure

```
com.root.vcsbackend/
│
├── VcsBackendApplication.java
│
├── shared/                          ← cross-cutting, no inbound deps from domain modules
│   ├── config/
│   │   ├── SecurityConfig.java      ← two filter chains: webhook (Order 1) + main (Order 2)
│   │   ├── JpaConfig.java           ← auditing (@EnableJpaAuditing)
│   │   ├── RedisConfig.java         ← StringRedisTemplate, consumer-group bootstrap
│   │   ├── JacksonConfig.java
│   │   ├── RetryConfig.java
│   │   ├── S3Config.java / S3Properties.java
│   │   ├── KeycloakProperties.java  ← client-id + redirect-uri config record
│   │   └── RedisProperties.java     ← diff-jobs stream key config
│   ├── domain/
│   │   └── BaseEntity.java          ← @MappedSuperclass (createdAt, updatedAt, createdBy)
│   ├── security/
│   │   ├── CurrentUser.java         ← custom @AuthenticationPrincipal annotation
│   │   ├── JwtPrincipal.java        ← record wrapper around Jwt with typed accessors
│   │   ├── JwtPrincipalResolver.java
│   │   ├── OrgRoleEvaluator.java    ← SpEL helper; delegates to OrgRoleLookup + DocumentOrgLookup
│   │   ├── OrgRoleLookup.java       ← interface; implemented by organization.OrgRoleLookupAdapter
│   │   ├── DocumentOrgLookup.java   ← interface; implemented by document.DocumentOrgLookupAdapter
│   │   ├── SecurityHelper.java      ← resolves current user from SecurityContext (for @Override controllers)
│   │   └── UserProfileSyncFilter.java ← OncePerRequestFilter; ensures user_profiles row exists on every request
│   ├── exception/
│   │   ├── AppException.java        ← RuntimeException subclass with HttpStatus
│   │   └── GlobalExceptionHandler.java
│   ├── redis/
│   │   ├── DiffTaskPublisher.java   ← publishes WorkerTaskMessage to Redis Stream via XADD
│   │   └── message/
│   │       ├── WorkerTaskMessage.java / WorkerTaskType.java (VERIFY_DIFF | RECONSTRUCT_DOCUMENT)
│   │       ├── VerifyTaskMessage.java / ReconstructTaskMessage.java
│   │       └── MessageMetadata.java
│   ├── s3/
│   │   ├── S3PresignService.java    ← upload/download pre-signed URL generation
│   │   └── S3KeyTemplates.java      ← canonical key patterns (permanentVersion, stagingDiff, tempReconstruction)
│   └── web/
│       ├── AuthController.java      ← GET /auth/login → 302 redirect to Keycloak auth page
│       └── PageMapper.java          ← PageMeta builder (Page<?> → PageMeta DTO)
│
├── user/                            ← Modulith module
│   ├── domain/
│   │   └── UserProfileEntity.java   ← JPA entity (mirrors Keycloak user, local profile data)
│   ├── persistence/
│   │   └── UserProfileRepository.java
│   ├── mapper/
│   │   └── UserMapper.java          ← Entity ↔ API model
│   ├── service/
│   │   └── UserService.java
│   ├── api/
│   │   └── UserFacade.java          ← only exposes what other modules need (e.g. resolveUser)
│   └── web/
│       └── UserController.java
│
├── organization/                    ← Modulith module
│   ├── OrgRoleLookupAdapter.java    ← implements shared.security.OrgRoleLookup
│   ├── domain/
│   │   ├── OrganizationEntity.java
│   │   └── OrgMembershipEntity.java ← (org_id, user_id, role); V10 supports multiple roles per user
│   ├── persistence/
│   │   ├── OrganizationRepository.java
│   │   └── OrgMembershipRepository.java
│   ├── mapper/
│   │   └── OrganizationMapper.java
│   ├── service/
│   │   └── OrganizationService.java
│   ├── api/
│   │   └── OrganizationFacade.java  ← resolveRole(orgId, userId) etc.
│   └── web/
│       └── OrganizationsController.java
│
├── document/                        ← Modulith module
│   ├── DocumentOrgLookupAdapter.java ← implements shared.security.DocumentOrgLookup
│   ├── domain/
│   │   ├── DocumentEntity.java       ← includes reviewerIds (@ElementCollection → document_reviewers)
│   │   └── CategoryEntity.java
│   ├── persistence/
│   │   ├── DocumentRepository.java
│   │   └── CategoryRepository.java
│   ├── mapper/
│   │   └── DocumentMapper.java
│   ├── service/
│   │   └── DocumentService.java
│   ├── api/
│   │   └── DocumentFacade.java
│   └── web/
│       ├── DocumentsController.java
│       └── MetadataController.java
│
├── version/                         ← Modulith module
│   ├── domain/
│   │   ├── VersionEntity.java        ← includes storageType, isUploading, checksum, diffPreview
│   │   ├── VersionStatus.java        ← enum: PENDING | APPROVED | REJECTED | DRAFT
│   │   ├── StorageType.java          ← enum: SNAPSHOT | DIFF
│   │   └── CommentEntity.java
│   ├── persistence/
│   │   ├── VersionRepository.java
│   │   └── CommentRepository.java
│   ├── mapper/
│   │   └── VersionMapper.java
│   ├── service/
│   │   └── VersionService.java
│   └── web/
│       ├── VersionsController.java
│       └── MinioWebhookController.java  ← POST /internal/webhook/minio (token-auth, no JWT)
│
├── request/                         ← Modulith module  (fork requests)
│   ├── domain/
│   │   └── ForkRequestEntity.java
│   ├── persistence/
│   │   └── ForkRequestRepository.java
│   ├── mapper/
│   │   └── RequestMapper.java
│   ├── service/
│   │   └── RequestService.java
│   └── web/
│       └── RequestsController.java
│
└── notification/                    ← Modulith module  (SSE)
    ├── api/
    │   └── NotificationEvent.java   ← ApplicationEvent published by other modules (lives in api/ to be importable)
    ├── domain/
    │   ├── NotificationEntity.java  ← persisted to PostgreSQL
    │   └── NotificationDto.java
    ├── persistence/
    │   └── NotificationRepository.java
    ├── service/
    │   └── NotificationService.java ← @EventListener: persists NotificationEvent to DB
    ├── sse/
    │   ├── SseEmitterRegistry.java           ← ConcurrentHashMap<UUID, SseEmitter>
    │   └── PostgresNotificationListener.java ← LISTEN vcs_notification_inserted; dispatches to SSE on pg_notify
    └── web/
        └── NotificationController.java  ← GET /notifications/stream, GET /notifications, POST /{id}/read
```

> **Spring Modulith rule:** only classes in `api/` subpackages are part of the public module API.
> Everything else is package-private or internal. Other modules call `OrganizationFacade`, never `OrganizationService` directly.

---

## 3. The Three Model Layers

```
┌─────────────────────────────────────────────────────────┐
│  HTTP (JSON)                                            │
│  ↕                                                      │
│  OpenAPI-generated DTOs  (com.root.vcsbackend.model.*)  │  ← request/response wire format
│       generated by openapi-generator, NEVER persisted   │
│  ↕  (mapped in Controller or Mapper)                    │
│  JPA Entities  ({module}.domain.*Entity)                │  ← database rows
│       annotated with @Entity, @Table, auditing fields   │
│  ↕  (optionally)                                        │
│  Command / Query objects  ({module}.service.*Command)   │  ← optional internal objects
│       useful for multi-step service operations          │
└─────────────────────────────────────────────────────────┘
```

### 3.1 OpenAPI DTOs (`com.root.vcsbackend.model.*`)
- Generated by `openApiGenerate`, live in `build/generated`.
- Used **only** in Controller method signatures (parameter types and return types).
- Must **never** be passed into a Repository or stored in a DB column.

### 3.2 JPA Entities (`{module}.domain.*Entity`)
- One class per DB table.
- Extend `BaseEntity` (auditing: `createdAt`, `updatedAt`, `createdBy`).
- No JSON annotations. No Lombok `@Data` (use `@Getter @Setter @Builder`).

```java
// shared/domain/BaseEntity.java  (abstract)
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @CreatedBy
    @Column(updatable = false)
    private UUID createdBy;     // populated from SecurityContext by AuditorAware bean
}
```

### 3.3 Mappers (`{module}.mapper.*Mapper`)
- One mapper class per module.
- Use **MapStruct** (add `org.mapstruct:mapstruct` to dependencies) or manual `static` methods.
- Convert Entity → API DTO and API DTO → Entity (or Command).

```java
// example
@Component
public class DocumentMapper {
    public Document toDto(DocumentEntity e) { ... }
    public DocumentEntity toEntity(CreateDocumentRequest req, UUID orgId, UUID authorId) { ... }
}
```

---

## 4. Database Layer

### 4.1 Entity Design (key entities)

```
UserProfileEntity          { id(UUID PK = Keycloak sub), name, email, photoUrl }
OrganizationEntity         { id, name, createdAt, createdBy }
OrgMembershipEntity        { id, orgId(FK), userId(FK), role(ENUM) }
                             ← V10 migration: a user may hold multiple roles in the same org
CategoryEntity             { id, orgId(FK), name }
DocumentEntity             { id, orgId(FK), authorId, name,
                             status(ENUM: DRAFT|PENDING_REVIEW|APPROVED|REJECTED),
                             categoryId(FK nullable),
                             latestVersionId(FK nullable), latestApprovedVersionId(FK nullable),
                             reviewerIds(List<UUID> → document_reviewers join table) }
VersionEntity              { id, docId(FK), versionNumber,
                             status(ENUM: PENDING|APPROVED|REJECTED|DRAFT),
                             isDraft(boolean),
                             isUploading(boolean, default true) ← cleared by MinIO webhook / worker
                             storageType(ENUM: SNAPSHOT|DIFF),
                             checksum(nullable),
                             diffPreview(nullable) ← short unified-diff excerpt for DIFF versions
                             createdAt, createdBy }
                             ← no s3Key column; S3 keys are derived deterministically via S3KeyTemplates
CommentEntity              { id, versionId(FK), authorId, body, createdAt }
ForkRequestEntity          { id, requesterId, docId(FK),
                             versionId(FK, not null) ← specific version the fork is based on,
                             status(ENUM: PENDING|APPROVED|REJECTED|CANCELLED) }
NotificationEntity         { id, recipientId, type(String), payload(jsonb), readAt(nullable),
                             createdAt, updatedAt, createdBy }  ← extends BaseEntity
```

### 4.2 Repositories

```java
// Keep repositories thin — no business logic inside
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {
    Page<DocumentEntity> findByOrgIdAndFilters(...);  // use @Query or Specification
    List<DocumentEntity> findByAuthorId(UUID authorId);
}
```

Use **Spring Data Specifications** (`JpaSpecificationExecutor`) for the filtered list endpoints (documents by org, status, category, author, name).

### 4.3 Flyway Migrations

```
src/main/resources/db/migration/
    V1__init_schema.sql
    V2__add_constraints_indexes_triggers.sql
    V3__add_event_publication_tables.sql
    V4__add_storage_type_to_versions.sql
    V5__add_audit_fields_and_defaults.sql
    V6__add_notification_insert_trigger.sql      ← adds pg_notify trigger on notifications INSERT
    V7__drop_s3_key_from_versions.sql            ← s3Key removed from VersionEntity (derived from S3KeyTemplates)
    V8__add_diff_preview_to_versions.sql
    V9__add_cascade_to_documents_fks.sql
    V10__multi_roles_and_user_search.sql         ← OrgMembership now supports multiple roles per user
    V11__add_draft_to_versions_status.sql
    V12__add_is_uploading_to_versions.sql
```

---

## 5. Service Layer (Business Logic)

Services contain **all** business rules. They are the only place that:
- calls repositories
- publishes `ApplicationEvent`s
- calls `S3PresignService`
- throws `AppException`

```java
@Service
@Transactional
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepo;
    private final OrgMembershipRepository membershipRepo;
    private final S3PresignService s3;
    private final ApplicationEventPublisher events;

    @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'AUTHOR', 'ADMIN')")
    public S3UploadResponse createDocument(UUID orgId, CreateDocumentRequest req, UUID callerId) {
        // 1. validate org exists (or throw 404 AppException)
        // 2. create DocumentEntity + VersionEntity (status=DRAFT)
        // 3. generate S3 pre-signed upload URL
        // 4. publish DocumentCreatedEvent
        // 5. return S3UploadResponse DTO
    }

    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    @Transactional(readOnly = true)
    public Document getDocument(UUID docId) { ... }
}
```

### Service method contract

| Input | Comes from |
|---|---|
| Request DTO fields | Passed from Controller (already validated by `@Valid`) |
| `callerId` | Extracted from JWT in Controller via `@CurrentUser JwtPrincipal principal` |
| Entity lookups | Repository calls inside the service |

| Output | Goes to |
|---|---|
| **Always returns API DTO** | Controller calls mapper before returning ResponseEntity |

---

## 6. Controller Layer

Controllers are **thin translators**. Their only jobs:

1. Accept the HTTP request (OpenAPI-generated interface already handles path/body binding).
2. Extract `@CurrentUser JwtPrincipal` from the security context.
3. Call the Service.
4. Return `ResponseEntity<ApiDto>`.

```java
@RestController
@RequiredArgsConstructor
public class DocumentsController implements DocumentsApi {

    private final DocumentService documentService;

    @Override
    public ResponseEntity<S3UploadResponse> createDocument(
            UUID orgId,
            CreateDocumentRequest body,
            @CurrentUser JwtPrincipal principal) {   // ← injected via custom annotation

        S3UploadResponse response = documentService.createDocument(orgId, body, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

> **Never** put `if (userRole != ADMIN) throw ...` logic in a controller.

---

## 7. Security Design

### 7.1 Filter Chain (`SecurityConfig`)

Two separate filter chains are declared (Spring Security `@Order`):

**Chain 1 — MinIO webhook (`@Order(1)`, matches `/internal/webhook/minio`):**
No JWT processing. `permitAll`. Auth is handled inside `MinioWebhookController` by
comparing an `Authorization` header against `app.minio.webhook-token` (a shared secret).
MinIO cannot present a Keycloak JWT, so the webhook endpoint is excluded from the main chain.

**Chain 2 — Main application (`@Order(2)`):**

```java
@Configuration
@EnableMethodSecurity          // enables @PreAuthorize on service methods
public class SecurityConfig {

    @Bean
    @Order(2)
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/login").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            // Syncs Keycloak user to local user_profiles table on every request
            .addFilterAfter(userProfileSyncFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    // CORS: allowed origins from cors.allowed-origins property (env var CORS_ALLOWED_ORIGINS)
    // Allowed methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
    // Exposed headers: Location
    @Bean
    CorsConfigurationSource corsConfigurationSource() { ... }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        // Map Keycloak realm_access.roles → GrantedAuthority("ROLE_XXX")
        var grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("realm_access.roles");
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        var result = new JwtAuthenticationConverter();
        result.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return result;
    }
}
```

> **JWKS decoupling:** A custom `JwtDecoder` bean fetches signing keys from
> `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (Docker-internal Keycloak DNS) while
> validating the `iss` claim against the public `issuer-uri`. This lets the backend reach Keycloak
> inside Docker without leaving the network.

> **`UserProfileSyncFilter`:** Runs after JWT auth. Calls `UserService.getOrCreateProfile` (idempotent)
> to ensure a `user_profiles` row exists for every Keycloak user before any controller runs.

> **`/notifications/stream` is NOT in `permitAll`** — SSE requires a valid JWT like any other endpoint.

### 7.2 `@CurrentUser` Annotation

```java
// Resolves the JWT from the security context into a typed JwtPrincipal
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal(expression = "@jwtPrincipalResolver.resolve(#this)")
public @interface CurrentUser { }
```

```java
// JwtPrincipal — typed wrapper
public record JwtPrincipal(UUID userId, String email, String name) {
    public static JwtPrincipal from(Jwt jwt) {
        return new JwtPrincipal(
            UUID.fromString(jwt.getSubject()),
            jwt.getClaimAsString("email"),
            jwt.getClaimAsString("name")
        );
    }
}
```

### 7.3 Org-Level Authorization (`OrgRoleEvaluator`)

Roles are stored in the DB (not in the JWT). `OrgRoleEvaluator` lives in `shared` but must not
import `OrganizationRepository` (that would create a circular Modulith dependency). The solution is
two interfaces in `shared/security/`:

- **`OrgRoleLookup`** — `List<String> findRoles(orgId, userId)`. Implemented by `organization.OrgRoleLookupAdapter`. Returns all roles for the user in that org (multi-role since V10).
- **`DocumentOrgLookup`** — `Optional<UUID> findOrgId(docId)`. Implemented by `document.DocumentOrgLookupAdapter`.

```java
@Component("orgRoleEvaluator")
@RequiredArgsConstructor
public class OrgRoleEvaluator {

    private final OrgRoleLookup orgRoleLookup;
    private final DocumentOrgLookup documentOrgLookup;

    /** @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'ADMIN', 'AUTHOR')") */
    public boolean hasRole(UUID orgId, Authentication auth, String... roles) {
        UUID userId = extractUserId(auth);
        if (userId == null) return false;
        List<String> userRoles = orgRoleLookup.findRoles(orgId, userId);
        return userRoles.stream().anyMatch(Arrays.asList(roles)::contains);
    }

    /** @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)") */
    public boolean isDocumentMember(UUID docId, Authentication auth) {
        UUID userId = extractUserId(auth);
        if (userId == null) return false;
        return documentOrgLookup.findOrgId(docId)
            .map(orgId -> !orgRoleLookup.findRoles(orgId, userId).isEmpty())
            .orElse(false);
    }
}
```

### 7.4 Hierarchy of Guards

```
Transport (HTTPS)
    └── JWT signature verified by Spring Security (no code needed)
        └── @authorizeHttpRequests — "is the user authenticated?"
            └── @PreAuthorize on Service — "does this user have the right org role?"
                └── in-service checks — "does this resource belong to this user/org?"
                    (e.g. document.getOrgId().equals(orgId))
```

---

## 8. Server-Sent Events (Notifications)

### 8.1 Flow

Notifications travel through **two hops**: Spring `ApplicationEvent` → PostgreSQL → SSE.
The DB is the reliable store; `pg_notify` / `LISTEN` is the delivery channel.

```
Any Service (e.g. VersionService.approveVersion)
    │
    │  events.publishEvent(new NotificationEvent(recipientId, type, payload))
    ▼
NotificationService  (@EventListener)
    │  1. Persists NotificationEntity to PostgreSQL (payload stored as jsonb)
    │     (the DB V6 migration adds a TRIGGER that fires pg_notify after INSERT)
    ▼
PostgreSQL trigger → pg_notify("vcs_notification_inserted", payload_json)
    ▼
PostgresNotificationListener  (dedicated non-pooled connection, virtual thread)
    │  2. Receives LISTEN channel notification
    │  3. Looks up SseEmitterRegistry for recipientId
    │  4. If connected → emitter.send(...)
    ▼
Client browser  (EventSource API)
```

> **Why pg_notify instead of a direct ApplicationEvent → SSE push?**
> Worker tasks (e.g. `VERIFY_DIFF`) run in a separate process (`vcs-backend-worker`) that writes
> results directly to PostgreSQL. Using pg_notify as the delivery channel means both in-process
> events (Spring ApplicationEvents) and out-of-process events (worker writes) reach the SSE
> layer through a single code path.

> **Dedicated connection:** `LISTEN` state is bound to a single connection. HikariCP returns
> connections to the pool after each use, losing the subscription. `PostgresNotificationListener`
> opens its own `DriverManager` connection, holds it for the application lifetime, and runs the
> poll loop on a virtual thread (Java 25). Reconnects with exponential back-off on failure.

> **Scaling note:** In a multi-instance deployment all backend instances receive the broadcast;
> only the one holding the user's SSE connection actually pushes bytes. Redis Pub/Sub could
> replace pg_notify as the fanout bus if needed.

### 8.2 `SseEmitterRegistry`

```java
@Component
public class SseEmitterRegistry {
    private final ConcurrentHashMap<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(UUID userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(userId, emitter);
        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError(e -> emitters.remove(userId));
        return emitter;
    }

    public void send(UUID userId, Object payload) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try { emitter.send(SseEmitter.event().data(payload)); }
            catch (IOException e) { emitters.remove(userId); }
        }
    }
}
```

### 8.3 `NotificationController`

```java
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final SseEmitterRegistry registry;
    private final NotificationService notificationService;

    /** Requires a valid JWT — standard Bearer token auth (EventSource sends cookies, not headers;
     *  the frontend must pass the token via a custom handshake or use fetch-based SSE). */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@CurrentUser JwtPrincipal principal) {
        // Send all unread notifications on connect
        notificationService.getUnread(principal.getUserId())
            .forEach(n -> registry.send(principal.getUserId(), n));
        return registry.register(principal.getUserId());
    }

    @GetMapping
    public ResponseEntity<List<NotificationDto>> list(@CurrentUser JwtPrincipal principal) { ... }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id, @CurrentUser JwtPrincipal principal) { ... }
}
```

---

## 9. Full Request Data Flow

```
                          ┌───────────────────────────────────────────────────┐
                          │           Spring Security Filter Chain            │
  HTTP POST               │  1. Extract Bearer JWT from Authorization header  │
  /organizations/{id}     │  2. Validate signature against Keycloak JWKS URI  │
  /documents              │  3. Populate SecurityContext (JwtAuthenticationToken)│
    ──────────────────────►  4. Check .anyRequest().authenticated()            │
                          └────────────────────────┬──────────────────────────┘
                                                   │ authenticated
                                                   ▼
                          ┌────────────────────────────────────────────────────┐
                          │              DocumentsController                   │
                          │  - implements DocumentsApi (OpenAPI-generated)     │
                          │  - @Valid already applied by generated interface   │
                          │  - extracts @CurrentUser JwtPrincipal              │
                          │  - calls documentService.createDocument(...)       │
                          └────────────────────────┬───────────────────────────┘
                                                   │
                                                   ▼
                          ┌────────────────────────────────────────────────────┐
                          │              DocumentService                       │
                          │  @PreAuthorize("@orgRoleEvaluator.hasRole(...)")   │◄─ AOP intercept
                          │  - loads OrganizationEntity (or 404)              │
                          │  - creates DocumentEntity + VersionEntity          │
                          │  - calls S3PresignService → upload URL             │
                          │  - publishes DocumentCreatedEvent                  │
                          │  - returns S3UploadResponse DTO (mapped in mapper) │
                          └──────────┬─────────────────────────┬───────────────┘
                                     │                         │
                         ┌───────────▼──────────┐  ┌──────────▼──────────────┐
                         │  DocumentRepository  │  │  ApplicationEventPublisher│
                         │  (Spring Data JPA)   │  │                          │
                         │  .save(entity)       │  │  → NotificationService   │
                         └──────────────────────┘  │    → SseEmitterRegistry  │
                                     │             │    → Client SSE stream   │
                          ┌──────────▼──────────┐  └──────────────────────────┘
                          │     PostgreSQL       │
                          └─────────────────────┘
```

---

## 10. Where Each Thing Lives — Quick Reference

| What | Package | Notes |
|---|---|---|
| OpenAPI request/response DTOs | `com.root.vcsbackend.model.*` | Generated, don't edit |
| OpenAPI controller interfaces | `com.root.vcsbackend.api.*` | Generated, don't edit |
| JPA entities | `{module}.domain.*Entity` | One per DB table |
| DB migrations | `src/main/resources/db/migration/` | Flyway `V{n}__*.sql` (V1–V12) |
| Mapper (Entity ↔ DTO) | `{module}.mapper.*Mapper` | MapStruct or manual |
| Business logic | `{module}.service.*Service` | `@Service @Transactional` |
| Security authorization | `shared.security.OrgRoleEvaluator` | Used in `@PreAuthorize`; delegates to OrgRoleLookup + DocumentOrgLookup interfaces |
| Security config | `shared.config.SecurityConfig` | Two filter chains (webhook Order 1, main Order 2); CORS; UserProfileSyncFilter |
| S3 pre-sign | `shared.s3.S3PresignService` | Upload + download pre-signed URLs |
| S3 key patterns | `shared.s3.S3KeyTemplates` | `documents/{docId}/v{n}`, `tmp/{docId}/v{n}.diff`, `tmp/{docId}/v{n}` |
| Redis task publisher | `shared.redis.DiffTaskPublisher` | XADD to `vcs.diff.jobs` stream |
| SSE emitter registry | `notification.sse.SseEmitterRegistry` | Per-JVM `ConcurrentHashMap` |
| pg_notify → SSE bridge | `notification.sse.PostgresNotificationListener` | Dedicated non-pooled JDBC connection; virtual thread |
| Domain events | `notification.api.NotificationEvent` extends `ApplicationEvent` | In `api/` so all modules can import it |
| Cross-module API | `{module}.api.*Facade` | Only public surface of a module |
| Org-role adapter | `organization.OrgRoleLookupAdapter` | implements `shared.security.OrgRoleLookup` |
| Doc-org adapter | `document.DocumentOrgLookupAdapter` | implements `shared.security.DocumentOrgLookup` |
| MinIO webhook | `version.web.MinioWebhookController` | POST `/internal/webhook/minio`; token auth |
| Auth redirect | `shared.web.AuthController` | GET `/auth/login` → 302 to Keycloak |

---

## 11. Dependency Rules (Spring Modulith)

```
shared  ←  (all modules depend on shared, shared depends on nothing)

user
organization  ←  user.api (resolve user)
document      ←  organization.api (resolve role), user.api
version       ←  document.api, user.api
request       ←  document.api, version.api, organization.api
notification  ←  (listens to ApplicationEvents from all modules, no compile deps needed)

Controllers   ←  their own module's Service only
```

**Adapter pattern for cross-module lookups:** `OrgRoleEvaluator` (in `shared`) needs to call
organization and document repositories, but `shared` cannot depend on domain modules.
The solution: interfaces (`OrgRoleLookup`, `DocumentOrgLookup`) live in `shared/security/`;
implementations (`OrgRoleLookupAdapter`, `DocumentOrgLookupAdapter`) live in their respective
modules and are injected at runtime. This keeps the Modulith boundary clean.

Modulith will fail the build if you violate these boundaries (circular deps, accessing package-private internals of another module).

---

## 12. Dependencies (`build.gradle.kts` — vcs-backend)

All listed dependencies are **already present** in the build file:

```kotlin
// Core
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
implementation("org.springframework.boot:spring-boot-starter-data-redis")
implementation("org.springframework.boot:spring-boot-starter-flyway")
implementation("org.springframework.boot:spring-boot-starter-security")
implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
implementation("org.springframework.boot:spring-boot-starter-validation")
implementation("org.springframework.boot:spring-boot-starter-webmvc")
implementation("org.flywaydb:flyway-database-postgresql")
implementation("org.springframework.modulith:spring-modulith-starter-core")
implementation("org.springframework.modulith:spring-modulith-starter-jpa")

// AWS S3 / MinIO
implementation("software.amazon.awssdk:s3")               // Core S3 client + S3Presigner
implementation("software.amazon.awssdk:netty-nio-client") // Async non-blocking transport

// OpenAPI / Documentation
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")
implementation("org.openapitools:jackson-databind-nullable:0.2.6")

// MapStruct for entity ↔ DTO mapping
implementation("org.mapstruct:mapstruct:1.6.3")
annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

// Spring Retry (for S3 / external call resilience)
implementation("org.springframework.retry:spring-retry:2.0.11")

// AOP (required by Spring Retry / @PreAuthorize)
implementation("org.aspectj:aspectjweaver")

// PostgreSQL LISTEN/NOTIFY (PGConnection, PGNotification — compile-time)
implementation("org.postgresql:postgresql")
```

---

## 13. Worker Service (`vcs-backend-worker`)

`vcs-backend-worker` is a **separate Spring Boot service** compiled to a GraalVM native image.
It is deployed alongside the main backend (see `docker-compose.yml`).

### 13.1 Responsibilities

| Task type | Trigger | What it does |
|---|---|---|
| `VERIFY_DIFF` | MinIO webhook (staging diff uploaded) | Fetches base version, applies diff, verifies SHA-256 checksum, promotes to permanent S3 key. Writes `APPROVED`/`REJECTED` result to DB and notifies user via `notifications` INSERT. |
| `RECONSTRUCT_DOCUMENT` | Download request for a DIFF version | Walks the diff chain from the nearest snapshot, reconstructs full content, writes to `tmp/{docId}/v{n}`, returns a pre-signed download URL. |

### 13.2 Communication

```
vcs-backend  ──XADD──►  Redis Stream (vcs.diff.jobs)  ◄──XREADGROUP──  vcs-backend-worker
```

- **Inbound:** Worker reads from the `vcs.diff.jobs` Redis Stream using `XREADGROUP`.
  After processing (success or failure) it always `XACK`s the message to clear it from the
  pending entries list. Unprocessed messages (crash, OOM) remain pending and are reclaimable
  via `XAUTOCLAIM`.
- **Outbound (results):** Worker writes directly to PostgreSQL (`versions` checksum update,
  `notifications` INSERT). The backend learns about completed tasks via the
  `PostgresNotificationListener` watching `pg_notify("vcs_notification_inserted", ...)`.

### 13.3 S3 Key Conventions

Shared between both services via `S3KeyTemplates` (duplicated in each codebase — same logic):

| Key | Purpose |
|---|---|
| `documents/{docId}/v{n}` | Permanent version (SNAPSHOT or reconstructed full content) |
| `tmp/{docId}/v{n}.diff` | Staging area for diff uploaded by client; awaits worker verification |
| `tmp/{docId}/v{n}` | Temporary reconstructed document for download (RECONSTRUCT tasks) |

### 13.4 Worker Package Structure

```
com.root.vcsbackendworker/
├── VcsBackendWorkerApplication.java
├── verifyTask/application/VerifyDiffUseCase.java
├── reconstructTask/application/ReconstructDocumentUseCase.java
└── shared/
    ├── config/       ← RedisSubscriberConfig, S3Config, JacksonConfig, ...
    ├── db/           ← VersionQueryGateway, NotificationWriteGateway (Spring JDBC)
    ├── diff/         ← DiffApplicator (java-diff-utils)
    ├── messaging/    ← WorkerTaskStreamListener, WorkerTaskDispatcher, message types
    ├── reconstruct/  ← Reconstructor (walks DIFF chain)
    ├── s3/           ← S3DocumentStorage, S3KeyTemplates
    └── verify/       ← ChecksumVerifier (SHA-256)
```

### 13.5 MinIO Webhook → Worker Trigger

MinIO is configured (via `MINIO_NOTIFY_WEBHOOK_ENABLE_stagingdiff` env var) to POST to
`http://vcs-backend:8080/internal/webhook/minio` on `s3:ObjectCreated:*` events.
`MinioWebhookController` parses the event, matches the S3 key against two patterns:

- `tmp/{docId}/v{n}.diff` → calls `VersionService.handleStagingDiffUploaded` → publishes `VERIFY_DIFF` task to Redis Stream
- `documents/{docId}/v{n}` (permanent snapshot) → calls `VersionService.handleSnapshotUploaded` → clears `isUploading` flag

---

*Updated: 2026-04-27 · reflects Spring Boot 4.0.3 / Java 25 / Spring Modulith 2.0.3 / vcs-backend-worker native image*

