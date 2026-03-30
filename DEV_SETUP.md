# Developer Environment Setup

Complete guide to get `doc-vcs` running locally from scratch.

---

## Prerequisites

| Tool | Required version | Notes |
|---|---|---|
| **Java** | 25 | Gradle toolchain downloads it automatically if missing |
| **Docker + Compose** | v2 (`docker compose`) | Used for Postgres, Keycloak, MinIO, Redis |
| **Node.js** | ≥ 20 | Frontend only |
| **mc** (MinIO CLI) | any | Optional; needed for bucket setup via CLI |

---

## Repository Layout

```
doc-vcs/
├── docker-compose.yml     ← all infra services
├── .env.example           ← copy to .env and customise
├── DB/
│   ├── init-postgres.sh   ← runs once on first container start
│   └── create-table-v2.sql
├── doc/
│   └── openapi.json       ← API spec; source of truth for generated code
├── vcs-backend/           ← Spring Boot 4 / Java 25
└── vcs-frontend/          ← React + Vite
```

---

## Step 1 — Environment Variables

```bash
cp .env.example .env
```

Edit `.env` to set your passwords. The defaults work out-of-the-box, but note
that if you change `APP_USER` / `APP_PASSWORD` or `MINIO_ROOT_USER` /
`MINIO_ROOT_PASSWORD` here, **you must set the matching Spring env vars** when
running the backend (see Step 4).

| compose `.env` key | Matching Spring env var | Default |
|---|---|---|
| `APP_USER` | `DB_USER` | `app_user` |
| `APP_PASSWORD` | `DB_PASSWORD` | `secret` |
| `MINIO_ROOT_USER` | `S3_ACCESS_KEY` | `minioadmin` |
| `MINIO_ROOT_PASSWORD` | `S3_SECRET_KEY` | `minioadmin` |

---

## Step 2 — Start Infrastructure

```bash
cd /path/to/doc-vcs
docker compose up -d
```

Wait for all services to be healthy:

```bash
docker compose ps          # all should show "healthy" or "running"
```

Services and their **host** ports:

| Service | Host port | UI |
|---|---|---|
| PostgreSQL | `55432` | — |
| Redis | `16379` | — |
| MinIO API | `19000` | — |
| MinIO Console | `19001` | http://localhost:19001 |
| Keycloak | `18080` | http://localhost:18080 |

> **First start only:** `init-postgres.sh` creates the `vcs_db` database,
> the `app_user` role, the `keycloak` database, and the `vcs_core` schema.
> This script runs only once; deleting the `db-data` volume forces a re-run.

---

## Step 3 — Create the MinIO Bucket

The bucket **`vcs-documents`** is not created automatically.

### Option A — MinIO Console (browser)

1. Open http://localhost:19001
2. Log in: `minioadmin` / `minioadmin` (or your `.env` values)
3. Go to **Buckets → Create Bucket**
4. Name: `vcs-documents` → **Create Bucket**

### Option B — mc CLI

```bash
mc alias set local http://localhost:19000 minioadmin minioadmin
mc mb local/vcs-documents
```

Verify:

```bash
mc ls local/
# should list: [date]  vcs-documents
```

---

## Step 4 — Configure Keycloak

Keycloak starts empty. You must create a realm, a client, and at least one user
before the backend can validate JWTs.

### 4.1 Create the realm

1. Open http://localhost:18080
2. Log in with the bootstrap admin: **admin / admin**
   (or the `KC_BOOTSTRAP_ADMIN_USERNAME` / `KC_BOOTSTRAP_ADMIN_PASSWORD` values
   from your `.env`)
3. Hover the realm dropdown (top-left, shows "Keycloak") → **Create realm**
4. **Realm name:** `vcs` → **Create**

> The backend's `KC_ISSUER_URI` defaults to `http://localhost:18080/realms/vcs`.
> Change the realm name here *and* the env var together if you want a different name.

### 4.2 Create a client

1. Inside the `vcs` realm → **Clients → Create client**
2. **Client ID:** `vcs-frontend`
3. **Client type:** `OpenID Connect` → Next
4. Enable **Standard flow** (Authorization Code) → Next
5. **Valid redirect URIs:** `http://localhost:5173/*`
6. **Web origins:** `http://localhost:5173` → Save

### 4.3 Create a test user

1. **Users → Create new user**
2. Fill **Username** (e.g. `dev`), toggle **Email verified** on → **Create**
3. **Credentials tab → Set password** → enter a password, disable "Temporary" → Save

### 4.4 Verify the issuer endpoint

```bash
curl -s http://localhost:18080/realms/vcs/.well-known/openid-configuration \
  | python3 -m json.tool | grep issuer
# "issuer": "http://localhost:18080/realms/vcs"
```

---

## Step 5 — Run the Backend

```bash
cd vcs-backend

# If your .env credentials differ from the Spring defaults, export them:
# export DB_USER=vcs_server_user DB_PASSWORD=vcs-dev-pass
# export S3_ACCESS_KEY=admin S3_SECRET_KEY=12345678

./gradlew bootRun
```

On first run Gradle will:
1. Download the Java 25 toolchain (if needed)
2. Run `openApiGenerate` → generates API interfaces from `../doc/openapi.json`
3. Compile, then start the Spring Boot app on **port 8080**

Flyway runs automatically and applies all migrations in
`src/main/resources/db/migration/`.

#### Verify

```bash
curl http://localhost:8080/actuator/health 2>/dev/null || \
curl http://localhost:8080/api/v1/documents   # expect 401, not 404/500
```

#### Common startup errors

| Error | Cause | Fix |
|---|---|---|
| `Connection refused 55432` | Postgres container not running | `docker compose up -d db` |
| `401 from Keycloak OIDC discovery` | Realm `vcs` doesn't exist yet | Complete Step 4 |
| `Unable to sign request … InvalidSignatureException` | MinIO credentials mismatch | Export `S3_ACCESS_KEY`/`S3_SECRET_KEY` |
| `FlywayException: Schema validation failed` | Entity changed without a migration | Add a new `Vn__*.sql` in `db/migration/` |

---

## Step 6 — Run the Frontend

```bash
cd vcs-frontend
npm install
npm run dev        # Vite dev server on http://localhost:5173
```

The Vite proxy is expected to forward `/api` requests to `http://localhost:8080`.
Check `vite.config.*` if calls return 404.

---

## Step 7 — Run Backend Tests

Tests use an in-memory H2 database — no running services needed:

```bash
cd vcs-backend
./gradlew test
```

Test reports: `build/reports/tests/test/index.html`

---

## Daily Workflow

```bash
# Start infra (idempotent)
docker compose up -d

# Backend (rebuilds generated sources automatically)
cd vcs-backend && ./gradlew bootRun

# Frontend
cd vcs-frontend && npm run dev
```

To regenerate the API interfaces after editing `../doc/openapi.json`:

```bash
cd vcs-backend && ./gradlew openApiGenerate
```

> **Never edit files under `vcs-backend/build/generated/`** — they are
> overwritten on every build.

---

## Resetting the Environment

```bash
# Wipe all data volumes and recreate from scratch
docker compose down -v
docker compose up -d
# Then redo Steps 3 and 4 (bucket + Keycloak realm)
```

