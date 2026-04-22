-- V11: Add DRAFT to the allowed values for versions.status.
-- The VersionStatus enum and createVersion() both use DRAFT for newly uploaded
-- versions, but the original constraint omitted it.

ALTER TABLE vcs_core.versions
    DROP CONSTRAINT chk_versions_status;

ALTER TABLE vcs_core.versions
    ADD CONSTRAINT chk_versions_status
        CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED'));
