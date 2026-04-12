# AGENTS.md – vcs-backend-worker

## Project Overview
`vcs-backend-worker` is a **Spring Boot 4 headless async worker** in the `doc-vcs` platform. It has **no HTTP layer** — inbound I/O is through a **Redis Stream** (task consumption), outbound through **MinIO/S3** (binary reads/writes) and **PostgreSQL** (notification inserts, version checksum updates).

- **Runtime**: Java 25, Spring Boot 4.0.5
- **Group / artifact**: `com.root` / `vcs-backend-worker`
- **Main package**: `com.root.vcsbackendworker`
- **Build**: Gradle Kotlin DSL (`build.gradle.kts`)

---

## Core Processing Pipeline

The worker handles two task types consumed from a Redis Stream:

**VERIFY_DIFF** — verifies and promotes an incoming diff:
```
VerifyTaskMessage (Redis Stream)
    │
    ▼
Fetch base version bytes  ←── S3  latestVersionS3Key
Fetch diff bytes          ←── S3  diffS3Key  (e.g. documents/{docId}/v{n+1}.diff)
    │
    ▼
Apply unified diff → new version bytes
    │
    ▼
SHA-256 hash result
    │
    ├─ matches expectedChecksum?
    │       YES → upload to permanent s3Key (strip .diff suffix)
    │             → UPDATE versions SET checksum (DB)
    │             → INSERT INTO notifications type=DIFF_VERIFIED (DB)
    │       NO  → INSERT INTO notifications type=WORKER_TASK_FAILED (DB)
```

**RECONSTRUCT_DOCUMENT** — reassembles a historical version for download:
```
ReconstructTaskMessage (Redis Stream)  ← carries targetVersionNumber
    │
    ▼
Query DB (vcs_core.versions) → find closest SNAPSHOT ≤ targetVersionNumber
Fetch snapshot bytes  ←── S3
Apply each DIFF in order (version_number ASC)  ←── S3
    │
    ▼
SHA-256 hash result → verify expectedChecksum
    │
    ▼
Upload to tmp/{docId}/v{n}  →  generate presigned GET URL
INSERT INTO notifications type=DOCUMENT_RECONSTRUCTED (DB, includes presignedDownloadUrl)
```

**No Redis results channel** — the worker writes outcomes directly to PostgreSQL. The backend learns about completed tasks via the `pg_notify` trigger on the `notifications` table (SSE delivery to users).

---

## Platform Architecture (doc-vcs)

```
vcs-frontend  →  vcs-backend (HTTP/REST, JWT via Keycloak)
                     │  publishes job to Redis Stream (XADD)
                     ▼
              vcs-backend-worker  (this service)
                     │  reads/writes S3            reads/writes PostgreSQL
                     ▼                                    ▼
                 MinIO / S3                     PostgreSQL (vcs_db)
                                                  ├─ vcs_core.versions (read + UPDATE checksum)
                                                  └─ vcs_core.notifications (INSERT)
                                                         │
                                                         ▼ pg_notify trigger
                                                  vcs-backend (PostgresNotificationListener → SSE)
```

Sibling services live at `../vcs-backend` and `../vcs-frontend`. Infrastructure is declared in `../docker-compose.yml`.

---

## Key Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-data-redis` | Redis Stream consumer (`StreamMessageListenerContainer`) |
| `software.amazon.awssdk:s3:2.32.26` | MinIO/S3 reads (versions + diffs) and writes (promoted versions, temp reconstructions) |
| `io.github.java-diff-utils:java-diff-utils:4.12` | Applying unified diffs (`DiffApplicator`) |
| `spring-boot-starter-jdbc` + `org.postgresql:postgresql` | JDBC queries/writes against `vcs_core.versions` and `vcs_core.notifications` |
| `com.fasterxml.jackson.core:jackson-databind` | JSON serialization of Redis messages and notification payloads |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | `Instant` serialization in `MessageMetadata` |
| `lombok` | `@Data`, `@Builder`, `@SuperBuilder`, `@RequiredArgsConstructor` on all DTOs/models |
| `spring-boot-starter-data-redis-test` | Embedded Redis for tests |


---

## Developer Workflows

