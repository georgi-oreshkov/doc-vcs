package com.root.vcsbackend.version.domain;

/**
 * How a version's content is stored in S3.
 * <ul>
 *   <li>{@code SNAPSHOT} — full document content stored at the permanent S3 key
 *       derived from {@code S3KeyTemplates.permanentVersion(docId, versionNumber)}.</li>
 *   <li>{@code DIFF} — only a delta (patch) relative to the previous version.
 *       A full document must be reconstructed by the worker before it can be
 *       downloaded.</li>
 * </ul>
 */
public enum StorageType {
    SNAPSHOT,
    DIFF
}

