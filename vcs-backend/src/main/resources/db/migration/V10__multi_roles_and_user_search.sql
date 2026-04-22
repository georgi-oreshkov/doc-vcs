-- ============================================================
-- V10: Multi-roles join table + user search index
-- ============================================================

-- [1] Create org_user_roles join table
CREATE TABLE org_user_roles (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id  UUID NOT NULL REFERENCES organizations(id)   ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES user_profiles(id)   ON DELETE CASCADE,
    role    VARCHAR(50) NOT NULL,
    CONSTRAINT uq_org_user_role UNIQUE (org_id, user_id, role)
);

-- [2] Migrate existing single-role memberships into the new table
INSERT INTO org_user_roles (org_id, user_id, role)
SELECT org_id, user_id, role
FROM   org_memberships
WHERE  role IS NOT NULL;

-- [3] Drop the role column from org_memberships (no longer needed)
ALTER TABLE org_memberships DROP COLUMN role;

-- [4] Indexes to speed up membership lookups
CREATE INDEX IF NOT EXISTS idx_org_user_roles_org_user ON org_user_roles (org_id, user_id);
CREATE INDEX IF NOT EXISTS idx_org_user_roles_org_role  ON org_user_roles (org_id, role);

-- [5] User-search indexes
CREATE INDEX IF NOT EXISTS idx_user_profiles_name  ON user_profiles (name);
CREATE INDEX IF NOT EXISTS idx_user_profiles_email ON user_profiles (email);