```bash
./gradlew bootRun    # requires Redis + MinIO + PostgreSQL running (see ../docker-compose.yml)
./gradlew build      # compile + test
./gradlew test       # tests only; embedded Redis via data-redis-test starter
./gradlew bootJar    # build/libs/vcs-backend-worker-*.jar

# Native image (requires GraalVM JDK 25 with native-image installed)
./gradlew nativeCompile          # compile → build/native/nativeCompile/vcs-backend-worker
./gradlew nativeRun              # compile and run immediately
./gradlew bootBuildImage         # OCI image with native binary via Paketo Buildpacks (no local GraalVM needed)

# Docker (multi-stage — no local GraalVM needed)
docker build -t vcs-backend-worker:latest .
docker run --rm \
  -e REDIS_HOST=redis \
  -e S3_ENDPOINT=http://minio:9000 \
  -e DB_HOST=postgres \
  vcs-backend-worker:latest
```

Start infrastructure: `cd .. && docker compose up minio redis postgres -d`

---

## Architecture Patterns

### Redis Stream Consumer
`WorkerTaskStreamListener` implements `StreamListener<String, MapRecord<String, String, String>>` and is registered via `StreamMessageListenerContainer` in `RedisSubscriberConfig`. The backend publishes tasks via `XADD`; this worker reads via `XREADGROUP` with consumer group `workers`.

- On startup, `RedisSubscriberConfig` creates the consumer group (idempotent, handles `BUSYGROUP`).
- Each stream record has a `"payload"` field containing the JSON-serialized `WorkerTaskMessage`.
- After processing (success **or** failure), the listener ACKs the message via `XACK`. Only genuinely unprocessed messages (crash, OOM) remain unacked and are reclaimable via `XAUTOCLAIM`.
- The listener is guarded by `@ConditionalOnProperty(prefix = "app.worker.redis", name = "listener-enabled", havingValue = "true", matchIfMissing = true)` — set `app.worker.redis.listener-enabled=false` in tests to skip registration.
- `WorkerTaskDispatcher` uses a Java `switch` on the concrete subtype to route to the appropriate use case.

### Redis Message Schema
All inbound messages extend `WorkerTaskMessage` (abstract). Jackson resolves the subtype from the `taskType` field (see `@JsonTypeInfo` / `@JsonSubTypes`).

**Inbound** (`shared/messaging/inbound/`):
- `WorkerTaskMessage` — base: `metadata`, `taskType`, `docId`, `versionId`, `expectedChecksum`, `recipientId`
- `VerifyTaskMessage` — adds: `latestVersionS3Key`, `diffS3Key`
- `ReconstructTaskMessage` — adds: `targetVersionNumber`

`MessageMetadata` carries `correlationId` (UUID), `emittedAt` (Instant), `producer` (string), `schemaVersion` (default 1).

`FailureReason` enum (`shared/messaging/`): `CHECKSUM_MISMATCH` | `DIFF_APPLY_FAILED` | `SOURCE_NOT_FOUND` | `INVALID_MESSAGE` | `STORAGE_ERROR` | `INTERNAL_ERROR` — used in notification payloads.

**No outbound messages** — the worker writes results directly to PostgreSQL.

### Task Outcome Recording (PostgreSQL)
`NotificationWriteGateway` (`shared/db/`) writes task outcomes directly to the `vcs_core.notifications` table using JDBC. The PostgreSQL `AFTER INSERT` trigger on `notifications` fires `pg_notify('vcs_notification_inserted', ...)`, which the backend's `PostgresNotificationListener` picks up and pushes via SSE. This is the uniform delivery path for all notification sources.

Three outcome methods (each `@Transactional`):
- `recordVerificationSuccess(recipientId, docId, versionId, checksum)` — atomically UPDATEs `versions.checksum` and INSERTs notification with type `DIFF_VERIFIED`
- `recordReconstructionSuccess(recipientId, docId, versionId, presignedDownloadUrl)` — INSERTs notification with type `DOCUMENT_RECONSTRUCTED`
- `recordFailure(recipientId, docId, versionId, taskType, failureReason)` — INSERTs notification with type `WORKER_TASK_FAILED`

Notification payloads are JSONB containing `docId`, `versionId`, and task-specific fields.

### S3 / MinIO Client
`S3Config` (in `shared/config/`) defines `S3Client` and `S3Presigner` beans, both with `forcePathStyle(true)` — **required for MinIO**. All S3 operations go through `S3DocumentStorage` (`shared/s3/`):
- `fetchBytes(s3Key)` — downloads an object as `byte[]`
- `uploadBytes(s3Key, bytes)` — puts an object (overwrites)
- `generatePresignedDownloadUrl(s3Key)` — GET presigned URL, duration from `app.s3.presign-duration-minutes`

Config prefix: `app.s3.*` (endpoint, bucket, region, access-key, secret-key, presign-duration-minutes).
S3 key convention: `documents/{docId}/v{versionNumber}` for permanent versions; `documents/{docId}/v{n+1}.diff` for temp diff objects; `tmp/{docId}/v{n}` for reconstructed temporary downloads.

