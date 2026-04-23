-- V12: Add is_uploading column to versions table.
-- Tracks whether a version's file is still being uploaded to S3.
-- Existing rows are assumed to have completed uploads, so default to FALSE.
ALTER TABLE vcs_core.versions
    ADD COLUMN is_uploading BOOLEAN NOT NULL DEFAULT FALSE;
