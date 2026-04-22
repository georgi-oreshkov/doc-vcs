-- Migration: support multiple roles per user per organization
-- Replaces the single-role 'role' column in org_memberships with a
-- separate org_user_roles junction table.

BEGIN;

-- 1. Create the new roles table
CREATE TABLE org_user_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL CHECK (role IN ('ADMIN', 'AUTHOR', 'REVIEWER', 'READER')),
    UNIQUE (org_id, user_id, role)
);

CREATE INDEX idx_org_user_roles_org_user ON org_user_roles(org_id, user_id);

-- 2. Migrate existing single roles to the new table
INSERT INTO org_user_roles (id, org_id, user_id, role)
SELECT gen_random_uuid(), org_id, user_id, role
FROM org_memberships;

-- 3. Drop the role column from org_memberships
--    The unique constraint on (org_id, user_id) is kept – it guarantees
--    one membership record per user per org.
ALTER TABLE org_memberships DROP COLUMN role;

COMMIT;
