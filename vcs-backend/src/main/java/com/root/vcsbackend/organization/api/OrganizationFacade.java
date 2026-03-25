package com.root.vcsbackend.organization.api;

import com.root.vcsbackend.organization.domain.OrgMembershipEntity;
import com.root.vcsbackend.organization.persistence.OrgMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the organization module. Only this class should be used by other modules.
 */
@Component
@RequiredArgsConstructor
public class OrganizationFacade {

    private final OrgMembershipRepository orgMembershipRepository;

    public Optional<OrgMembershipEntity.OrgRole> resolveRole(UUID orgId, UUID userId) {
        // TODO: implement
        return Optional.empty();
    }

    public boolean isMember(UUID orgId, UUID userId) {
        // TODO: implement
        return false;
    }
}

