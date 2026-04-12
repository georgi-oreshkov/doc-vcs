# ── Stage 1: Build ───────────────────────────────────────────────────────────
# Build context MUST be the repo root (doc-vcs/) so that ../doc/openapi.json
# is reachable by the openApiGenerate Gradle task ($rootDir/../doc/openapi.json).
#
# Build manually:
#   docker build -t vcs-backend:latest -f vcs-backend/Dockerfile .
#
# Via docker-compose (see docker-compose.yml — context is set to "."):
#   docker compose build vcs-backend
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

# Copy Gradle wrapper + build descriptors first.
# This layer is only invalidated when build config changes, not on source edits.
COPY vcs-backend/gradlew vcs-backend/gradlew.bat ./
COPY vcs-backend/gradle/ gradle/
COPY vcs-backend/build.gradle.kts vcs-backend/settings.gradle.kts ./

RUN chmod +x gradlew

# openApiGenerate reads inputSpec = "$rootDir/../doc/openapi.json"
# With WORKDIR=/workspace, $rootDir=/workspace, so ../doc = /doc.
COPY doc/openapi.json /doc/openapi.json

# Pre-download all dependencies (cached as long as build files don't change).
RUN ./gradlew dependencies --no-daemon -q

# Copy source and build the executable fat jar; skip tests (run separately in CI).
COPY vcs-backend/src/ src/

RUN ./gradlew bootJar --no-daemon -x test

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
# Slim JRE image — no compiler, no dev tools.
FROM eclipse-temurin:25-jre

# Non-root user for security hardening.
RUN groupadd --system --gid 1001 spring && \
    useradd  --system --uid 1001 --gid spring --no-create-home spring

WORKDIR /app

COPY --from=builder /workspace/build/libs/vcs-backend-0.0.1-SNAPSHOT.jar app.jar

USER spring

EXPOSE 8080

# Runtime configuration is supplied entirely through environment variables.
# Required env vars (all have defaults for local dev — see application.properties):
#
#   DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
#   KC_ISSUER_URI            (must match the "iss" claim in JWTs)
#   KC_CLIENT_ID, KC_REDIRECT_URI
#   REDIS_HOST, REDIS_PORT
#   WORKER_STREAM
#   S3_ENDPOINT, S3_BUCKET, S3_REGION, S3_ACCESS_KEY, S3_SECRET_KEY
#   CORS_ALLOWED_ORIGINS
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
