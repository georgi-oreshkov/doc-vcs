package com.root.vcsbackend.organization.api;

import com.root.vcsbackend.organization.domain.OrgMembershipEntity.OrgRole;
import com.root.vcsbackend.organization.domain.OrgUserRoleEntity;
import com.root.vcsbackend.organization.persistence.OrgMembershipRepository;
import com.root.vcsbackend.organization.persistence.OrgUserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Public API of the organization module. Only this class should be referenced by other modules.
 */
@Component
@RequiredArgsConstructor
public class OrganizationFacade {

    private final OrgMembershipRepository orgMembershipRepository;
    private final OrgUserRoleRepository orgUserRoleRepository;

    public List<String> resolveRoles(UUID orgId, UUID userId) {
        return orgUserRoleRepository.findByOrgIdAndUserId(orgId, userId)
            .stream().map(r -> r.getRole().name()).toList();
    }

    public boolean isMember(UUID orgId, UUID userId) {
        return orgMembershipRepository.existsByOrgIdAndUserId(orgId, userId);
    }

    /**
     * Returns true if the user holds any of the given role names in the org.
     */
    public boolean hasRole(UUID orgId, UUID userId, String... roleNames) {
        List<String> userRoles = resolveRoles(orgId, userId);
        List<String> required = Arrays.asList(roleNames);
        return userRoles.stream().anyMatch(required::contains);
    }

    /**
     * Fetches the User IDs of all members with ADMIN or REVIEWER roles in the organization.
     */
    public List<UUID> getReviewersAndAdmins(UUID orgId) {
        return orgUserRoleRepository.findByOrgIdAndRoleIn(orgId, Arrays.asList(OrgRole.ADMIN, OrgRole.REVIEWER))
            .stream()
            .map(OrgUserRoleEntity::getUserId)
            .distinct()
            .toList();
    }
}
