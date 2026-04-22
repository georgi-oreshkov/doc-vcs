package com.root.vcsbackend.organization.service;

import com.root.vcsbackend.model.CreateOrganizationRequest;
import com.root.vcsbackend.model.OrgUser;
import com.root.vcsbackend.organization.domain.OrgMembershipEntity;
import com.root.vcsbackend.organization.domain.OrgMembershipEntity.OrgRole;
import com.root.vcsbackend.organization.domain.OrgUserRoleEntity;
import com.root.vcsbackend.organization.domain.OrganizationEntity;
import com.root.vcsbackend.organization.mapper.OrganizationMapper;
import com.root.vcsbackend.organization.persistence.OrgMembershipRepository;
import com.root.vcsbackend.organization.persistence.OrgUserRoleRepository;
import com.root.vcsbackend.organization.persistence.OrganizationRepository;
import com.root.vcsbackend.shared.exception.AppException;
import com.root.vcsbackend.user.api.UserFacade;
import com.root.vcsbackend.user.domain.UserProfileEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrgMembershipRepository orgMembershipRepository;
    private final OrgUserRoleRepository orgUserRoleRepository;
    private final OrganizationMapper organizationMapper;
    private final UserFacade userFacade;

    public OrganizationEntity createOrganization(CreateOrganizationRequest req, UUID callerId) {
        OrganizationEntity org = organizationMapper.toEntity(req);
        org = organizationRepository.save(org);
        // Creator automatically becomes ADMIN
        OrgMembershipEntity membership = orgMembershipRepository.save(
            OrgMembershipEntity.builder().orgId(org.getId()).userId(callerId).build());
        orgUserRoleRepository.save(
            OrgUserRoleEntity.builder().orgId(org.getId()).userId(callerId).role(OrgRole.ADMIN).build());
        return org;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'ADMIN', 'AUTHOR', 'REVIEWER', 'READER')")
    public OrganizationEntity getOrganization(UUID orgId) {
        return resolve(orgId);
    }

    @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'ADMIN')")
    public OrganizationEntity updateOrganization(UUID orgId, CreateOrganizationRequest req) {
        OrganizationEntity org = resolve(orgId);
        org.setName(req.getName());
        return organizationRepository.save(org);
    }

    @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'ADMIN')")
    public void deleteOrganization(UUID orgId) {
        organizationRepository.delete(resolve(orgId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<OrgWithRoles> listOrganizations(UUID userId) {
        List<OrgMembershipEntity> memberships = orgMembershipRepository.findByUserId(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<UUID> orgIds = memberships.stream().map(OrgMembershipEntity::getOrgId).toList();
        List<OrganizationEntity> orgs = organizationRepository.findAllById(orgIds);
        // Collect roles per org for this user
        return orgs.stream()
            .map(org -> new OrgWithRoles(org,
                orgUserRoleRepository.findByOrgIdAndUserId(org.getId(), userId)))
            .toList();
    }

    public record OrgWithRoles(OrganizationEntity org, List<OrgUserRoleEntity> roles) {}

    @Transactional(readOnly = true)
    @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'ADMIN', 'AUTHOR', 'REVIEWER', 'READER')")
    public List<OrgMembershipEntity> listOrgUsers(UUID orgId) {
        resolve(orgId); // verify org exists
        return orgMembershipRepository.findByOrgId(orgId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<OrgUserRoleEntity>> getRolesByUsers(UUID orgId, List<UUID> userIds) {
        return orgUserRoleRepository.findByOrgId(orgId).stream()
            .filter(r -> userIds.contains(r.getUserId()))
            .collect(Collectors.groupingBy(OrgUserRoleEntity::getUserId));
    }

    @Transactional(readOnly = true)
    public Map<UUID, UserProfileEntity> resolveUserProfiles(List<UUID> userIds) {
        return userFacade.resolveUsers(userIds);
    }

    /**
     * Add or replace a member's roles in the org (replaces all existing roles for that user).
     */
    @Transactional
    @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'ADMIN')")
    public List<OrgUserRoleEntity> upsertOrgUserRoles(UUID orgId, OrgUser req) {
        resolve(orgId);
        UUID userId = req.getUserId();
        // Ensure membership exists
        if (!orgMembershipRepository.existsByOrgIdAndUserId(orgId, userId)) {
            orgMembershipRepository.save(
                OrgMembershipEntity.builder().orgId(orgId).userId(userId).build());
        }
        // Replace all roles — use native ON CONFLICT DO NOTHING to be idempotent
        orgUserRoleRepository.deleteByOrgIdAndUserId(orgId, userId);
        req.getRoles().stream()
            .map(r -> r.getValue())
            .distinct()
            .forEach(role -> orgUserRoleRepository.insertRoleIfAbsent(orgId, userId, role));
        return orgUserRoleRepository.findByOrgIdAndUserId(orgId, userId);
    }

    @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'ADMIN')")
    public void removeOrgUser(UUID orgId, UUID userId) {
        resolve(orgId);
        if (!orgMembershipRepository.existsByOrgIdAndUserId(orgId, userId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "User is not a member of this organization");
        }
        orgUserRoleRepository.deleteByOrgIdAndUserId(orgId, userId);
        orgMembershipRepository.deleteByOrgIdAndUserId(orgId, userId);
    }

    private OrganizationEntity resolve(UUID orgId) {
        return organizationRepository.findById(orgId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Organization not found: " + orgId));
    }
}
