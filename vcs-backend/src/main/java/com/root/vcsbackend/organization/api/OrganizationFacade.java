package com.root.vcsbackend.organization.api;

import com.root.vcsbackend.organization.domain.OrgMembershipEntity;
import com.root.vcsbackend.organization.domain.OrgMembershipEntity.OrgRole;
import com.root.vcsbackend.organization.persistence.OrgMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the organization module. Only this class should be referenced by other modules.
 */
@Component
@RequiredArgsConstructor
public class OrganizationFacade {

    private final OrgMembershipRepository orgMembershipRepository;

    public Optional<OrgRole> resolveRole(UUID orgId, UUID userId) {
        return orgMembershipRepository.findByOrgIdAndUserId(orgId, userId)
            .map(OrgMembershipEntity::getRole);
    }

    public boolean isMember(UUID orgId, UUID userId) {
        return orgMembershipRepository.existsByOrgIdAndUserId(orgId, userId);
    }

    /**
     * Returns true if the user holds one of the given role names in the org.
     * Used by other modules (e.g. request) for permission checks without importing OrgRole.
     */
    public boolean hasRole(UUID orgId, UUID userId, String... roleNames) {
        return orgMembershipRepository.findByOrgIdAndUserId(orgId, userId)
            .map(m -> Arrays.asList(roleNames).contains(m.getRole().name()))
            .orElse(false);
    }
}
