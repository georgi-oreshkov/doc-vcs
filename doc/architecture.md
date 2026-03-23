# VCS Backend — Architecture & Data-Flow Reference

> **Stack:** Spring Boot 4 · Spring Modulith · Spring Security (OAuth2 Resource Server / Keycloak JWT) · Spring Data JPA (PostgreSQL) · Spring Data Redis · Flyway · AWS S3 · OpenAPI Generator (interface-only) · Lombok · SpringDoc

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
│   │   ├── SecurityConfig.java      ← filter chain, JWT decoder bean
│   │   ├── JpaConfig.java           ← auditing (@EnableJpaAuditing)
│   │   └── RedisConfig.java
│   ├── security/
│   │   ├── CurrentUser.java         ← custom @AuthenticationPrincipal annotation
│   │   ├── JwtPrincipal.java        ← wrapper around Jwt with typed accessors
│   │   └── OrgRoleEvaluator.java    ← SpEL helper for @PreAuthorize org-level checks
│   ├── exception/
│   │   ├── AppException.java        ← RuntimeException subclass with HttpStatus
│   │   └── GlobalExceptionHandler.java
│   ├── s3/
│   │   └── S3PresignService.java    ← upload/download pre-signed URL generation
│   └── web/
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
│   ├── api/                         ← package-private internal API for other modules
│   │   └── UserFacade.java          ← only exposes what other modules need (e.g. resolveUser)
│   └── web/
│       └── UserController.java
│
├── organization/                    ← Modulith module
│   ├── domain/
│   │   ├── OrganizationEntity.java
│   │   └── OrgMembershipEntity.java ← (org_id, user_id, role) — replaces OrgUser
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
│   ├── domain/
│   │   ├── DocumentEntity.java
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
│   │   ├── VersionEntity.java
│   │   └── CommentEntity.java
│   ├── persistence/
│   │   ├── VersionRepository.java
│   │   └── CommentRepository.java
│   ├── mapper/
│   │   └── VersionMapper.java
│   ├── service/
│   │   └── VersionService.java
│   └── web/
│       └── VersionsController.java
│
├── request/                         ← Modulith module  (fork + deletion requests)
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
    ├── domain/
    │   ├── NotificationEntity.java  ← persisted notifications (Redis or PG)
    │   └── NotificationEvent.java   ← ApplicationEvent published by other modules
    ├── persistence/
    │   └── NotificationRepository.java
    ├── service/
    │   └── NotificationService.java ← @ApplicationListener, pushes to SSE registry
    ├── sse/
    │   └── SseEmitterRegistry.java  ← ConcurrentHashMap<UUID, SseEmitter>
    └── web/
        └── NotificationController.java  ← GET /notifications/stream
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
OrgMembershipEntity        { id, orgId(FK), userId(FK), role(ENUM) }   ← composite unique(orgId,userId)
CategoryEntity             { id, orgId(FK), name }
DocumentEntity             { id, orgId(FK), authorId, name, status(ENUM), categoryId(FK nullable),
                             latestVersionId(FK nullable), latestApprovedVersionId(FK nullable) }
VersionEntity              { id, docId(FK), versionNumber, status(ENUM), isDraft,
                             s3Key, createdAt, createdBy }
CommentEntity              { id, versionId(FK), authorId, body, createdAt }
ForkRequestEntity          { id, type(FORK|DELETE), requesterId, docId(FK),
                             fromVersionId(FK nullable), status(ENUM), createdAt }
NotificationEntity         { id, recipientId, type, payload(jsonb), readAt(nullable), createdAt }
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
    V2__add_notifications.sql
    V3__...
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

