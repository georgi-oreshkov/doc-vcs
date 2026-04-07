-- V4: Add storage_type column to versions table.
-- Required by vcs-backend-worker to distinguish between full snapshots
-- and delta-stored versions when reconstructing documents.
-- Existing rows default to 'SNAPSHOT' (all previously uploaded content is full).

ALTER TABLE vcs_core.versions
    ADD COLUMN storage_type VARCHAR(20) NOT NULL DEFAULT 'SNAPSHOT';

-- Constrain to allowed values.
ALTER TABLE vcs_core.versions
    ADD CONSTRAINT chk_versions_storage_type
        CHECK (storage_type IN ('SNAPSHOT', 'DIFF'));

-- Index so the worker can efficiently find the nearest snapshot.
CREATE INDEX idx_versions_storage_type
    ON vcs_core.versions (doc_id, version_number)
    WHERE storage_type = 'SNAPSHOT';

