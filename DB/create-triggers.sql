-- UPDATED_AT TRIGGERS
CREATE OR REPLACE FUNCTION vcs_core.update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    IF (OLD IS DISTINCT FROM NEW) THEN
        NEW.updated_at = now();
        RETURN NEW;
    END IF;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Trigger for Document Metadata
CREATE TRIGGER update_doc_metadata_modtime
BEFORE UPDATE ON vcs_core.doc_metadata
FOR EACH ROW
EXECUTE FUNCTION vcs_core.update_timestamp();

-- Trigger for Version Metadata
CREATE TRIGGER update_version_metadata_modtime
BEFORE UPDATE ON vcs_core.version_metadata
FOR EACH ROW
EXECUTE FUNCTION vcs_core.update_timestamp();

-- Trigger for Collaboration Requests
CREATE TRIGGER update_collab_requests_modtime
BEFORE UPDATE ON vcs_core.collaboration_requests
FOR EACH ROW
EXECUTE FUNCTION vcs_core.update_timestamp();

-- DOC_METADATA INSERT TRIGGER
CREATE OR REPLACE FUNCTION vcs_core.initialize_doc_metadata()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO vcs_core.doc_metadata (doc_id)
    VALUES (NEW.doc_id);
    RETURN NEW;
END;
$$ LANGUAGE 'plpgsql';

--Trigger
CREATE TRIGGER trigger_init_doc_metadata
AFTER INSERT ON vcs_core.documents
FOR EACH ROW
EXECUTE FUNCTION vcs_core.initialize_doc_metadata();

--TRIGGER TO UPDATE DOC_METADATA ON INSERT AND UPDATE OF VERSION_METADATA

CREATE OR REPLACE FUNCTION vcs_core.update_doc_meta_counters()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE vcs_core.doc_metadata
    SET 
        latest_version_num = NEW.version_num,
        latest_approved_version = CASE 
            WHEN NEW.version_status = 'approved' THEN NEW.version_num 
            ELSE latest_approved_version
        END
    WHERE doc_id = NEW.doc_id;
    RETURN NEW;
END;
$$ LANGUAGE 'plpgsql';

CREATE TRIGGER trigger_incr_doc_meta_counters
AFTER INSERT OR UPDATE ON vcs_core.version_metadata
FOR EACH ROW
EXECUTE FUNCTION vcs_core.update_doc_meta_counters();

-- WRITE PROTECTION TRIGGER FOR VERSIONS
CREATE OR REPLACE FUNCTION vcs_core.lock_non_pending_versions()
RETURNS TRIGGER AS $$
BEGIN
    IF (OLD.s3_location IS DISTINCT FROM NEW.s3_location) THEN
        RAISE EXCEPTION 's3_location is immutable once saved.';
    END IF;
    IF (OLD.version_status != 'pending') THEN
        RAISE EXCEPTION 'Cannot UPDATE % version.', OLD.version_status;
    END IF;
    IF (OLD.version_num IS DISTINCT FROM NEW.version_num) THEN
        RAISE EXCEPTION 'version_num cannot be modified.';
    END IF;
END;
$$ LANGUAGE 'plpgsql';

CREATE TRIGGER trigger_lock_version_metadata
BEFORE UPDATE ON vcs_core.version_metadata
FOR EACH ROW
EXECUTE FUNCTION vcs_core.lock_non_pending_versions();
