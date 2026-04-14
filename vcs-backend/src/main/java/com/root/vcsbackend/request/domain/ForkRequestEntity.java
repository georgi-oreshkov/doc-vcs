package com.root.vcsbackend.request.domain;

import com.root.vcsbackend.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "fork_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForkRequestEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "doc_id", nullable = false)
    private UUID docId;

    /** The specific version the fork is based on. Required, matches OpenAPI version_id. */
    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    /** Matches OpenAPI ForkRequest.status enum exactly. */
    public enum RequestStatus {
        PENDING, APPROVED, REJECTED, CANCELLED
    }
}
