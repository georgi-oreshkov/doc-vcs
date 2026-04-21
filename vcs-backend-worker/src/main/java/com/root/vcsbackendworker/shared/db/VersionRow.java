package com.root.vcsbackendworker.shared.db;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Lightweight projection of a version row returned by read queries.
 * Not a JPA entity — mapped manually from JDBC result sets.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionRow {

    private UUID id;
    private UUID docId;
    private Integer versionNumber;
    private String status;
    private String storageType;
    private String checksum;
}

