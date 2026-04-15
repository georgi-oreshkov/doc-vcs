# AGENTS.md — vcs-backend

Spring Boot 4 / Java 25 document version-control backend. Keycloak JWT auth, PostgreSQL (`vcs_core` schema), MinIO/S3 for file storage, SSE for live notifications.

---

## Build & Run

```bash
./gradlew build                  # openApiGenerate → compileJava → test
./gradlew openApiGenerate        # regenerate API interfaces from ../doc/openapi.json
./gradlew bootRun                # start locally (requires Postgres + Keycloak + MinIO)
./gradlew test                   # run tests only
```

**Critical:** `compileJava` depends on `openApiGenerate`. Never edit files under `build/generated/` — they are overwritten on every build. The OpenAPI spec lives at `../doc/openapi.json` (outside this repo).

---

## Architecture: Spring Modulith Modules

Each top-level package under `com.root.vcsbackend` is a `@ApplicationModule` (declared in `package-info.java`):

| Module | Responsibility |
|---|---|
| `document` | Documents, categories, metadata |
| `version` | Versions, comments, diffs, S3 presign |
| `organization` | Orgs, memberships, roles |
| `request` | Fork requests |
| `notification` | Persist + push notifications via SSE |
| `user` | User profile sync from Keycloak |
| `shared` | Cross-cutting: security, config, S3, Redis messaging, exceptions, mappers |

**Cross-module calls must go through the `api/` Facade** (e.g., `DocumentFacade`, `VersionFacade`, `OrganizationFacade`). Direct use of another module's repositories or services is forbidden by Modulith's package boundary enforcement.

---

## API-First Pattern

All controllers implement a generated interface from `build/generated/src/main/java/com/root/vcsbackend/api/`. Example:

```java
@RestController
public class DocumentsController implements DocumentsApi {   // DocumentsApi is generated
    private final DocumentService documentService;
    // Override methods from the interface
}
```

Generated model classes (e.g., `Document`, `CreateDocumentRequest`) live in `com.root.vcsbackend.model`. Many controller methods currently return `NOT_IMPLEMENTED` — implement them by calling the corresponding `Service`.

---

## Security

- **JWT from Keycloak** — `spring.security.oauth2.resourceserver.jwt.issuer-uri` (env: `KC_ISSUER_URI`).
- Inject the current user with `@CurrentUser JwtPrincipal principal` (resolves `userId`, `email`, `name` from the JWT subject).
- **Method-level auth** uses `@PreAuthorize` with the `@orgRoleEvaluator` bean:
  ```java
  @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'AUTHOR', 'ADMIN')")
  @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
  ```
- Org roles: `ADMIN`, `AUTHOR`, `REVIEWER`, `READER` (stored in `org_memberships`).
- `permitAll` endpoints: `/notifications/stream` (EventSource cannot set headers), `/auth/login` (OAuth2 redirect entry-point), `/actuator/health`.
- **`SecurityHelper`** — inject when you need the current user inside an `@Override` method on a generated API interface (cannot add `@CurrentUser` as an extra parameter on `@Override`):
  ```java
  @Autowired SecurityHelper securityHelper;
  JwtPrincipal principal = securityHelper.currentUser(); // throws 401 AppException if unauthenticated
  ```
- **`AuthController`** (`shared/web/`) — handwritten `GET /auth/login` redirect; builds the Keycloak Authorization Code URL from `KC_ISSUER_URI`, `KC_CLIENT_ID`, `KC_REDIRECT_URI` and returns `302 Found`.

---

## Cross-Module Dependency Inversion

`shared` defines interfaces that feature modules implement, avoiding compile dependencies:

- `shared/security/DocumentOrgLookup` ← implemented by `document/DocumentOrgLookupAdapter`
- `shared/security/OrgRoleLookup` ← implemented by `organization/OrgRoleLookupAdapter`

Follow this pattern when `shared` (or `OrgRoleEvaluator`) needs to query a feature module.

---

## MapStruct Conventions

All mappers use `MapStructConfig` as their `config`:

```java
@Mapper(componentModel = "spring", config = MapStructConfig.class, uses = JsonNullableMapper.class)
public interface DocumentMapper { ... }
```

- **`disableBuilder = true`** in `MapStructConfig`: MapStruct uses setters, not Lombok `@Builder`, because `@Builder` does not inherit `BaseEntity` fields. Service code still uses `.builder()` freely.
- **`JsonNullableMapper`** provides named converters (`uuidToJsonNullable`, `jsonNullableToUuid`, etc.) for OpenAPI `JsonNullable<T>` fields.
- Annotation processor order matters (already set in `build.gradle.kts`): lombok → lombok-mapstruct-binding → mapstruct-processor.

