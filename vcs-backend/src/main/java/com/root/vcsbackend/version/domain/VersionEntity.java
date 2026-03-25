package com.root.vcsbackend.version.domain;

import com.root.vcsbackend.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "versions")
@Getter
@Setter
@Builder
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
    private com.root.vcsbackend.version.domain.VersionStatus status;

    @Column(nullable = false)
    private Boolean isDraft;

    @Column(name = "s3_key", nullable = false)
    private String s3Key;
}

