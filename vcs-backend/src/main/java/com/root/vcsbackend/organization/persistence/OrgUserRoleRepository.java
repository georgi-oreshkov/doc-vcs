package com.root.vcsbackend.organization.persistence;

import com.root.vcsbackend.organization.domain.OrgMembershipEntity.OrgRole;
import com.root.vcsbackend.organization.domain.OrgUserRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Modifying
    @Transactional
    void deleteByOrgId(UUID orgId);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO org_user_roles (id, org_id, user_id, role) VALUES (gen_random_uuid(), :orgId, :userId, :role) ON CONFLICT (org_id, user_id, role) DO NOTHING", nativeQuery = true)
    void insertRoleIfAbsent(@Param("orgId") UUID orgId, @Param("userId") UUID userId, @Param("role") String role);
}
