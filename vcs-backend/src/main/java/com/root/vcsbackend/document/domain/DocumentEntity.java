package com.root.vcsbackend.document.domain;

import com.root.vcsbackend.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "latest_version_id")
    private UUID latestVersionId;

    @Column(name = "latest_approved_version_id")
    private UUID latestApprovedVersionId;

    /** Reviewers assigned at document creation time. Stored in document_reviewers table. */
    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "document_reviewers", joinColumns = @JoinColumn(name = "document_id"))
    @Column(name = "reviewer_id", nullable = false)
    private List<UUID> reviewerIds = new ArrayList<>();

    /** Ensures the collection is never null after JPA no-arg construction. */
    @PostLoad
    @PostPersist
    protected void initCollections() {
        if (reviewerIds == null) reviewerIds = new ArrayList<>();
    }

    public enum DocumentStatus {
        DRAFT, PENDING_REVIEW, APPROVED, REJECTED
    }
}
