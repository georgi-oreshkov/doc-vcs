-- =============================================================================
-- VCS DB Schema — v2
-- =============================================================================
-- This script is the evolved version of create-table.sql, reconciled against:
--   • doc/design.md          — business model and role definitions
--   • doc/architecture.md    — entity design and package structure
--   • doc/openapi.json       — API contracts (field names, enums, relationships)
--   • JPA entities in the Spring Boot project
--
-- SUMMARY OF CHANGES FROM create-table.sql:
--   [1]  uuidv7() → gen_random_uuid()
--        uuidv7() requires a custom extension not installed by init-postgres.sh.
--        gen_random_uuid() is built-in since Postgres 13.
--
--   [2]  PostgreSQL ENUM types removed → VARCHAR(50) + CHECK constraints
--        JPA @Enumerated(EnumType.STRING) stores Java enum names as plain strings.
--        Mixing DB enums with JPA VARCHAR causes type-cast errors.
--        CHECK constraints preserve the validation guarantee at the DB level.
--
--   [3]  users → user_profiles
--        Auth is delegated to Keycloak. This table stores local profile data only
--        (name, email, photo). The PK is the Keycloak subject UUID, not generated here.
--        Columns `full_name`, `org_id`, `role`, `password` removed —
--        org membership and roles moved to `org_memberships`.
--
--   [4]  org_memberships table added
--        The old design embedded org_id and role directly on users, limiting each
--        user to a single org. The new design allows a user to be a member of
--        multiple orgs with different roles. Roles expanded: added AUTHOR
--        (was: admin, reader, reviewer → now: ADMIN, AUTHOR, REVIEWER, READER).
--
--   [5]  categories table added
--        New feature from OpenAPI spec: categories scoped per organisation,
--        referenced optionally by documents.
--
--   [6]  documents table restructured
--        • PK renamed: doc_id → id
--        • title → name  (aligns with OpenAPI field name)
--        • Added: org_id, status, category_id, latest_version_id,
--                 latest_approved_version_id, updated_at, created_by
--        • Removed: parent_doc_id, parent_version_num
--          Fork/co-author lineage is now tracked through fork_requests
--          (the requesting user + source version captures all needed context).
--        • Removed: deleted_at
--          The design doc says authors "request delete" — hard delete is executed
--          only after the request is approved. The deleted row simply disappears;
--          there is no need for a soft-delete column.
--
--   [7]  document_reviewers column names changed
--        doc_id  → document_id  (matches JPA @CollectionTable joinColumn)
--        user_id → reviewer_id  (clearer semantics; avoids confusion with user_profiles.id)
--
--   [8]  doc_metadata table REMOVED
--        The metadata (latest_version_id, latest_approved_version_id) was merged
--        directly into the `documents` table. The trigger that auto-maintained
--        doc_metadata counters is replaced by explicit service-layer writes.
--        Fewer tables, fewer round-trips, no sync anomalies.
--
--   [9]  version_metadata → versions
--        • PK changed: composite (doc_id, version_num) → UUID id
--          Single-column UUID PK is simpler for JPA and FK references.
--          The uniqueness constraint UNIQUE(doc_id, version_number) is kept.
--        • s3_location → s3_key  (aligns with VersionEntity.s3Key)
--        • version_status enum split:
--          'draft' status removed; replaced by a separate boolean is_draft column.
--          Remaining statuses: PENDING, APPROVED, REJECTED.
--          This separates the "visibility" state (is_draft) from the "review" state.
--        • reviewer_comment removed → comments table (supports multiple comments,
--          one per reviewer interaction, with timestamps and author tracking).
--        • Added: checksum (file integrity), created_by (audit trail).
--
--  [10]  comments table added
--        Reviewers can add multiple comments per version.
--        Matches OpenAPI Comment schema and CommentEntity.
--
--  [11]  collaboration_requests → fork_requests
--        • Renamed to reflect the feature name used in the API ("fork").
--        • PK renamed: request_id → id
--        • requestor_id → requester_id  (consistent spelling throughout)
--        • parent_doc_id / parent_doc_version (INT) → doc_id / version_id (UUID FK)
--          Referencing the version UUID is cleaner than an integer version number
--          and makes the FK resolvable.
--        • target_author_id removed
--          In the old design, a fork request was directed at a specific author.
--          In the new design, a fork request is against a document; the document's
--          org members (authors/admins) can act on it.
--        • Status value CANCELLED added (matches OpenAPI ForkRequest.status enum).
--        • Fixed typo: updated_ad → updated_at
--        • Fixed broken FK: old script referenced column `parent_version_num` which
--          did not exist in collaboration_requests (column was parent_doc_version).
--
--  [12]  notifications table added
--        Persisted notifications for the SSE / notification module.
--        Matches NotificationEntity.
--
-- TRIGGERS:
--  [T1]  update_timestamp() kept — auto-updates updated_at on any row change.
--        Now applied to all tables that have an updated_at column (previously only
--        doc_metadata, version_metadata, collaboration_requests).
--        Spring JPA @LastModifiedDate also sets updated_at; the trigger acts as a
--        safety net for direct SQL writes.
--
--  [T2]  initialize_doc_metadata() REMOVED
--        The doc_metadata table no longer exists.
--
--  [T3]  update_doc_meta_counters() REMOVED
--        latest_version_id / latest_approved_version_id are now updated by the
--        DocumentService. Application logic is clearer and avoids hidden trigger
--        side effects.
--
--  [T4]  lock_non_pending_versions() → lock_version() (adapted + fixed)
--        The original function was missing RETURN NEW on the success path, which
--        would silently abort every UPDATE on version_metadata even when no
--        exception was raised (BEFORE trigger returning NULL = cancel the DML).
--        Adapted for the new `versions` schema:
--          • s3_location → s3_key
--          • version_status → status / is_draft
--          • version_num → version_number
--        Immutability rules:
--          • version_number — always immutable
--          • s3_key        — immutable once is_draft = FALSE (version submitted)
--          • status        — immutable once APPROVED or REJECTED
-- =============================================================================

