package com.root.vcsbackend.request.domain;

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
@Table(name = "fork_requests")
@Getter
@Setter
@Builder
public class ForkRequestEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestType type;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "doc_id", nullable = false)
    private UUID docId;

    @Column(name = "from_version_id")
    private UUID fromVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    public enum RequestType {
        FORK, DELETE
    }

    public enum RequestStatus {
        PENDING, APPROVED, REJECTED
    }
}

