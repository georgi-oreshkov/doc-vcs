-- V2: Bring schema in line with create-table-v2.sql reference schema.
--
-- V1 was a minimal baseline. This migration adds:
--   [A] ON DELETE CASCADE / ON DELETE SET NULL on FK constraints that were
--       created without a referential action in V1.
--   [B] CHECK constraints on VARCHAR enum columns (org_memberships.role,
--       documents.status, versions.status, fork_requests.status).
--   [C] Indexes for common query patterns (already present in v2 reference).
--   [D] update_timestamp() trigger — safety net for direct SQL writes.
--   [E] lock_version() trigger — enforces version immutability rules at the DB level.

SET search_path TO vcs_core;

-- =============================================================================
-- [A] ON DELETE CASCADE / ON DELETE SET NULL
--
-- PostgreSQL auto-names unnamed FK constraints as {table}_{column}_fkey.
-- We drop & recreate each one that needs a referential action.
-- =============================================================================

-- org_memberships: cascade when the org or the user is deleted
ALTER TABLE org_memberships
    DROP CONSTRAINT IF EXISTS org_memberships_org_id_fkey,
    ADD CONSTRAINT org_memberships_org_id_fkey
        FOREIGN KEY (org_id) REFERENCES organizations(id) ON DELETE CASCADE;

ALTER TABLE org_memberships
    DROP CONSTRAINT IF EXISTS org_memberships_user_id_fkey,
    ADD CONSTRAINT org_memberships_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES user_profiles(id) ON DELETE CASCADE;

-- categories: cascade when the org is deleted
ALTER TABLE categories
    DROP CONSTRAINT IF EXISTS categories_org_id_fkey,
    ADD CONSTRAINT categories_org_id_fkey
        FOREIGN KEY (org_id) REFERENCES organizations(id) ON DELETE CASCADE;

-- documents.category_id: set NULL when the category is deleted (not cascade)
-- V1 left this as NO ACTION, which would block category deletion.
ALTER TABLE documents
    DROP CONSTRAINT IF EXISTS documents_category_id_fkey,
    ADD CONSTRAINT documents_category_id_fkey
        FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL;

-- document_reviewers.reviewer_id: cascade when the user is deleted
-- (document_id → documents already had CASCADE in V1)
ALTER TABLE document_reviewers
    DROP CONSTRAINT IF EXISTS document_reviewers_reviewer_id_fkey,
    ADD CONSTRAINT document_reviewers_reviewer_id_fkey
        FOREIGN KEY (reviewer_id) REFERENCES user_profiles(id) ON DELETE CASCADE;

-- versions: cascade when the parent document is deleted
ALTER TABLE versions
    DROP CONSTRAINT IF EXISTS versions_doc_id_fkey,
    ADD CONSTRAINT versions_doc_id_fkey
        FOREIGN KEY (doc_id) REFERENCES documents(id) ON DELETE CASCADE;

-- comments: cascade when the parent version is deleted
ALTER TABLE comments
    DROP CONSTRAINT IF EXISTS comments_version_id_fkey,
    ADD CONSTRAINT comments_version_id_fkey
        FOREIGN KEY (version_id) REFERENCES versions(id) ON DELETE CASCADE;

-- notifications: cascade when the recipient user is deleted
ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS notifications_recipient_id_fkey,
    ADD CONSTRAINT notifications_recipient_id_fkey
        FOREIGN KEY (recipient_id) REFERENCES user_profiles(id) ON DELETE CASCADE;

-- =============================================================================
-- [B] CHECK constraints on VARCHAR enum columns
--     Guards against invalid values from direct SQL writes outside the app.
-- =============================================================================

ALTER TABLE org_memberships
    ADD CONSTRAINT chk_org_memberships_role
        CHECK (role IN ('ADMIN', 'AUTHOR', 'REVIEWER', 'READER'));

ALTER TABLE documents
    ADD CONSTRAINT chk_documents_status
        CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED'));

ALTER TABLE versions
    ADD CONSTRAINT chk_versions_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));

ALTER TABLE fork_requests
    ADD CONSTRAINT chk_fork_requests_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'));

-- =============================================================================
-- [C] Indexes
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_org_memberships_org
    ON org_memberships (org_id);

CREATE INDEX IF NOT EXISTS idx_org_memberships_user
    ON org_memberships (user_id);

CREATE INDEX IF NOT EXISTS idx_categories_org
    ON categories (org_id);

CREATE INDEX IF NOT EXISTS idx_documents_org
    ON documents (org_id);

CREATE INDEX IF NOT EXISTS idx_documents_author
    ON documents (author_id);

CREATE INDEX IF NOT EXISTS idx_versions_doc
    ON versions (doc_id);

-- Partial indexes to speed up common list-versions queries
CREATE INDEX IF NOT EXISTS idx_versions_status_approved
    ON versions (doc_id, status) WHERE status = 'APPROVED';

CREATE INDEX IF NOT EXISTS idx_versions_status_pending
    ON versions (doc_id, status) WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_comments_version
    ON comments (version_id);

-- Partial index — most queries only care about pending fork requests
CREATE INDEX IF NOT EXISTS idx_fork_requests_requester
    ON fork_requests (requester_id) WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_fork_requests_doc
    ON fork_requests (doc_id);

-- Partial index — unread notification badge count
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_unread
    ON notifications (recipient_id) WHERE read_at IS NULL;

-- =============================================================================
-- [D] update_timestamp trigger
--     Auto-updates updated_at on every row change.
--     Spring JPA @LastModifiedDate also sets this; the trigger is a safety net
--     for direct SQL writes (migrations, admin scripts).
-- =============================================================================

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

-- =============================================================================
-- [E] lock_version trigger
--     Enforces version immutability rules at the DB level:
--       • version_number  — always immutable once set
--       • s3_key          — immutable once is_draft = FALSE (version submitted)
--       • status          — immutable once APPROVED or REJECTED
--     Adapted from create-table-v2.sql [T4]; fixes the missing RETURN NEW bug
--     from the original create-triggers.sql.
-- =============================================================================

CREATE OR REPLACE FUNCTION vcs_core.lock_version()
RETURNS TRIGGER AS $$
BEGIN
    IF (OLD.version_number IS DISTINCT FROM NEW.version_number) THEN
        RAISE EXCEPTION 'version_number is immutable once set.';
    END IF;

    IF (OLD.is_draft = FALSE AND OLD.s3_key IS DISTINCT FROM NEW.s3_key) THEN
        RAISE EXCEPTION 's3_key is immutable once a version is submitted (is_draft = FALSE).';
    END IF;

    IF (OLD.status IN ('APPROVED', 'REJECTED') AND OLD.status IS DISTINCT FROM NEW.status) THEN
        RAISE EXCEPTION 'Cannot change status of an already-% version.', OLD.status;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE 'plpgsql';

CREATE TRIGGER trg_versions_lock
    BEFORE UPDATE ON vcs_core.versions
    FOR EACH ROW EXECUTE FUNCTION vcs_core.lock_version();

