-- V5: Add DEFAULT now() to all NOT NULL timestamp columns (item 17),
--     extend audit fields on notifications (item 15),
--     and add audit columns to org_memberships / categories (item 16).

-- ── Item 17: DEFAULT now() on existing timestamp columns ─────────────────────

ALTER TABLE user_profiles
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at SET DEFAULT now();

ALTER TABLE organizations
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at SET DEFAULT now();

ALTER TABLE documents
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at SET DEFAULT now();

ALTER TABLE versions
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at SET DEFAULT now();

ALTER TABLE comments
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at SET DEFAULT now();

ALTER TABLE fork_requests
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at SET DEFAULT now();

ALTER TABLE notifications
    ALTER COLUMN created_at SET DEFAULT now();

-- ── Item 15: Add updated_at / created_by to notifications ────────────────────
-- Allows NotificationEntity to extend BaseEntity (JPA auditing).

ALTER TABLE notifications
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN created_by UUID;

-- ── Item 16: Audit columns for org_memberships ───────────────────────────────

ALTER TABLE org_memberships
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN created_by UUID;

-- ── Item 16: Audit columns for categories ────────────────────────────────────

ALTER TABLE categories
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN created_by UUID;