```java
@Configuration
@EnableMethodSecurity          // enables @PreAuthorize on service methods
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/login", "/notifications/stream").permitAll()
                // SSE stream uses its own token-param auth (see §8)
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        // Map Keycloak realm_access.roles → GrantedAuthority("ROLE_XXX")
        // Map resource_access.<client>.roles → GrantedAuthority("ROLE_XXX")
        var converter = new JwtGrantedAuthoritiesConverter();
        // customize as needed
        var result = new JwtAuthenticationConverter();
        result.setJwtGrantedAuthoritiesConverter(converter);
        return result;
    }
}
```

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

Since roles are stored in the DB (not in the JWT), use a Spring bean registered in SpEL:

```java
@Component("orgRoleEvaluator")
@RequiredArgsConstructor
public class OrgRoleEvaluator {

    private final OrgMembershipRepository membershipRepo;

    /** Used as: @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'ADMIN')") */
    public boolean hasRole(UUID orgId, Authentication auth, String... roles) {
        UUID userId = extractUserId(auth);
        return membershipRepo.findByOrgIdAndUserId(orgId, userId)
            .map(m -> Arrays.asList(roles).contains(m.getRole().name()))
            .orElse(false);
    }

    public boolean isDocumentMember(UUID docId, Authentication auth) {
        // load doc → orgId → check membership
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

```
Any Service (e.g. VersionService.approveVersion)
    │
    │  events.publishEvent(new NotificationEvent(recipientId, type, payload))
    ▼
NotificationService  (@ApplicationListener<NotificationEvent>)
    │  1. Persists NotificationEntity (PostgreSQL / Redis)
    │  2. Looks up SseEmitterRegistry for recipientId
    │  3. If connected → emitter.send(SseEmitter.event().data(notificationDto))
    ▼
Client browser  (EventSource API)
```

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

    /** Client connects once; token passed as query param because EventSource can't set headers */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@CurrentUser JwtPrincipal principal) {
        // Send all unread notifications on connect
        notificationService.getUnread(principal.getUserId())
            .forEach(n -> registry.send(principal.getUserId(), n));
        return registry.register(principal.getUserId());
    }

    @GetMapping
    public ResponseEntity<List<NotificationDto>> list(@CurrentUser JwtPrincipal principal) {
        return ResponseEntity.ok(notificationService.getAll(principal.getUserId()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id,
                                         @CurrentUser JwtPrincipal principal) {
        notificationService.markRead(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
```

> **Scaling note:** In a multi-instance deployment, use Redis Pub/Sub as the event bus instead of Spring's in-memory `ApplicationEventPublisher`. `NotificationService` subscribes to a Redis channel; `SseEmitterRegistry` is local per pod.

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
| DB migrations | `src/main/resources/db/migration/` | Flyway `V{n}__*.sql` |
| Mapper (Entity ↔ DTO) | `{module}.mapper.*Mapper` | MapStruct or manual |
| Business logic | `{module}.service.*Service` | `@Service @Transactional` |
| Security authorization | `shared.security.OrgRoleEvaluator` | Used in `@PreAuthorize` |
| Security config | `shared.config.SecurityConfig` | JWT + method security |
| S3 pre-sign | `shared.s3.S3PresignService` | Upload + download URLs |
| SSE emitter registry | `notification.sse.SseEmitterRegistry` | Per-JVM, Redis for scale |
| Domain events | `{module}.domain.*Event` extends `ApplicationEvent` | Fire-and-forget to notification module |
| Cross-module API | `{module}.api.*Facade` | Only public surface of a module |

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

Modulith will fail the build if you violate these boundaries (circular deps, accessing package-private internals of another module).

---

## 12. Recommended Additional Dependencies

Add to `build.gradle.kts`:

```kotlin
// MapStruct for entity ↔ DTO mapping
implementation("org.mapstruct:mapstruct:1.6.3")
annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

// Flyway PostgreSQL support (already in your build)
// implementation("org.flywaydb:flyway-database-postgresql")

// Spring Retry (for S3 / external call resilience)
implementation("org.springframework.retry:spring-retry")
```

---

*Generated: 2026-03-22 · reflects Spring Boot 4 / Spring Security 6 / Spring Modulith 2*

