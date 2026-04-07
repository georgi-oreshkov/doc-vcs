# TODO.md — vcs-backend

Comprehensive audit of the codebase performed 2026-04-07.
Items are grouped by priority. Each links to the relevant file(s).

---

## Critical (blocks correct runtime behaviour)

### ~~1. No CORS configuration~~ DONE
**Fixed:** `shared/config/SecurityConfig.java`, `application.properties`
Added `CorsConfigurationSource` bean and `.cors()` on the filter chain.
Allowed origins are driven by `cors.allowed-origins` (env: `CORS_ALLOWED_ORIGINS`),
defaulting to `http://localhost:3000` for local development.

### ~~2. Document status never transitions~~ DONE
**Fixed:** `document/api/DocumentFacade.java`, `version/service/VersionService.java`
Added `updateStatusToPendingReview`, `updateStatusToApproved`, `updateStatusToRejected`
to `DocumentFacade` (keeping `DocumentStatus` internal to the document module).
`VersionService` now drives the state machine:
- `createVersion(isDraft=false)` → `PENDING_REVIEW`
- `approveVersion` → `APPROVED`
- `rejectVersion` → `REJECTED`
- `rollbackVersion` → `PENDING_REVIEW` (always submits with `isDraft=false`)

### ~~3. RequestService has zero PreAuthorize~~ DONE
**Fixed:** `request/service/RequestService.java`
- `createForkRequest` — `@PreAuthorize("@orgRoleEvaluator.isDocumentMember(#req.docId, authentication)")`:
  caller must already be a member of the document's org to request a fork.
- `actionRequest` — `@PreAuthorize("isAuthenticated()")`: org-level role check
  (ADMIN / AUTHOR only) is still enforced internally after resolving the request entity.
- `cancelRequest` — `@PreAuthorize("isAuthenticated()")`: ownership check
  (requesterId == callerId) still enforced internally.
- `listRequests` — `@PreAuthorize("isAuthenticated()")`.

### 4. ~~AuthController.authLogin() returns 501 NOT_IMPLEMENTED~~ DONE
**Files:** `shared/web/AuthController.java`
The `/auth/login` endpoint is `permitAll` in SecurityConfig but returns
NOT_IMPLEMENTED. Either implement it (e.g., redirect to Keycloak OIDC login) or
remove it from the OpenAPI spec and SecurityConfig.

---

## High (functional gaps / data issues)

### ~~5. Diff endpoint is a stub~~ DONE
**Fixed:** `version/service/VersionService.java`, `version/domain/VersionEntity.java`,
`version/domain/StorageType.java`, `version/mapper/VersionMapper.java`,
`version/web/VersionsController.java`, `V4__add_storage_type_to_versions.sql`

Full diff / download / reconstruct flow wired up:
- **`storage_type`** column added to `versions` table (Flyway V4) — values: `SNAPSHOT` (full
  content at S3 key) or `DIFF` (delta only, worker must reconstruct before download).
- **`StorageType`** enum + field on `VersionEntity` with `@Builder.Default SNAPSHOT`.
- **`getDiff`** generates presigned download URLs for both versions; if either is `DIFF`-stored,
  triggers async reconstruction via Redis worker. Returns a JSON payload in the `diff` field
  with `fromUrl`, `toUrl`, version numbers, and storage types so the client can fetch both
  documents and render the diff.
- **`getVersionDownloadUrl`** auto-triggers reconstruction for `DIFF`-stored versions; the
  reconstructed document's presigned URL arrives via SSE (`DOCUMENT_RECONSTRUCTED`).
- **`requestReconstruct`** method available for explicit on-demand reconstruction.
- **`rollbackVersion`** now copies `storageType` from the target version.
- Controller passes `callerId` through to service methods for correlation tracking.

### ~~6. RedisConfig is an empty stub~~ DONE
**Fixed:** `shared/config/RedisConfig.java`, `shared/config/RedisProperties.java`,
`shared/redis/DiffTaskPublisher.java`, `shared/redis/DiffResultListener.java`,
`shared/redis/DiffResultEvent.java`, `shared/redis/message/*`,
`version/service/DiffResultHandler.java`, `version/service/VersionService.java`,
`version/persistence/VersionRepository.java`, `application.properties`

Full Redis Pub/Sub integration with the `vcs-backend-worker`:
- **Publish** tasks (`VERIFY_DIFF`, `RECONSTRUCT_DOCUMENT`) to channel `vcs.diff.jobs`
- **Subscribe** to results on channel `vcs.diff.results` via `RedisMessageListenerContainer`
- **Message DTOs** in `shared/redis/message/` mirror the worker's contract exactly
  (polymorphic `WorkerTaskMessage` with Jackson `@JsonTypeInfo`, result messages for
  verification and reconstruction)
- **Correlation cache** in `DiffTaskPublisher` maps `correlationId → userId` so the
  result listener knows which user to notify via SSE
