# syntax=docker/dockerfile:1
# ── Stage 1: Build native image ─────────────────────────────────────────────
# GraalVM Community 25 ships native-image; matches the Java 25 project toolchain.
FROM ghcr.io/graalvm/native-image-community:25 AS builder

WORKDIR /workspace

# Copy wrapper + build descriptors first so the dependency-download layer is
# only invalidated when build files change, not on every source edit.
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY gradle.properties ./
COPY build.gradle.kts settings.gradle.kts ./

# --mount=type=cache persists the Gradle dependency cache on the host across
# Docker builds so dependencies are only downloaded on the very first build.
RUN --mount=type=cache,target=/root/.gradle,id=gradle-worker,sharing=locked \
    chmod +x gradlew && ./gradlew dependencies --no-daemon -q

# Now copy source and compile the native binary.
COPY src/ src/
RUN --mount=type=cache,target=/root/.gradle,id=gradle-worker,sharing=locked \
    ./gradlew nativeCompile --no-daemon

# ── Stage 2: Minimal runtime image ──────────────────────────────────────────
# distroless/cc provides glibc + zlib (libz.so.1) + libgcc/libstdc++ — all
# required by GraalVM native images that link against the system zlib.
# distroless/base omits zlib, causing "cannot open shared object file: libz.so.1".
FROM gcr.io/distroless/cc-debian12:nonroot

COPY --from=builder \
    /workspace/build/native/nativeCompile/vcs-backend-worker \
    /app/vcs-backend-worker

# All configuration is supplied via environment variables at runtime:
#   REDIS_HOST, REDIS_PORT
#   S3_ENDPOINT, S3_BUCKET, S3_REGION, S3_ACCESS_KEY, S3_SECRET_KEY, S3_PRESIGN_MINUTES
#   DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
#   WORKER_CHANNEL, WORKER_RESULT_CHANNEL, WORKER_LISTENER_ENABLED
ENTRYPOINT ["/app/vcs-backend-worker"]

