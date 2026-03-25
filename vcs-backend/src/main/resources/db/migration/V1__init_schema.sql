-- V1: initial schema
-- TODO: write DDL for all entities matching the JPA entity classes

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

-- org_memberships
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

-- documents
CREATE TABLE documents (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                       UUID NOT NULL REFERENCES organizations(id),
    author_id                    UUID NOT NULL REFERENCES user_profiles(id),
    name                         VARCHAR(255) NOT NULL,
    status                       VARCHAR(50) NOT NULL,
    category_id                  UUID REFERENCES categories(id),
    latest_version_id            UUID,
    latest_approved_version_id   UUID,
    created_at                   TIMESTAMPTZ NOT NULL,
    updated_at                   TIMESTAMPTZ NOT NULL,
    created_by                   UUID
);

-- versions
CREATE TABLE versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_id          UUID NOT NULL REFERENCES documents(id),
    version_number  INT NOT NULL,
    status          VARCHAR(50) NOT NULL,
    is_draft        BOOLEAN NOT NULL DEFAULT TRUE,
    s3_key          VARCHAR(1024) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID
);

-- comments
CREATE TABLE comments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_id  UUID NOT NULL REFERENCES versions(id),
    author_id   UUID NOT NULL REFERENCES user_profiles(id),
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    created_by  UUID
);

-- fork_requests
CREATE TABLE fork_requests (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type             VARCHAR(50) NOT NULL,
    requester_id     UUID NOT NULL REFERENCES user_profiles(id),
    doc_id           UUID NOT NULL REFERENCES documents(id),
    from_version_id  UUID REFERENCES versions(id),
    status           VARCHAR(50) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    created_by       UUID
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

