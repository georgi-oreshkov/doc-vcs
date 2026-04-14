-- =============================================================================
-- V7: Drop s3_key column from versions table.
--
-- S3 keys are now derived deterministically from (doc_id, version_number)
-- via S3KeyTemplates — no need to persist them.
-- Also updates the lock_version trigger to remove the s3_key immutability check.
-- =============================================================================

-- 1. Update the lock_version trigger to remove the s3_key guard.
CREATE OR REPLACE FUNCTION vcs_core.lock_version()
RETURNS TRIGGER AS $$
BEGIN
    IF (OLD.version_number IS DISTINCT FROM NEW.version_number) THEN
        RAISE EXCEPTION 'version_number is immutable once set.';
    END IF;

    IF (OLD.status IN ('APPROVED', 'REJECTED') AND OLD.status IS DISTINCT FROM NEW.status) THEN
        RAISE EXCEPTION 'Cannot change status of an already-% version.', OLD.status;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2. Drop the column.
ALTER TABLE vcs_core.versions DROP COLUMN s3_key;

