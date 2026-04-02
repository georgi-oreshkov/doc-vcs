# AGENTS.md – vcs-backend-worker

## Project Overview
`vcs-backend-worker` is a **Spring Boot 4 headless async worker** in the `doc-vcs` platform. It has **no HTTP layer** — all I/O is through **Redis** (inbound jobs) and **MinIO/S3** (binary file reads and writes).

- **Runtime**: Java 25, Spring Boot 4.0.5
- **Group / artifact**: `com.root` / `vcs-backend-worker`
- **Main package**: `com.root.vcsbackendworker`
- **Build**: Gradle Kotlin DSL (`build.gradle.kts`)

---

## Core Processing Pipeline

The worker's single responsibility is **diff verification and promotion**:

```
Redis message (diff job)
    │
    ▼
Fetch old version bytes   ←── MinIO  s3Key: documents/{docId}/v{n}
Fetch diff bytes          ←── MinIO  s3Key: documents/{docId}/v{n+1}.diff  (temp)
    │
    ▼
Apply diff → new version bytes
    │
    ▼
SHA-256 hash result
    │
    ├─ hash matches expected checksum in message?
    │       YES → upload to permanent s3Key: documents/{docId}/v{n+1}
    │             → publish success notification to Redis / update DB
    │       NO  → discard / publish failure notification
```

`vcs-backend` generates a presigned PUT URL for the diff (temp object), the client uploads directly, then `vcs-backend` publishes a Redis job carrying: `docId`, `versionId`, `oldS3Key`, `diffS3Key`, `expectedChecksum`.

---

## Platform Architecture (doc-vcs)

```
vcs-frontend  →  vcs-backend (HTTP/REST, JWT via Keycloak)
                     │  publishes job to Redis
                     ▼
              vcs-backend-worker  (this service)
                     │  reads/writes
                     ▼
                 MinIO / S3
```

Sibling services live at `../vcs-backend` and `../vcs-frontend`. Infrastructure is declared in `../docker-compose.yml`.

---

## Key Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-data-redis` | Redis pub/sub or Streams listener |
| `software.amazon.awssdk:s3` | MinIO read (old version + diff) and S3 write (new version) |
| `lombok` | `@Data`, `@Builder`, `@RequiredArgsConstructor` on all DTOs/models |
| `spring-boot-starter-data-redis-test` | Embedded Redis for tests |

> **Add the AWS SDK S3 dependency** — it is not yet in `build.gradle.kts`. Mirror the pattern from `vcs-backend`:
> ```kotlin
> implementation("software.amazon.awssdk:s3:2.x.x")
> ```

---

## Developer Workflows

```bash
./gradlew bootRun    # requires Redis + MinIO running (see ../docker-compose.yml)
./gradlew build      # compile + test
./gradlew test       # tests only; embedded Redis via data-redis-test starter
./gradlew bootJar    # build/libs/vcs-backend-worker-*.jar
```

Start infrastructure: `cd .. && docker compose up minio redis -d`

---

## Architecture Patterns

### Redis Listener
Place listeners in sub-packages of `com.root.vcsbackendworker`. Use `RedisMessageListenerContainer` for pub/sub or `StreamListener` for Redis Streams. Channel/stream names go in `application.properties` (`app.worker.redis.channel`).

### S3 / MinIO Client
Copy the `S3Config` / `S3Properties` pattern from `../vcs-backend/src/main/java/com/root/vcsbackend/shared/config/`:
- `S3Client` bean with `forcePathStyle(true)` — **required for MinIO**.
- Config prefix: `app.s3.*` (endpoint, bucket, region, access-key, secret-key).
- S3 key convention (from `vcs-backend`): `documents/{docId}/v{versionNumber}` for versions; use e.g. `documents/{docId}/v{n}.diff` for temp diff objects.

### Checksum
`VersionEntity.checksum` in `vcs-backend` is documented as SHA-256. Use `MessageDigest.getInstance("SHA-256")` on the assembled new-version bytes.

### Configuration
All config in `src/main/resources/application.properties` (no YAML). Required properties to add:
```properties
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}

app.s3.endpoint=${S3_ENDPOINT:http://localhost:9000}
app.s3.bucket=${S3_BUCKET:vcs-documents}
app.s3.region=${S3_REGION:eu-central-1}
app.s3.access-key=${S3_ACCESS_KEY:minioadmin}
app.s3.secret-key=${S3_SECRET_KEY:minioadmin}

app.worker.redis.channel=${WORKER_CHANNEL:vcs.diff.jobs}
```

### Lombok
Use on all model/DTO classes. Avoid manual getters/setters/constructors. `@RequiredArgsConstructor` on `@Service`/`@Component` classes instead of `@Autowired`.

---

## Key Files

| File | Role |
|---|---|
| `build.gradle.kts` | All dependency/plugin declarations |
| `src/main/resources/application.properties` | Runtime configuration (currently minimal) |
| `src/main/java/com/root/vcsbackendworker/VcsBackendWorkerApplication.java` | Spring Boot entry point |
| `src/test/java/com/root/vcsbackendworker/VcsBackendWorkerApplicationTests.java` | Baseline context-loads test |
| `../vcs-backend/src/main/java/com/root/vcsbackend/shared/config/S3Config.java` | Reference S3Client/Presigner bean pattern |
| `../vcs-backend/src/main/java/com/root/vcsbackend/version/domain/VersionEntity.java` | Canonical domain model (docId, s3Key, checksum, status) |
| `../docker-compose.yml` | Local MinIO (`9000`), Redis, Postgres, Keycloak |

---

## Notes
- Do **not** add `spring-boot-starter-web` — this service has no HTTP layer.
- Java 25 toolchain is enforced; do not downgrade.
- `vcs-backend`'s `RedisConfig` is a stub (TODO comment) — the publisher side is not yet wired; design the Redis message schema here and coordinate with `vcs-backend`.
