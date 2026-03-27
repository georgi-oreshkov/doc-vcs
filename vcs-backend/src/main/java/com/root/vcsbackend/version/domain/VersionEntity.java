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
@Table(name = "versions")
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

    @Column(name = "s3_key", nullable = false)
    private String s3Key;
}
