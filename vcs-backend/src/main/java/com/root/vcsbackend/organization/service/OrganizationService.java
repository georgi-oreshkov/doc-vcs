package com.root.vcsbackend.organization.service;

import com.root.vcsbackend.model.CreateOrganizationRequest;
import com.root.vcsbackend.model.OrgUser;
import com.root.vcsbackend.organization.domain.OrgMembershipEntity;
import com.root.vcsbackend.organization.domain.OrgMembershipEntity.OrgRole;
import com.root.vcsbackend.organization.domain.OrganizationEntity;
import com.root.vcsbackend.organization.mapper.OrganizationMapper;
import com.root.vcsbackend.organization.persistence.OrgMembershipRepository;
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
    private final OrganizationMapper organizationMapper;
    private final UserFacade userFacade;

    public OrganizationEntity createOrganization(CreateOrganizationRequest req, UUID callerId) {
        OrganizationEntity org = organizationMapper.toEntity(req);
        org = organizationRepository.save(org);
        // Creator automatically becomes ADMIN
        orgMembershipRepository.save(OrgMembershipEntity.builder()
            .orgId(org.getId()).userId(callerId).role(OrgRole.ADMIN).build());
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
    public List<OrganizationService.OrgWithRole> listOrganizations(UUID userId) {
        List<OrgMembershipEntity> memberships = orgMembershipRepository.findByUserId(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        Map<UUID, OrgMembershipEntity> membershipByOrgId = memberships.stream()
            .collect(Collectors.toMap(OrgMembershipEntity::getOrgId, Function.identity()));
        List<OrganizationEntity> orgs = organizationRepository.findAllById(membershipByOrgId.keySet());
        return orgs.stream()
            .map(org -> new OrgWithRole(org, membershipByOrgId.get(org.getId()).getRole()))
            .toList();
    }

    public record OrgWithRole(OrganizationEntity org, OrgRole role) {}

    @Transactional(readOnly = true)
    @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'ADMIN', 'AUTHOR', 'REVIEWER', 'READER')")
    public List<OrgMembershipEntity> listOrgUsers(UUID orgId) {
        resolve(orgId); // verify org exists
        return orgMembershipRepository.findByOrgId(orgId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, UserProfileEntity> resolveUserProfiles(List<UUID> userIds) {
        return userFacade.resolveUsers(userIds);
    }

    @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'ADMIN')")
    public OrgMembershipEntity upsertOrgUserRole(UUID orgId, OrgUser req) {
        resolve(orgId);
        OrgMembershipEntity membership = orgMembershipRepository
            .findByOrgIdAndUserId(orgId, req.getUserId())
            .orElse(OrgMembershipEntity.builder().orgId(orgId).userId(req.getUserId()).build());
        membership.setRole(OrgRole.valueOf(req.getRole().getValue()));
        return orgMembershipRepository.save(membership);
    }

    @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'ADMIN')")
    public void removeOrgUser(UUID orgId, UUID userId) {
        resolve(orgId);
        if (!orgMembershipRepository.existsByOrgIdAndUserId(orgId, userId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "User is not a member of this organization");
        }
        orgMembershipRepository.deleteByOrgIdAndUserId(orgId, userId);
    }

    private OrganizationEntity resolve(UUID orgId) {
        return organizationRepository.findById(orgId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Organization not found: " + orgId));
    }
}