---

## Notifications (Event-Driven + LISTEN/NOTIFY)

**Persistence:** Any service publishes a `NotificationEvent` via `ApplicationEventPublisher` — no import of the notification module needed:

```java
events.publishEvent(new NotificationEvent(this, recipientId, "VERSION_APPROVED", payload));
```

`NotificationEvent` lives in `notification/api/` (a `@NamedInterface`), so any module can import it without violating Modulith boundaries. `NotificationService` (`@EventListener`) persists to DB.

**SSE delivery:** A PostgreSQL `AFTER INSERT` trigger on `notifications` fires `pg_notify('vcs_notification_inserted', {...})` after each transaction commits. `PostgresNotificationListener` maintains a dedicated (non-pooled) `LISTEN` connection on a virtual thread and dispatches to `SseEmitterRegistry.send()`. This is the **single, uniform SSE delivery path** for all notification sources (backend services, worker, direct SQL).

---

## Redis Streams — Worker Communication

The backend communicates with the `vcs-backend-worker` service via a **one-way Redis Stream** for task dispatch. Results flow back through PostgreSQL, not Redis.

1. **Publish** — `DiffTaskPublisher` (in `shared/redis/`) serializes `VerifyTaskMessage` or `ReconstructTaskMessage` to JSON and appends to the **jobs stream** (`vcs.diff.jobs`) via `XADD`. Each message includes `recipientId` (the user to notify) so no correlation cache is needed.
2. **Consumer group** — On startup, `RedisConfig` creates the `workers` consumer group on the stream (idempotent). The worker reads with `XREADGROUP GROUP workers <consumer-name>`, guaranteeing each task is delivered to **exactly one** worker instance — no duplicate processing even with horizontal scaling. Failed/crashed messages are reclaimable via `XAUTOCLAIM`.
3. **Worker processes the task** — the worker performs S3 operations (verify diff / reconstruct document) and writes outcomes **directly to PostgreSQL**:
   - **Verification success** → `UPDATE versions SET checksum = :actual WHERE id = :versionId AND checksum IS NULL` + `INSERT INTO notifications`.
   - **Reconstruction success** → `INSERT INTO notifications` with a `presignedDownloadUrl` in the payload.
   - **Failure** → `INSERT INTO notifications` with failure reason.
4. **SSE delivery** — a PostgreSQL `AFTER INSERT` trigger on `notifications` fires `pg_notify('vcs_notification_inserted', ...)`. `PostgresNotificationListener` (in `notification/sse/`) receives it on a dedicated `LISTEN` connection and calls `SseEmitterRegistry.send()`. Only the backend instance holding the user's SSE connection actually pushes bytes; others no-op.

This architecture means:
- **No Redis results channel** — the worker's contract is two SQL statements, not a message format.
- **No correlation cache** — `recipientId` travels with the task message.
- **Exactly-once task delivery** — Redis Streams consumer groups guarantee each task goes to one worker (unlike Pub/Sub which broadcasts to all).
- **Uniform SSE path** — both worker-originated notifications (inserted by the worker) and backend-originated notifications (e.g., `VERSION_APPROVED`, inserted by `NotificationService`) follow the same `pg_notify → PostgresNotificationListener → SseEmitterRegistry` delivery path.

Message DTOs live in `shared/redis/message/` and mirror the worker's inbound contract:
- `WorkerTaskMessage` (abstract, polymorphic via `taskType`), `VerifyTaskMessage`, `ReconstructTaskMessage`
- `MessageMetadata`, `WorkerTaskType`

Config: `app.redis.diff-jobs-stream` (env: `WORKER_STREAM`).

**Important:** Uses Jackson 3 (`tools.jackson.databind.json.JsonMapper`), not Jackson 2 (`com.fasterxml.jackson.databind.ObjectMapper`). Spring Boot 4 auto-configures `JsonMapper`.

---

## Database

- Schema: `vcs_core` (all tables live here; Flyway also targets this schema).
- Migrations: `src/main/resources/db/migration/` — Flyway versioned scripts (`V1__`, `V2__`, …).
- `ddl-auto=validate` — schema must match entities; always add a Flyway migration for structural changes.
- `BaseEntity` supplies `createdAt`, `updatedAt`, `createdBy` (JPA auditing, populated from JWT subject via `JpaConfig.auditorProvider()`).
- Reference schema (with full change rationale): `../DB/create-table-v2.sql`.

---

## S3 / File Upload Flow

`S3KeyTemplates` (`shared/s3/`) derives all S3 keys deterministically from `(docId, versionNumber)` — no key is stored in the DB:

| Template method | S3 key pattern | Use |
|---|---|---|
| `permanentVersion(docId, n)` | `documents/{docId}/v{n}` | Full snapshot or DIFF stored permanently |
| `stagingDiff(docId, n)` | `tmp/{docId}/v{n}.diff` | Diff awaiting worker verification |
| `tempReconstruction(docId, n)` | `tmp/{docId}/v{n}` | Worker-assembled full doc for download |

**Upload flow:**
1. Client calls `POST /documents` or `POST /versions` → service generates a presigned PUT URL via `S3PresignService.generateUploadUrl(s3Key)`.
2. Controller returns `S3UploadResponse` with the presigned URL; client uploads the file directly.

**Download flow — `StorageType` matters:**
- `SNAPSHOT` — `generateDownloadUrl(permanentVersion(…))` returns a presigned GET URL immediately.
- `DIFF` — the same presigned GET URL is returned (pointing at the raw diff), **and** a `RECONSTRUCT_DOCUMENT` task is published to Redis so the worker assembles the full document. The reconstructed presigned URL arrives via SSE (`DOCUMENT_RECONSTRUCTED` notification).

`S3PresignService` methods (`generateUploadUrl`, `generateDownloadUrl`) are both `@Retryable` (3 attempts, exponential back-off). Config via `S3Properties` (`app.s3.*`).

---

## Environment Variables

| Variable | Default | Purpose |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost/5432/vcs_db` | PostgreSQL |
| `DB_USER` / `DB_PASSWORD` | `app_user/secret` | PostgreSQL credentials |
| `KC_ISSUER_URI` | `http://localhost:18080/realms/vcs` | Keycloak JWT issuer (must match `iss` claim) |
| `KC_JWK_SET_URI` | *(derived from issuer-uri)* | JWKS endpoint — override for Docker where backend fetches keys via `http://keycloak:8080` but issuer stays at public URL |
| `KC_CLIENT_ID` | `vcs-frontend` | Keycloak client ID used by `/auth/login` redirect |
| `KC_REDIRECT_URI` | `http://localhost:5173/callback` | Frontend OAuth2 callback URL |
| `S3_ENDPOINT` | `http://localhost:19000` | MinIO/S3 endpoint |
| `S3_BUCKET` | `vcs-documents` | S3 bucket name |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | `minioadmin/minioadmin` | MinIO credentials |
| `S3_REGION` | `eu-central-1` | Cosmetic for MinIO; must match real AWS region if switched |
| `S3_PRESIGN_MINUTES` | `15` | Presigned URL validity duration |
| `REDIS_HOST` / `REDIS_PORT` | `localhost/16379` | Redis |
| `WORKER_STREAM` | `vcs.diff.jobs` | Redis Stream: backend → worker tasks |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated allowed CORS origins |

---

## Key Files

| Path | What it is |
|---|---|
| `build.gradle.kts` | Deps, openApiGenerate config, Java 25 toolchain |
| `src/main/resources/application.properties` | All runtime config with env-var defaults |
| `src/main/resources/db/migration/V1__init_schema.sql` | Canonical Flyway baseline |
| `src/main/java/.../shared/config/SecurityConfig.java` | Filter chain, JWT converter, permit-all rules |
| `src/main/java/.../shared/security/OrgRoleEvaluator.java` | SpEL security evaluator used in `@PreAuthorize` |
| `src/main/java/.../shared/security/SecurityHelper.java` | Resolves current user from SecurityContext; use in `@Override` controller methods |
| `src/main/java/.../shared/mapper/MapStructConfig.java` | Global MapStruct config (read the Javadoc) |
| `src/main/java/.../shared/redis/DiffTaskPublisher.java` | Publishes verify/reconstruct tasks to worker via Redis (fire-and-forget) |
| `src/main/java/.../shared/redis/message/` | Task message DTOs mirroring the worker's inbound contract |
| `src/main/java/.../shared/s3/S3KeyTemplates.java` | Deterministic S3 key derivation from `(docId, versionNumber)` |
| `src/main/java/.../shared/web/AuthController.java` | Handwritten `GET /auth/login` OAuth2 redirect; not generated from OpenAPI spec |
| `src/main/java/.../notification/api/NotificationEvent.java` | Cross-module notification contract (`@NamedInterface`) |
| `src/main/java/.../notification/sse/PostgresNotificationListener.java` | Dedicated LISTEN connection; dispatches pg_notify events to SseEmitterRegistry |
| `src/test/java/.../TestSecurityConfig.java` | Stub `JwtDecoder` for tests — import with `@Import(TestSecurityConfig.class)` + `@ActiveProfiles("test")` |

