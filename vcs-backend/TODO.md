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

### 7. SSE: only one emitter per user (multi-tab broken)
**Files:** `notification/sse/SseEmitterRegistry.java`
`ConcurrentHashMap<UUID, SseEmitter>` overwrites the previous emitter when a user
opens a second browser tab. Change to `ConcurrentHashMap<UUID, List<SseEmitter>>`
(or CopyOnWriteArrayList) so all tabs receive events.

### 8. SSE: no heartbeat / keep-alive
**Files:** `notification/sse/SseEmitterRegistry.java`
Long-lived SSE connections are dropped by proxies and load balancers (typically after
30-60s of silence). Add a `@Scheduled` heartbeat that sends a comment event
(`: keepalive\n\n`) every ~15s to all registered emitters.

### 9. listRequests ignores the type query parameter
**Files:** `request/web/RequestsController.java`, `request/service/RequestService.java`
The generated `RequestsApi.listRequests(String type, String status)` passes both
params, but the controller only forwards `status`. The `type` parameter is silently
dropped. Either implement type-based filtering or document that it is unused.

### 10. N+1 query in OrganizationService.listOrganizations
**Files:** `organization/service/OrganizationService.java`
Fetches all memberships for a user, then issues a separate `findById` per org.
Replace with a single `@Query` JOIN or `findAllById(orgIds)`.

### 11. No pagination on notification endpoints
**Files:** `notification/service/NotificationService.java`, `notification/web/NotificationController.java`
`getAll()` and `getUnread()` return unbounded lists. For active users this will
become a performance problem. Add Pageable support.

---

## Medium (code quality / consistency)

### 12. DocumentFacade.resolveDocument() leaks DocumentEntity across modules
**Files:** `document/api/DocumentFacade.java`
Returns the full JPA entity to other modules, violating the Modulith boundary pattern
used by VersionFacade (which returns a VersionSummary record). Expose a lightweight
record / DTO instead.

### 13. request module has no api/ package or facade
**Files:** `request/` (entire module)
Every other feature module (document, version, organization, notification, user) has an
`api/` package with a `@NamedInterface` and a facade. The request module has none.
If another module ever needs to query fork requests, it cannot do so cleanly.

### 14. Deprecated notification.domain.NotificationEvent alias still exists
**Files:** `notification/domain/NotificationEvent.java`
Marked `@Deprecated` — all imports already use `notification.api.NotificationEvent`.
Delete the deprecated class.

### 15. NotificationEntity does not use BaseEntity / JPA auditing
**Files:** `notification/domain/NotificationEntity.java`
Manages `createdAt` manually (`Instant.now()` in service) instead of relying on
`@CreatedDate`. Has no updatedAt or createdBy. Consider extending BaseEntity for
consistency, or at minimum add `@PrePersist` for createdAt.

### 16. OrgMembershipEntity and CategoryEntity have no audit fields
**Files:** `organization/domain/OrgMembershipEntity.java`, `document/domain/CategoryEntity.java`
Neither extends BaseEntity. The DB tables also lack created_at / updated_at for these
(consistent with v2 schema). Intentional for now but consider adding audit columns if
you need to track who added a member or when a category was created.

### 17. V1 migration: timestamp columns lack DEFAULT now()
**Files:** `src/main/resources/db/migration/V1__init_schema.sql`
All created_at / updated_at columns are NOT NULL but have no DEFAULT now().
JPA sets them, but direct SQL inserts (migrations, admin scripts, test fixtures) will
fail. The v2 reference schema has defaults. Add them in a new V4 migration if direct
SQL usage is anticipated.

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

### 19. NotificationController is hand-written (not API-first)
**Files:** `notification/web/NotificationController.java`
Notifications are not in openapi.json, so this controller does not implement a
generated interface. This is acceptable since SSE endpoints are hard to express in
OpenAPI, but document the decision. Consider adding the REST endpoints
(GET /notifications, POST /notifications/{id}/read) to the spec for client
codegen consistency.

### 20. S3PresignService @Recover methods throw raw RuntimeException
**Files:** `shared/s3/S3PresignService.java`
recoverUpload / recoverDownload throw RuntimeException which gets caught by
GlobalExceptionHandler as a generic 500. Throw AppException(SERVICE_UNAVAILABLE)
for a cleaner HTTP 503 response.

### 21. SseEmitterRegistry.send() swallows IOException silently
**Files:** `notification/sse/SseEmitterRegistry.java`
When emitter.send() throws, the emitter is removed but nothing is logged.
Add log.debug so SSE disconnections are observable.

### 22. PageMeta.totalElements is int (may overflow)
**Files:** `shared/web/PageMapper.java`
`page.getTotalElements()` returns long but is cast to int. For tables with
more than 2 billion rows this overflows. Update the OpenAPI PageMeta schema to use
int64 / long, or accept the pragmatic risk for this project's scale.

### 23. Missing @Transactional on OrgMembershipRepository.deleteByOrgIdAndUserId
**Files:** `organization/persistence/OrgMembershipRepository.java`
Spring Data derived delete methods require a wrapping @Transactional or @Modifying.
The calling service method is @Transactional (class-level), so this works today,
but adding @Modifying on the repository method is safer and more explicit.

### 24. listOrganizations has no @PreAuthorize
**Files:** `organization/service/OrganizationService.java`
Returns only the caller's orgs (filtered by userId), so it is safe, but unlike every
other method it lacks even an isAuthenticated() guard. Add for consistency.