SET search_path TO vcs_core;

-- =============================================================================
-- TABLES
-- =============================================================================

-- 1. User Profiles
-- Stores local profile data synced from Keycloak.
-- The PK (id) is the Keycloak subject UUID; it is NOT auto-generated here.
-- [3] Renamed from `users`. Removed: org_id, full_name, role, password.
CREATE TABLE user_profiles (
    id          UUID        PRIMARY KEY,                    -- Keycloak sub, no default
    name        VARCHAR(255) NOT NULL,                      -- [3] was: full_name
    email       VARCHAR(255) NOT NULL UNIQUE,
    photo_url   VARCHAR(1024),                              -- [3] new field (OpenAPI UserProfile)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),         -- [3] new: BaseEntity audit field
    created_by  UUID                                        -- [3] new: BaseEntity audit field
);

-- 2. Organizations
-- [1] gen_random_uuid() replaces uuidv7()
-- [changed] Added updated_at, created_by to match BaseEntity audit fields.
CREATE TABLE organizations (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),  -- [1]
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),                 -- new
    created_by  UUID                                                -- new
);

-- 3. Org Memberships
-- [4] NEW TABLE — replaces org_id + role columns on users.
-- A user can belong to multiple organisations with different roles.
-- Roles: ADMIN (all rights), AUTHOR (create/edit docs), REVIEWER (review versions),
--        READER (read-only, approved versions only).
-- [4] Role AUTHOR added vs. the old 3-role enum (admin, reader, reviewer).
CREATE TABLE org_memberships (
    id      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id  UUID        NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID        NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    role    VARCHAR(50) NOT NULL
            CHECK (role IN ('ADMIN', 'AUTHOR', 'REVIEWER', 'READER')),  -- [2]
    UNIQUE (org_id, user_id)
);

CREATE INDEX idx_org_memberships_org  ON org_memberships (org_id);
CREATE INDEX idx_org_memberships_user ON org_memberships (user_id);

-- 4. Categories
-- [5] NEW TABLE — not present in v1 schema.
-- Optional document grouping scoped to an organization.
CREATE TABLE categories (
    id      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id  UUID        NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name    VARCHAR(255) NOT NULL
);

CREATE INDEX idx_categories_org ON categories (org_id);