- **`DiffResultEvent`** (Spring `ApplicationEvent`) bridges the `shared` Redis listener
  to the `version` module's `DiffResultHandler` without violating Modulith boundaries
- **`DiffResultHandler`** updates version checksum on successful verification, pushes
  SSE notifications (`DIFF_VERIFIED`, `DIFF_VERIFICATION_FAILED`,
  `DOCUMENT_RECONSTRUCTED`, `DOCUMENT_RECONSTRUCTION_FAILED`) via the existing
  `NotificationEvent` → `NotificationService` → `SseEmitterRegistry` pipeline
- `VersionService.createVersion()` now publishes a `VERIFY_DIFF` task when a non-draft
  version is submitted with a checksum
- New `VersionService.requestReconstruct()` method publishes a `RECONSTRUCT_DOCUMENT` task
- Channel names configurable via `app.redis.diff-jobs-channel` / `app.redis.diff-results-channel`
  (env: `WORKER_CHANNEL` / `WORKER_RESULT_CHANNEL`), matching the worker's defaults

### ~~7. SSE: only one emitter per user (multi-tab broken)~~ DONE
**Fixed:** `notification/sse/SseEmitterRegistry.java`, `notification/web/NotificationController.java`
Changed `ConcurrentHashMap<UUID, SseEmitter>` to `ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>`
so every browser tab gets its own emitter. `send()` fans out to all emitters for a user.
`remove()` completes all emitters. Lifecycle callbacks (`onCompletion`, `onTimeout`,
`onError`) clean up individual emitters and prune empty lists.
Also fixed `NotificationController.stream()`: emitter is now registered *before* flushing
unread notifications so the initial batch actually reaches the client.

### ~~8. SSE: no heartbeat / keep-alive~~ DONE
**Fixed:** `notification/sse/SseEmitterRegistry.java`
Added `@EnableScheduling` on the registry and a `@Scheduled(fixedRate = 15_000)` heartbeat
method that sends an SSE comment event (`: keepalive\n\n`) to every registered emitter.
Comment events are invisible to `EventSource.onmessage` but keep the TCP connection alive
through proxies and load balancers. Failed emitters are removed and logged at DEBUG level.

### ~~9. listRequests ignores the type query parameter~~ DONE
**Fixed:** `request/web/RequestsController.java`, `request/service/RequestService.java`
Controller now passes the `type` parameter through to `RequestService.listRequests()`.
The service accepts `typeFilter` — currently only `"fork"` is valid (the sole request
type). Any unrecognised type returns an empty list. Null/blank `type` returns all.

### ~~10. N+1 query in OrganizationService.listOrganizations~~ DONE
**Fixed:** `organization/service/OrganizationService.java`
Replaced the N+1 pattern (fetch memberships, then `findById` per org) with a
single `findAllById(orgIds)` call. Collects org IDs from memberships first,
short-circuits on empty list.

### ~~11. No pagination on notification endpoints~~ DONE
**Fixed:** `notification/persistence/NotificationRepository.java`,
`notification/service/NotificationService.java`, `notification/web/NotificationController.java`
Added `Page`-returning repository queries (`findByRecipientId…(UUID, Pageable)`).
New service methods `getAllPaged(recipientId, page, size)` and
`getUnreadPaged(recipientId, page, size)`. Controller `GET /notifications` now
accepts `page` (default 0), `size` (default 20), and `unreadOnly` (default false)
query params. Returns a Spring `Page<NotificationDto>` with content, totalElements,
totalPages, etc. Unbounded `getAll()` / `getUnread()` kept for SSE initial flush.

---

## Medium (code quality / consistency)

### ~~12. DocumentFacade.resolveDocument() leaks DocumentEntity across modules~~ DONE
**Fixed:** `document/api/DocumentFacade.java`, `document/api/DocumentSummary.java` (new)
- `resolveDocument(UUID)` changed from `public` to `private` — it is now an internal
  implementation detail that can only be called within the facade class itself.
- New `DocumentSummary` record added to `document/api/` (mirrors `VersionSummary` in
  `version/api/`). Fields: `id`, `orgId`, `authorId`, `name`, `latestVersionId`,
  `latestApprovedVersionId`.
- New public `getDocumentSummary(UUID)` method returns the lightweight record for any
  cross-module caller that needs document metadata beyond the existing scalar helpers
  (`requireExists`, `resolveOrgId`, `getAuthorId`). No `DocumentEntity` is ever
  accessible outside the document module.

### ~~13. request module has no api/ package or facade~~ DONE
**Fixed:** `request/api/package-info.java`, `request/api/ForkRequestSummary.java`,
`request/api/RequestFacade.java` (all new)
- `request/api/` package created with `@NamedInterface` annotation, consistent with
  every other feature module (`document/api/`, `version/api/`, etc.).
- `ForkRequestSummary` record exposes the fields another module legitimately needs
  (`id`, `requesterId`, `docId`, `versionId`, `status`, `createdAt`) without leaking
  `ForkRequestEntity`.
