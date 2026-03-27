package com.root.vcsbackend.organization;

import com.root.vcsbackend.organization.persistence.OrgMembershipRepository;
import com.root.vcsbackend.shared.security.OrgRoleLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Implements OrgRoleLookup (defined in shared) using the organization module's
 * own repository. This keeps shared free of compile dependencies on organization.
 */
@Component
@RequiredArgsConstructor
class OrgRoleLookupAdapter implements OrgRoleLookup {

    private final OrgMembershipRepository membershipRepository;

    @Override
    public Optional<String> findRole(UUID orgId, UUID userId) {
        return membershipRepository.findByOrgIdAndUserId(orgId, userId)
            .map(m -> m.getRole().name());
    }
}

