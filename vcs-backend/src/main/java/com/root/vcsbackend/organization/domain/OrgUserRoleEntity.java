package com.root.vcsbackend.organization.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
    name = "org_user_roles",
    uniqueConstraints = @UniqueConstraint(columnNames = {"org_id", "user_id", "role"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgUserRoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrgMembershipEntity.OrgRole role;
}