-- 5. Documents
-- [6] Major restructure — see file header note [6].
-- status values align with DocumentEntity.DocumentStatus and OpenAPI Document.status.
CREATE TABLE documents (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                      UUID        NOT NULL REFERENCES organizations(id),           -- [6] new
    author_id                   UUID        NOT NULL REFERENCES user_profiles(id),
    name                        VARCHAR(255) NOT NULL,                                       -- [6] was: title
    status                      VARCHAR(50) NOT NULL DEFAULT 'DRAFT'
                                CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')), -- [2][6]
    category_id                 UUID        REFERENCES categories(id) ON DELETE SET NULL,   -- [6] new
    -- Denormalised pointers to the latest and latest-approved version UUIDs.
    -- Maintained by DocumentService, not by trigger [T3 removed].
    latest_version_id           UUID,                                                        -- [6] was: doc_metadata.latest_version (INT)
    latest_approved_version_id  UUID,                                                        -- [6] was: doc_metadata.latest_approved_version (INT)
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),                          -- [6] new
    created_by                  UUID                                                         -- [6] new
    -- [6] removed: parent_doc_id, parent_version_num, deleted_at
);

CREATE INDEX idx_documents_org    ON documents (org_id);
CREATE INDEX idx_documents_author ON documents (author_id);

-- 6. Document Reviewers
-- [7] Column names changed to match JPA @CollectionTable definition on DocumentEntity.
--     doc_id  → document_id  (joinColumn name in @CollectionTable)
--     user_id → reviewer_id  (element column name)
CREATE TABLE document_reviewers (
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,  -- [7] was: doc_id
    reviewer_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,  -- [7] was: user_id
    PRIMARY KEY (document_id, reviewer_id)
);

-- 7. Versions
-- [9] Renamed from version_metadata. See file header note [9].
-- status values align with VersionEntity.VersionStatus and OpenAPI Version.status.
-- is_draft separates visibility from review state (replaces old 'draft' status value).
CREATE TABLE versions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),  -- [9] was: composite PK
    doc_id          UUID        NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    version_number  INTEGER     NOT NULL,                               -- [9] was: version_num
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),  -- [2][9] removed 'draft'
    is_draft        BOOLEAN     NOT NULL DEFAULT TRUE,                  -- [9] new: replaces 'draft' status
    is_uploading    BOOLEAN     NOT NULL DEFAULT FALSE,                 -- true until S3 upload confirmed
    checksum        VARCHAR(128),                                       -- [9] new: file integrity
    s3_key          VARCHAR(1024) NOT NULL,                             -- [9] was: s3_location
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),                 -- kept from old trigger
    created_by      UUID,                                               -- [9] new
    UNIQUE (doc_id, version_number)                                     -- [9] was the composite PK
);

-- Partial indexes mirror the old version_metadata indexes; adapted for new table/column names.
CREATE INDEX idx_versions_doc             ON versions (doc_id);
CREATE INDEX idx_versions_status_approved ON versions (doc_id, status) WHERE status = 'APPROVED';
CREATE INDEX idx_versions_status_pending  ON versions (doc_id, status) WHERE status = 'PENDING';

-- 8. Comments
-- [10] NEW TABLE — not present in v1 schema.
-- Replaces version_metadata.reviewer_comment (single text field) with a proper
-- one-to-many comments table, allowing multiple comments per version with
-- authorship and timestamps.
CREATE TABLE comments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_id  UUID NOT NULL REFERENCES versions(id) ON DELETE CASCADE,
    author_id   UUID NOT NULL REFERENCES user_profiles(id),
    content     TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID
);

CREATE INDEX idx_comments_version ON comments (version_id);

-- 9. Fork Requests
-- [11] Renamed from collaboration_requests. See file header note [11].
-- A fork request represents an author asking to fork a document at a specific
-- version, creating a new independently-owned document. Status CANCELLED added.
CREATE TABLE fork_requests (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),     -- [11] was: request_id
    requester_id  UUID        NOT NULL REFERENCES user_profiles(id),     -- [11] was: requestor_id (fixed spelling)
    doc_id        UUID        NOT NULL REFERENCES documents(id),         -- [11] was: parent_doc_id
    version_id    UUID        NOT NULL REFERENCES versions(id),          -- [11] was: parent_doc_version INT FK
    status        VARCHAR(50) NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),  -- [2][11] added CANCELLED
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),                     -- [11] fixed typo: updated_ad
    created_by    UUID
    -- [11] removed: target_author_id, broken FK constraint
);

CREATE INDEX idx_fork_requests_requester ON fork_requests (requester_id)
    WHERE status = 'PENDING';
CREATE INDEX idx_fork_requests_doc ON fork_requests (doc_id);

