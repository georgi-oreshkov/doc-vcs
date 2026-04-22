package com.root.vcsbackend.organization.persistence;

import com.root.vcsbackend.organization.domain.OrgMembershipEntity.OrgRole;
import com.root.vcsbackend.organization.domain.OrgUserRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface OrgUserRoleRepository extends JpaRepository<OrgUserRoleEntity, UUID> {

    List<OrgUserRoleEntity> findByOrgIdAndUserId(UUID orgId, UUID userId);

    List<OrgUserRoleEntity> findByOrgId(UUID orgId);

    List<OrgUserRoleEntity> findByOrgIdAndRoleIn(UUID orgId, List<OrgRole> roles);

    boolean existsByOrgIdAndUserId(UUID orgId, UUID userId);

    @Modifying(clearAutomatically = true)
    @Transactional
    void deleteByOrgIdAndUserId(UUID orgId, UUID userId);
}
