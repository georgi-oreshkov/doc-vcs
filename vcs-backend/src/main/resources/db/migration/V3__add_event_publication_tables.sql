-- V3: Create Spring Modulith event publication tables.
--
-- spring-modulith-starter-jpa (2.0.x) maps two JPA entities to these tables:
--   DefaultJpaEventPublication  → EVENT_PUBLICATION
--   ArchivedJpaEventPublication → EVENT_PUBLICATION_ARCHIVE
--
-- Because ddl-auto=validate, Hibernate will not create them automatically.

SET search_path TO vcs_core;

CREATE TABLE event_publication (
    id                    UUID         NOT NULL PRIMARY KEY,
    publication_date      TIMESTAMPTZ  NOT NULL,
    listener_id           VARCHAR(512) NOT NULL,
    serialized_event      TEXT         NOT NULL,
    event_type            VARCHAR(512) NOT NULL,
    completion_date       TIMESTAMPTZ,
    last_resubmission_date TIMESTAMPTZ,
    completion_attempts   INT          NOT NULL DEFAULT 0,
    status                VARCHAR(64)
);

CREATE TABLE event_publication_archive (
    id                    UUID         NOT NULL PRIMARY KEY,
    publication_date      TIMESTAMPTZ  NOT NULL,
    listener_id           VARCHAR(512) NOT NULL,
    serialized_event      TEXT         NOT NULL,
    event_type            VARCHAR(512) NOT NULL,
    completion_date       TIMESTAMPTZ,
    last_resubmission_date TIMESTAMPTZ,
    completion_attempts   INT          NOT NULL DEFAULT 0,
    status                VARCHAR(64)
);

-- Index for the most frequent query: find incomplete publications
CREATE INDEX idx_event_publication_incomplete
    ON event_publication (status)
    WHERE completion_date IS NULL;

