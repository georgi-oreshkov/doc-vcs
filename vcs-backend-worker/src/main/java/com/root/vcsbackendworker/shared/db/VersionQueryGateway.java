package com.root.vcsbackendworker.shared.db;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only JDBC gateway for querying version rows in the vcs_core schema.
 * <p>
 * Schema is owned by vcs-backend (Flyway migrations).
 * This worker only reads — no inserts, updates, or DDL.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class VersionQueryGateway {

    private final JdbcClient jdbcClient;

    /**
     * Finds the last snapshot version before (or at) the given target version number
     * for a specific document.
     * <p>
     * Requires the {@code storage_type} column on the {@code versions} table
     * (added via Flyway migration in vcs-backend).
     *
     * @param docId               document ID
     * @param targetVersionNumber the version number to reconstruct up to
     * @return the closest preceding snapshot version, or empty if none exists
     */
    //subject to change
    public Optional<VersionRow> findLastSnapshotBefore(UUID docId, int targetVersionNumber) {
        return jdbcClient.sql("""
                SELECT id, doc_id, version_number, status, storage_type, checksum, s3_key
                FROM vcs_core.versions
                WHERE doc_id = :docId
                  AND version_number <= :targetVersionNumber
                  AND storage_type = 'SNAPSHOT'
                ORDER BY version_number DESC
                LIMIT 1
                """)
                .param("docId", docId)
                .param("targetVersionNumber", targetVersionNumber)
                .query(VersionRow.class)
                .optional();
    }

    /**
     * Returns all diff versions between two version numbers (exclusive start, inclusive end)
     * for a document, ordered ascending by version number.
     *
     * @param docId             document ID
     * @param afterVersionNumber  version number of the snapshot (exclusive)
     * @param upToVersionNumber   target version number (inclusive)
     * @return ordered list of diff version rows to apply sequentially
     */
    public List<VersionRow> findDiffVersionsBetween(UUID docId, int afterVersionNumber, int upToVersionNumber) {
        return jdbcClient.sql("""
                SELECT id, doc_id, version_number, status, storage_type, checksum, s3_key
                FROM vcs_core.versions
                WHERE doc_id = :docId
                  AND version_number > :afterVersionNumber
                  AND version_number <= :upToVersionNumber
                  AND storage_type = 'DIFF'
                ORDER BY version_number ASC
                """)
                .param("docId", docId)
                .param("afterVersionNumber", afterVersionNumber)
                .param("upToVersionNumber", upToVersionNumber)
                .query(VersionRow.class)
                .list();
    }

    /**
     * Fetches a single version by its ID.
     */
    public Optional<VersionRow> findById(UUID versionId) {
        return jdbcClient.sql("""
                SELECT id, doc_id, version_number, status, storage_type, checksum, s3_key
                FROM vcs_core.versions
                WHERE id = :versionId
                """)
                .param("versionId", versionId)
                .query(VersionRow.class)
                .optional();
    }
}

