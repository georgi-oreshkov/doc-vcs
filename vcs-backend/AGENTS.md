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
| `shared` | Cross-cutting: security, config, S3, exceptions, mappers |

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
- SSE `/notifications/stream` is `permitAll` — EventSource API cannot set headers.

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

## Notifications (Event-Driven)

Any service publishes a `NotificationEvent` via `ApplicationEventPublisher` — no import of the notification module needed:

```java
events.publishEvent(new NotificationEvent(this, recipientId, "VERSION_APPROVED", payload));
```

`NotificationService` (`@EventListener`) persists to DB and pushes live via `SseEmitterRegistry`.

---

## Database

- Schema: `vcs_core` (all tables live here; Flyway also targets this schema).
- Migrations: `src/main/resources/db/migration/` — Flyway versioned scripts (`V1__`, `V2__`, …).
- `ddl-auto=validate` — schema must match entities; always add a Flyway migration for structural changes.
- `BaseEntity` supplies `createdAt`, `updatedAt`, `createdBy` (JPA auditing, populated from JWT subject via `JpaConfig.auditorProvider()`).
- Reference schema (with full change rationale): `../DB/create-table-v2.sql`.

---

## S3 / File Upload Flow

1. Client calls `POST /documents` or `POST /versions` → service generates a presigned PUT URL via `S3PresignService.generateUploadUrl(s3Key)`.
2. Controller returns `S3UploadResponse` with the presigned URL; client uploads the file directly.
3. `S3PresignService` methods are `@Retryable` (3 attempts, exponential back-off). Config via `S3Properties` (`app.s3.*`).

---

## Environment Variables

| Variable | Default | Purpose |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost/5432/vcs_db` | PostgreSQL |
| `DB_USER` / `DB_PASSWORD` | `app_user/secret` | PostgreSQL credentials |
| `KC_ISSUER_URI` | `http://localhost:8080/realms/vcs` | Keycloak JWT issuer |
| `S3_ENDPOINT` / `S3_BUCKET` | `http://localhost:9000/vcs-documents` | MinIO/S3 |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | `minioadmin/minioadmin` | MinIO credentials |

---

## Key Files

| Path | What it is |
|---|---|
| `build.gradle.kts` | Deps, openApiGenerate config, Java 25 toolchain |
| `src/main/resources/application.properties` | All runtime config with env-var defaults |
| `src/main/resources/db/migration/V1__init_schema.sql` | Canonical Flyway baseline |
| `src/main/java/.../shared/config/SecurityConfig.java` | Filter chain, JWT converter, permit-all rules |
| `src/main/java/.../shared/security/OrgRoleEvaluator.java` | SpEL security evaluator used in `@PreAuthorize` |
| `src/main/java/.../shared/mapper/MapStructConfig.java` | Global MapStruct config (read the Javadoc) |
| `src/main/java/.../notification/domain/NotificationEvent.java` | Cross-module notification contract |

