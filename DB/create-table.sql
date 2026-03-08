SET
    search_path TO vcs_core;

CREATE TYPE request_status_type AS ENUM ('pending', 'approved', 'rejected');

CREATE TYPE version_status_type AS ENUM ('pending', 'approved', 'rejected', 'draft');

CREATE TYPE user_role AS ENUM ('admin', 'reader', 'reviewer');

-- 1. Organizations
CREATE TABLE organizations (
    org_id UUID PRIMARY KEY DEFAULT uuidv7 (),
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now ()
);

-- 2. Users 
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT uuidv7 (),
    org_id UUID REFERENCES organizations (org_id) ON DELETE CASCADE,
    email TEXT UNIQUE NOT NULL,
    full_name TEXT NOT NULL,
    role user_role NOT NULL DEFAULT 'reader',
    created_at TIMESTAMPTZ DEFAULT now ()
);

CREATE INDEX idx_users_org ON users (org_id);

-- 3. Documents
CREATE TABLE documents (
    doc_id UUID PRIMARY KEY DEFAULT uuidv7 (),
    author_id UUID NOT NULL REFERENCES users (user_id),
    title TEXT NOT NULL,
    parent_doc_id UUID,
    parent_version_num INTEGER,
    created_at TIMESTAMPTZ DEFAULT now (),
    deleted_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_documents_author ON documents (author_id);

-- List of reviewers
CREATE TABLE document_reviewers (
    doc_id UUID REFERENCES documents (doc_id) ON DELETE CASCADE,
    user_id UUID REFERENCES users (user_id) ON DELETE CASCADE,
    PRIMARY KEY (doc_id, user_id)
);

-- 4. Document Metadata
CREATE TABLE doc_metadata (
    doc_id UUID PRIMARY KEY REFERENCES documents (doc_id) ON DELETE CASCADE,
    latest_version INT DEFAULT 0,
    latest_approved_version INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now (),
    updated_at TIMESTAMPTZ NULL
);

-- 5. Version Metadata
CREATE TABLE version_metadata (
    doc_id UUID NOT NULL REFERENCES documents (doc_id) ON DELETE CASCADE,
    version_num INTEGER NOT NULL,
    s3_location TEXT NOT NULL,
    version_status version_status_type NOT NULL DEFAULT 'pending',
    reviewer_comment TEXT,
    created_at TIMESTAMPTZ DEFAULT now (),
    updated_at TIMESTAMPTZ NULL,
    PRIMARY KEY (doc_id, version_num)
);

CREATE INDEX idx_version_status_approved ON version_metadata (doc_id, version_status)
WHERE
    version_status = 'approved';

CREATE INDEX idx_version_status_pending ON version_metadata (doc_id, version_status)
WHERE
    version_status = 'pending';

-- This must come AFTER version_metadata is created
ALTER TABLE documents ADD CONSTRAINT fk_parent_version_doc FOREIGN KEY (parent_doc_id, parent_version_num) REFERENCES version_metadata (doc_id, version_num) ON DELETE RESTRICT;

CREATE TABLE collaboration_requests (
    request_id UUID PRIMARY KEY DEFAULT uuidv7 (),
    parent_doc_id UUID NOT NULL,
    parent_doc_version INTEGER NOT NULL,
    requestor_id UUID REFERENCES users (user_id),
    target_author_id UUID REFERENCES users (user_id),
    status request_status_type DEFAULT 'pending',
    created_at TIMESTAMPTZ DEFAULT now (),
    updated_ad TIMESTAMPTZ NULL,
    CONSTRAINT fk_parent_version_requests FOREIGN KEY (parent_doc_id, parent_version_num) REFERENCES version_metadata (doc_id, version_num) ON DELETE RESTRICT
);

CREATE INDEX idx_collab_target_author ON collaboration_requests (target_author_id)
WHERE
    status = 'pending';