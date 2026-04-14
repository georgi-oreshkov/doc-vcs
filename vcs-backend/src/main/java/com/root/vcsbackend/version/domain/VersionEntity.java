package com.root.vcsbackend.version.domain;

import com.root.vcsbackend.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
    name = "versions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"doc_id", "version_number"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "doc_id", nullable = false)
    private UUID docId;

    @Column(nullable = false)
    private Integer versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VersionStatus status;

    @Column(nullable = false)
    private Boolean isDraft;

    /** SHA-256 (or similar) of the uploaded file content. */
    @Column
    private String checksum;


    /**
     * How this version's content is persisted in S3.
     * {@code SNAPSHOT} means the S3 key holds the full document;
     * {@code DIFF} means only a delta is stored and the worker must
     * reconstruct the full document before it can be downloaded.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false)
    @Builder.Default
    private StorageType storageType = StorageType.SNAPSHOT;
}
