package com.root.vcsbackend.organization.web;

import com.root.vcsbackend.api.OrganizationsApi;
import com.root.vcsbackend.model.CreateOrganizationRequest;
import com.root.vcsbackend.model.OrgUser;
import com.root.vcsbackend.model.Organization;
import com.root.vcsbackend.organization.domain.OrgMembershipEntity;
import com.root.vcsbackend.organization.mapper.OrganizationMapper;
import com.root.vcsbackend.organization.service.OrganizationService;
import com.root.vcsbackend.shared.security.SecurityHelper;
import com.root.vcsbackend.user.domain.UserProfileEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OrganizationsController implements OrganizationsApi {

    private final OrganizationService organizationService;
    private final OrganizationMapper organizationMapper;
    private final SecurityHelper securityHelper;

    @Override
    public ResponseEntity<Organization> createOrganization(CreateOrganizationRequest req) {
        UUID callerId = securityHelper.currentUser().userId();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(organizationMapper.toDto(organizationService.createOrganization(req, callerId)));
    }

    @Override
    public ResponseEntity<Void> deleteOrganization(UUID orgId) {
        organizationService.deleteOrganization(orgId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Organization> getOrganization(UUID orgId) {
        return ResponseEntity.ok(organizationMapper.toDto(organizationService.getOrganization(orgId)));
    }

    @Override
    public ResponseEntity<List<OrgUser>> listOrgUsers(UUID orgId) {
        List<OrgMembershipEntity> memberships = organizationService.listOrgUsers(orgId);
        List<UUID> userIds = memberships.stream().map(OrgMembershipEntity::getUserId).toList();
        Map<UUID, UserProfileEntity> profiles = organizationService.resolveUserProfiles(userIds);
        List<OrgUser> users = memberships.stream()
            .map(m -> organizationMapper.toOrgUserDto(m, profiles.get(m.getUserId())))
            .toList();
        return ResponseEntity.ok(users);
    }

    @Override
    public ResponseEntity<List<Organization>> listOrganizations() {
        UUID callerId = securityHelper.currentUser().userId();
        List<Organization> orgs = organizationService.listOrganizations(callerId).stream()
            .map(owr -> organizationMapper.toDto(owr.org(), owr.role()))
            .toList();
        return ResponseEntity.ok(orgs);
    }

    @Override
    public ResponseEntity<Void> removeOrgUser(UUID orgId, UUID userId) {
        organizationService.removeOrgUser(orgId, userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Organization> updateOrganization(UUID orgId, CreateOrganizationRequest req) {
        return ResponseEntity.ok(
            organizationMapper.toDto(organizationService.updateOrganization(orgId, req)));
    }

    @Override
    public ResponseEntity<OrgUser> upsertOrgUserRole(UUID orgId, OrgUser orgUser) {
        return ResponseEntity.ok(
            organizationMapper.toOrgUserDto(organizationService.upsertOrgUserRole(orgId, orgUser)));
    }
}