- `RequestFacade` provides three cross-module-safe methods:
  - `getRequest(UUID)` — fetch by ID, throws 404 if absent
  - `listByDocument(UUID)` — all requests for a doc (e.g., guard before deletion)
  - `hasPendingRequest(UUID)` — convenience boolean for guard checks

### ~~14. Deprecated notification.domain.NotificationEvent alias still exists~~ DONE
**Fixed:** `notification/domain/NotificationEvent.java`
Deleted the deprecated class. All code already imported `notification.api.NotificationEvent`.

### ~~15. NotificationEntity does not use BaseEntity / JPA auditing~~ DONE
**Fixed:** `notification/domain/NotificationEntity.java`, `notification/service/NotificationService.java`,
`V5__add_audit_fields_and_defaults.sql`
`NotificationEntity` now extends `BaseEntity` — `createdAt`, `updatedAt`, and `createdBy` are
populated automatically by `AuditingEntityListener`. Removed the manual `.createdAt(Instant.now())`
from `NotificationService`. V5 migration adds `updated_at` and `created_by` columns to `notifications`.

### ~~16. OrgMembershipEntity and CategoryEntity have no audit fields~~ DONE
**Fixed:** `organization/domain/OrgMembershipEntity.java`, `document/domain/CategoryEntity.java`,
`V5__add_audit_fields_and_defaults.sql`
Both entities now extend `BaseEntity`. V5 migration adds `created_at`, `updated_at`, `created_by`
columns to `org_memberships` and `categories`.

### ~~17. V1 migration: timestamp columns lack DEFAULT now()~~ DONE
**Fixed:** `V5__add_audit_fields_and_defaults.sql`
V5 migration adds `DEFAULT now()` to all `created_at` / `updated_at` columns across
`user_profiles`, `organizations`, `documents`, `versions`, `comments`, `fork_requests`,
and `notifications`. Direct SQL inserts no longer require explicit timestamp values.

---

## Low (nice-to-haves / tech debt)

### 18. Zero test coverage beyond contextLoads()
**Files:** `src/test/java/`
Only two test files exist: VcsBackendApplicationTests (smoke) and TestSecurityConfig
(test helper). No unit tests for services, no integration tests for controllers, no
repository tests. Priority areas:
- DocumentService / VersionService — core business logic
- OrgRoleEvaluator — security-critical
- RequestService — authorization edge cases
- Controller integration tests with @WebMvcTest + mock JWT

### ~~19. NotificationController is hand-written (not API-first)~~ DONE
**Fixed:** `notification/web/NotificationController.java`
Added a class-level Javadoc block documenting why this controller deliberately deviates from the
API-first pattern: the SSE streaming endpoint cannot be expressed cleanly in OpenAPI 3 (no standard
SSE response type; `EventSource` API cannot set custom headers, breaking normal JWT auth flow).
The REST endpoints (`GET /notifications`, `POST /notifications/{id}/read`) are noted as candidates
for future spec inclusion to enable client codegen consistency.

### ~~20. S3PresignService @Recover methods throw raw RuntimeException~~ DONE
**Fixed:** `shared/s3/S3PresignService.java`, `shared/exception/AppException.java`
Added an `AppException(HttpStatus, String, Throwable)` constructor for cause-chain support.
`recoverUpload` and `recoverDownload` now throw `AppException(HttpStatus.SERVICE_UNAVAILABLE, …, ex)`
so `GlobalExceptionHandler` returns a clean HTTP 503 response instead of a generic 500.

### ~~21. SseEmitterRegistry.send() swallows IOException silently~~ DONE
**Fixed:** `notification/sse/SseEmitterRegistry.java`
Both `send()` and `heartbeat()` now log at DEBUG with the exception message and call
`emitter.completeWithError(e)` before removing the emitter, ensuring proper Spring
`SseEmitter` lifecycle cleanup and observable SSE disconnections in logs.

### 22. PageMeta.totalElements is int (may overflow)
**Files:** `shared/web/PageMapper.java`
`page.getTotalElements()` returns long but is cast to int. For tables with
more than 2 billion rows this overflows. Update the OpenAPI PageMeta schema to use
int64 / long, or accept the pragmatic risk for this project's scale.

### ~~23. Missing @Transactional on OrgMembershipRepository.deleteByOrgIdAndUserId~~ DONE
**Fixed:** `organization/persistence/OrgMembershipRepository.java`
Added `@Modifying` and `@Transactional` annotations to `deleteByOrgIdAndUserId` so the
derived delete is explicit and safe regardless of whether a surrounding transaction exists.

### ~~24. listOrganizations has no @PreAuthorize~~ DONE
**Fixed:** `organization/service/OrganizationService.java`
Added `@PreAuthorize("isAuthenticated()")` to `listOrganizations` for consistency
with every other service method.

