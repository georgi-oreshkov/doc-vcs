package com.root.vcsbackend.organization.domain;

import com.root.vcsbackend.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
    name = "org_memberships",
    uniqueConstraints = @UniqueConstraint(columnNames = {"org_id", "user_id"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgMembershipEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrgRole role;

    /** Matches OpenAPI OrgUser.Role enum exactly. */
    public enum OrgRole {
        ADMIN, AUTHOR, REVIEWER, READER
    }
}