### Database
`VersionQueryGateway` (`shared/db/`) is a JDBC repository using `JdbcClient`. It queries `vcs_core.versions` (owned by `vcs-backend`'s Flyway migrations).

Key methods:
- `findById(versionId)` — fetch single version row
- `findLastSnapshotBefore(docId, targetVersionNumber)` — closest `SNAPSHOT` ≤ target
- `findDiffVersionsBetween(docId, afterVersionNumber, upToVersionNumber)` — ordered `DIFF` rows to apply

`NotificationWriteGateway` (`shared/db/`) writes to `vcs_core.notifications` and updates `vcs_core.versions.checksum`. Schema migrations are owned by `vcs-backend` (Flyway). The worker never creates tables or alters schema.

`VersionRow` (Lombok `@Data @Builder`) is the JDBC projection: `id`, `docId`, `versionNumber`, `status`, `storageType`, `checksum`, `s3Key`. Not a JPA entity.

### Diff Application
`DiffApplicator` (`shared/diff/`) applies unified-diff patches using `java-diff-utils`. Both base and diff are UTF-8 text. Throws `DiffApplicationException` on patch failure.

### Checksum
`ChecksumVerifier` (`verify/domain/`) computes SHA-256 hex via `MessageDigest`. Use `sha256Hex(bytes)` to compute; `matches(bytes, expectedChecksum)` for comparison (case-insensitive).

### Configuration
All config in `src/main/resources/application.properties` (no YAML). Current properties:
```properties
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}

app.s3.endpoint=${S3_ENDPOINT:http://localhost:9000}
app.s3.bucket=${S3_BUCKET:vcs-documents}
app.s3.region=${S3_REGION:eu-central-1}
app.s3.access-key=${S3_ACCESS_KEY:minioadmin}
app.s3.secret-key=${S3_SECRET_KEY:minioadmin}
app.s3.presign-duration-minutes=${S3_PRESIGN_MINUTES:15}

spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:55432}/${DB_NAME:vcs_db}?currentSchema=vcs_core
spring.datasource.username=${DB_USER:app_user}
spring.datasource.password=${DB_PASSWORD:secret}
spring.datasource.driver-class-name=org.postgresql.Driver

app.worker.redis.stream=${WORKER_STREAM:vcs.diff.jobs}
app.worker.redis.consumer-group=${WORKER_CONSUMER_GROUP:workers}
app.worker.redis.consumer-name=${WORKER_CONSUMER_NAME:${random.uuid}}
app.worker.redis.listener-enabled=${WORKER_LISTENER_ENABLED:true}
```

### GraalVM Native Image
The `org.graalvm.buildtools.native` plugin (`0.10.4`) is applied. Spring Boot's AOT processor runs automatically before native compilation and handles most reflection/proxy registration. Three project-specific concerns require manual attention:

1. **Jackson polymorphic types** — `@JsonSubTypes` on `WorkerTaskMessage` is detected by Spring AOT, but nested types (`MessageMetadata`) are not. All inbound message DTOs and enums are registered in `WorkerRuntimeHints`.
2. **JDBC `DataClassRowMapper`** — `JdbcClient.query(VersionRow.class)` is a dynamic call site invisible to AOT static analysis. `VersionRow` is registered explicitly.
3. **AWS SDK HTTP client** — `software.amazon.awssdk:url-connection-client` is declared explicitly to pin the native-image-friendly sync HTTP transport (Java's built-in `HttpURLConnection`) and avoid Netty being selected, which pulls in additional reflection requirements.

`WorkerRuntimeHints` is wired via `@ImportRuntimeHints(WorkerRuntimeHints.class)` on `VcsBackendWorkerApplication`. The `graalvmNative` block in `build.gradle.kts` sets `--no-fallback` (prevents silent JVM fallback) and initialises SLF4J/Logback at build time.

The AWS SDK artifacts are version-managed through `platform("software.amazon.awssdk:bom:2.32.26")` — add new AWS SDK modules without explicit versions.

### Docker
The `Dockerfile` uses a two-stage build:

| Stage | Base image | Purpose |
|---|---|---|
| `builder` | `ghcr.io/graalvm/native-image-community:25` | Runs `./gradlew nativeCompile`; no local GraalVM needed |
| runtime | `gcr.io/distroless/base-debian12:nonroot` | Ships only the binary + glibc; ~40–60 MB total |

`distroless/base` is used instead of `scratch` because GraalVM's default native-image output is **dynamically linked** against glibc. The `:nonroot` tag runs as uid 65532 with no shell.

All runtime configuration is passed via environment variables (see `application.properties` for the full mapping). No ports are exposed — the container has no HTTP layer.

`.dockerignore` excludes `.gradle/`, `build/`, and IDE files so the build context is small and Gradle dependency layers cache correctly between builds.

### Lombok
Use on all model/DTO classes. Avoid manual getters/setters/constructors. `@RequiredArgsConstructor` on `@Service`/`@Component` classes instead of `@Autowired`.

---

## Key Files

| File | Role |
|---|---|
| `build.gradle.kts` | All dependency/plugin declarations |
| `Dockerfile` | Two-stage build: GraalVM builder → distroless runtime |
| `.dockerignore` | Excludes build artefacts and IDE files from Docker build context |
| `src/main/resources/application.properties` | Runtime configuration (fully populated) |
| `src/main/java/com/root/vcsbackendworker/VcsBackendWorkerApplication.java` | Spring Boot entry point |
| `src/test/java/com/root/vcsbackendworker/VcsBackendWorkerApplicationTests.java` | Baseline context-loads test (sets `listener-enabled=false`) |
| `shared/config/RedisSubscriberConfig.java` | Registers `StreamMessageListenerContainer` + consumer group |
| `shared/config/WorkerRedisProperties.java` | `@ConfigurationProperties("app.worker.redis")` record |
| `shared/config/S3Config.java` | `S3Client` + `S3Presigner` beans (forcePathStyle for MinIO) |
| `shared/config/S3Properties.java` | `@ConfigurationProperties("app.s3")` record |
| `shared/config/JacksonConfig.java` | `ObjectMapper` bean with `JavaTimeModule` |
| `shared/config/WorkerRuntimeHints.java` | GraalVM native-image reflection hints (message DTOs + `VersionRow`) |
| `shared/messaging/WorkerTaskStreamListener.java` | Redis Stream `StreamListener` — deserializes, dispatches, and ACKs |
| `shared/messaging/WorkerTaskDispatcher.java` | Routes `WorkerTaskMessage` subtypes to use cases via `switch` |
| `shared/messaging/FailureReason.java` | Enum of failure reasons for notification payloads |
| `shared/messaging/MessageMetadata.java` | Inbound message metadata (correlationId, emittedAt, producer) |
| `shared/messaging/inbound/WorkerTaskMessage.java` | Polymorphic base with `@JsonTypeInfo`/`@JsonSubTypes` |
| `shared/messaging/inbound/VerifyTaskMessage.java` | Adds `latestVersionS3Key`, `diffS3Key` |
| `shared/messaging/inbound/ReconstructTaskMessage.java` | Adds `targetVersionNumber` |
| `shared/db/NotificationWriteGateway.java` | Writes task outcomes to `vcs_core.notifications` + updates `versions.checksum` |
| `shared/db/VersionQueryGateway.java` | JDBC queries against `vcs_core.versions` |
| `shared/db/VersionRow.java` | JDBC projection (not a JPA entity) |
| `shared/s3/S3DocumentStorage.java` | `fetchBytes`, `uploadBytes`, `generatePresignedDownloadUrl` |
| `shared/diff/DiffApplicator.java` | Applies unified diffs via `java-diff-utils` |
| `verify/domain/ChecksumVerifier.java` | SHA-256 hex computation and comparison |
| `verify/application/VerifyDiffUseCase.java` | Orchestrates the VERIFY_DIFF pipeline |
| `reconstruct/application/ReconstructDocumentUseCase.java` | Orchestrates the RECONSTRUCT_DOCUMENT pipeline |
| `../vcs-backend/src/main/java/com/root/vcsbackend/version/domain/VersionEntity.java` | Canonical domain model (docId, s3Key, checksum, status) |
| `../docker-compose.yml` | Local MinIO (`9000`), Redis, Postgres, Keycloak |

---

## Notes
- Do **not** add `spring-boot-starter-web` — this service has no HTTP layer.
- Java 25 toolchain is enforced; do not downgrade.
- The worker **reads** from `vcs_core.versions` and **writes** to `vcs_core.notifications` + updates `versions.checksum`. All schema migrations are owned by `vcs-backend` (Flyway). Never create tables or alter schema from this service.
- Redis transport is **Streams** (XREADGROUP), not Pub/Sub. The backend publishes via XADD; do not use `RedisMessageListenerContainer` or `ChannelTopic`.
- All tasks are ACKed after processing (success or failure). Unacked messages indicate crashes and are reclaimable via XAUTOCLAIM.
