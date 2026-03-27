-- V1: initial schema

-- user_profiles
CREATE TABLE user_profiles (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL UNIQUE,
    photo_url   VARCHAR(1024),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    created_by  UUID
);

-- organizations
CREATE TABLE organizations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    created_by  UUID
);

-- org_memberships  (role: ADMIN | AUTHOR | REVIEWER | READER)
CREATE TABLE org_memberships (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id      UUID NOT NULL REFERENCES organizations(id),
    user_id     UUID NOT NULL REFERENCES user_profiles(id),
    role        VARCHAR(50) NOT NULL,
    UNIQUE (org_id, user_id)
);

-- categories
CREATE TABLE categories (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id  UUID NOT NULL REFERENCES organizations(id),
    name    VARCHAR(255) NOT NULL
);

-- documents  (status: DRAFT | PENDING_REVIEW | APPROVED | REJECTED)
CREATE TABLE documents (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                       UUID NOT NULL REFERENCES organizations(id),
    author_id                    UUID NOT NULL REFERENCES user_profiles(id),
    name                         VARCHAR(255) NOT NULL,
    status                       VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    category_id                  UUID REFERENCES categories(id),
    latest_version_id            UUID,
    latest_approved_version_id   UUID,
    created_at                   TIMESTAMPTZ NOT NULL,
    updated_at                   TIMESTAMPTZ NOT NULL,
    created_by                   UUID
);

-- document_reviewers  (ElementCollection for reviewer_ids on DocumentEntity)
CREATE TABLE document_reviewers (
    document_id  UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    reviewer_id  UUID NOT NULL REFERENCES user_profiles(id),
    PRIMARY KEY (document_id, reviewer_id)
);

-- versions  (status: PENDING | APPROVED | REJECTED)
CREATE TABLE versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_id          UUID NOT NULL REFERENCES documents(id),
    version_number  INT NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    is_draft        BOOLEAN NOT NULL DEFAULT TRUE,
    checksum        VARCHAR(128),
    s3_key          VARCHAR(1024) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID,
    UNIQUE (doc_id, version_number)
);

-- comments
CREATE TABLE comments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_id  UUID NOT NULL REFERENCES versions(id),
    author_id   UUID NOT NULL REFERENCES user_profiles(id),
    content     TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    created_by  UUID
);

-- fork_requests  (status: PENDING | APPROVED | REJECTED | CANCELLED)
CREATE TABLE fork_requests (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id  UUID NOT NULL REFERENCES user_profiles(id),
    doc_id        UUID NOT NULL REFERENCES documents(id),
    version_id    UUID NOT NULL REFERENCES versions(id),
    status        VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    created_by    UUID
);

-- notifications
CREATE TABLE notifications (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id  UUID NOT NULL REFERENCES user_profiles(id),
    type          VARCHAR(100) NOT NULL,
    payload       JSONB,
    read_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL
);