-- 10. Notifications
-- [12] NEW TABLE — not present in v1 schema.
-- Persisted notifications pushed via SSE. payload is JSONB for flexible
-- per-notification-type data. No updated_at — the only state change is read_at.
CREATE TABLE notifications (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id  UUID        NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    type          VARCHAR(100) NOT NULL,
    payload       JSONB,
    read_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_recipient_unread ON notifications (recipient_id)
    WHERE read_at IS NULL;

-- =============================================================================
-- TRIGGERS
-- =============================================================================

-- [T1] Generic updated_at maintenance.
-- Kept from original script; column rename (updated_ad → updated_at) fixed.
-- Applied to every table that has an updated_at column.
-- Spring JPA @LastModifiedDate also sets this field; the trigger is a safety net
-- for any direct SQL writes outside the application (migrations, admin scripts).
CREATE OR REPLACE FUNCTION vcs_core.update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    IF (OLD IS DISTINCT FROM NEW) THEN
        NEW.updated_at = now();
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE 'plpgsql';

CREATE TRIGGER trg_user_profiles_updated_at
    BEFORE UPDATE ON vcs_core.user_profiles
    FOR EACH ROW EXECUTE FUNCTION vcs_core.update_timestamp();

CREATE TRIGGER trg_organizations_updated_at
    BEFORE UPDATE ON vcs_core.organizations
    FOR EACH ROW EXECUTE FUNCTION vcs_core.update_timestamp();

CREATE TRIGGER trg_documents_updated_at
    BEFORE UPDATE ON vcs_core.documents
    FOR EACH ROW EXECUTE FUNCTION vcs_core.update_timestamp();

CREATE TRIGGER trg_versions_updated_at
    BEFORE UPDATE ON vcs_core.versions
    FOR EACH ROW EXECUTE FUNCTION vcs_core.update_timestamp();

CREATE TRIGGER trg_comments_updated_at
    BEFORE UPDATE ON vcs_core.comments
    FOR EACH ROW EXECUTE FUNCTION vcs_core.update_timestamp();

CREATE TRIGGER trg_fork_requests_updated_at
    BEFORE UPDATE ON vcs_core.fork_requests
    FOR EACH ROW EXECUTE FUNCTION vcs_core.update_timestamp();

-- [T2] initialize_doc_metadata — REMOVED
-- The doc_metadata table no longer exists. latest_version_id and
-- latest_approved_version_id are columns on `documents` and are maintained
-- by DocumentService.

-- [T3] update_doc_meta_counters — REMOVED
-- Replaced by explicit service-layer writes in DocumentService when a version
-- is created or approved/rejected.

-- [T4] Version immutability guard.
-- Adapted from lock_non_pending_versions() in create-triggers.sql.
-- FIXES from original:
--   • Missing RETURN NEW on the non-exception path (original would silently
--     cancel every UPDATE — a BEFORE trigger returning NULL aborts the DML).
--   • Adapted column names: s3_location → s3_key, version_num → version_number.
--   • Adapted status check for the new two-axis model (is_draft + status):
--       - version_number is always immutable once set.
--       - s3_key is immutable once the version has been submitted (is_draft = FALSE).
--       - status is immutable once the review is finalised (APPROVED or REJECTED).
CREATE OR REPLACE FUNCTION vcs_core.lock_version()
RETURNS TRIGGER AS $$
BEGIN
    -- version_number can never be changed after creation
    IF (OLD.version_number IS DISTINCT FROM NEW.version_number) THEN
        RAISE EXCEPTION 'version_number is immutable once set.';
    END IF;

    -- s3_key is locked the moment a version is submitted for review (is_draft = FALSE)
    IF (OLD.is_draft = FALSE AND OLD.s3_key IS DISTINCT FROM NEW.s3_key) THEN
        RAISE EXCEPTION 's3_key is immutable once a version is submitted (is_draft = FALSE).';
    END IF;

    -- once a reviewer has finalised a decision the status cannot be changed
    IF (OLD.status IN ('APPROVED', 'REJECTED') AND OLD.status IS DISTINCT FROM NEW.status) THEN
        RAISE EXCEPTION 'Cannot change status of an already-% version.', OLD.status;
    END IF;

    RETURN NEW;  -- [T4 fix] was missing; returning NULL would have aborted every UPDATE
END;
$$ LANGUAGE 'plpgsql';

CREATE TRIGGER trg_versions_lock
    BEFORE UPDATE ON vcs_core.versions
    FOR EACH ROW EXECUTE FUNCTION vcs_core.lock_version();

