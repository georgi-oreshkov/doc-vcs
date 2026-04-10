# ── Stage 1: Build native image ─────────────────────────────────────────────
# GraalVM Community 25 ships native-image; matches the Java 25 project toolchain.
FROM ghcr.io/graalvm/native-image-community:25 AS builder

WORKDIR /workspace

# Copy wrapper + build descriptors first so the dependency-download layer is
# only invalidated when build files change, not on every source edit.
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts ./

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q

# Now copy source and compile the native binary.
COPY src/ src/
RUN ./gradlew nativeCompile --no-daemon

# ── Stage 2: Minimal runtime image ──────────────────────────────────────────
# distroless/base provides glibc (required by GraalVM's default dynamic linking)
# with zero shell, no package manager, and a built-in non-root user (uid 65532).
FROM gcr.io/distroless/base-debian12:nonroot

COPY --from=builder \
    /workspace/build/native/nativeCompile/vcs-backend-worker \
    /app/vcs-backend-worker

# All configuration is supplied via environment variables at runtime:
#   REDIS_HOST, REDIS_PORT
#   S3_ENDPOINT, S3_BUCKET, S3_REGION, S3_ACCESS_KEY, S3_SECRET_KEY, S3_PRESIGN_MINUTES
#   DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
#   WORKER_CHANNEL, WORKER_RESULT_CHANNEL, WORKER_LISTENER_ENABLED
ENTRYPOINT ["/app/vcs-backend-worker"]

