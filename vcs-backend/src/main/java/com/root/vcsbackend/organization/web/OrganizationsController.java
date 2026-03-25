package com.root.vcsbackend.organization.web;

import com.root.vcsbackend.api.OrganizationsApi;
import com.root.vcsbackend.model.CreateOrganizationRequest;
import com.root.vcsbackend.model.OrgUser;
import com.root.vcsbackend.model.Organization;
import com.root.vcsbackend.organization.service.OrganizationService;
import com.root.vcsbackend.shared.security.CurrentUser;
import com.root.vcsbackend.shared.security.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OrganizationsController implements OrganizationsApi {

    private final OrganizationService organizationService;

    @Override
    public ResponseEntity<Organization> createOrganization(CreateOrganizationRequest createOrganizationRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Void> deleteOrganization(UUID orgId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Organization> getOrganization(UUID orgId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<List<OrgUser>> listOrgUsers(UUID orgId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<List<Organization>> listOrganizations() {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Void> removeOrgUser(UUID orgId, UUID userId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Organization> updateOrganization(UUID orgId, CreateOrganizationRequest createOrganizationRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<OrgUser> upsertOrgUserRole(UUID orgId, OrgUser orgUser) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
