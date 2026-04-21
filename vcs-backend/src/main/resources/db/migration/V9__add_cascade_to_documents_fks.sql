-- V9: Add ON DELETE CASCADE/SET NULL to documents foreign keys
--
-- PROBLEM:
--   Organizations cannot be deleted when they have documents.
--   Error: "update or delete on table "organizations" violates foreign key
--          constraint "documents_org_id_fkey" on table "documents""
--
-- SOLUTION:
--   [1] documents.org_id → organizations(id)
--       Add ON DELETE CASCADE — when an org is deleted, all its documents
--       should be deleted as well (they're owned by the org).
--
--   [2] documents.author_id → user_profiles(id)
--       Add ON DELETE SET NULL — when an author is deleted, documents remain
--       but author_id becomes NULL. Documents with NULL author_id can only be
--       viewed/managed by admins (orphaned documents).
--
--   [3] fork_requests.requester_id → user_profiles(id)
--       Add ON DELETE SET NULL — when a user is deleted, their fork requests
--       remain for audit/history purposes, but requester becomes NULL.
--
--   [4] fork_requests.doc_id → documents(id)
--       Add ON DELETE CASCADE — when a document is deleted, its fork requests
--       should also be deleted (no orphaned requests).
--
--   [5] fork_requests.version_id → versions(id)
--       Add ON DELETE CASCADE — when a version is deleted, fork requests
--       pointing to it should also be deleted.
--
--   [6] comments.author_id → user_profiles(id)
--       Add ON DELETE SET NULL — when a user is deleted, their comments remain
--       for audit purposes, but author becomes NULL.

SET search_path TO vcs_core;

-- Make columns nullable where we use ON DELETE SET NULL
ALTER TABLE documents
    ALTER COLUMN author_id DROP NOT NULL;

ALTER TABLE fork_requests
    ALTER COLUMN requester_id DROP NOT NULL;

ALTER TABLE comments
    ALTER COLUMN author_id DROP NOT NULL;

-- [1] documents.org_id: CASCADE when org is deleted
ALTER TABLE documents
    DROP CONSTRAINT IF EXISTS documents_org_id_fkey,
    ADD CONSTRAINT documents_org_id_fkey
        FOREIGN KEY (org_id) REFERENCES organizations(id) ON DELETE CASCADE;

-- [2] documents.author_id: SET NULL when author is deleted (orphaned docs)
ALTER TABLE documents
    DROP CONSTRAINT IF EXISTS documents_author_id_fkey,
    ADD CONSTRAINT documents_author_id_fkey
        FOREIGN KEY (author_id) REFERENCES user_profiles(id) ON DELETE SET NULL;

-- [3] fork_requests.requester_id: SET NULL when requester is deleted
ALTER TABLE fork_requests
    DROP CONSTRAINT IF EXISTS fork_requests_requester_id_fkey,
    ADD CONSTRAINT fork_requests_requester_id_fkey
        FOREIGN KEY (requester_id) REFERENCES user_profiles(id) ON DELETE SET NULL;

-- [4] fork_requests.doc_id: CASCADE when document is deleted
ALTER TABLE fork_requests
    DROP CONSTRAINT IF EXISTS fork_requests_doc_id_fkey,
    ADD CONSTRAINT fork_requests_doc_id_fkey
        FOREIGN KEY (doc_id) REFERENCES documents(id) ON DELETE CASCADE;

-- [5] fork_requests.version_id: CASCADE when version is deleted
ALTER TABLE fork_requests
    DROP CONSTRAINT IF EXISTS fork_requests_version_id_fkey,
    ADD CONSTRAINT fork_requests_version_id_fkey
        FOREIGN KEY (version_id) REFERENCES versions(id) ON DELETE CASCADE;

-- [6] comments.author_id: SET NULL when author is deleted (keep comments for audit)
ALTER TABLE comments
    DROP CONSTRAINT IF EXISTS comments_author_id_fkey,
    ADD CONSTRAINT comments_author_id_fkey
        FOREIGN KEY (author_id) REFERENCES user_profiles(id) ON DELETE SET NULL;
