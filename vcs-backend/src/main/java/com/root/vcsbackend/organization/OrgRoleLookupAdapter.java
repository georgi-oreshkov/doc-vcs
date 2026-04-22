package com.root.vcsbackend.organization;

import com.root.vcsbackend.organization.persistence.OrgUserRoleRepository;
import com.root.vcsbackend.shared.security.OrgRoleLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Implements OrgRoleLookup (defined in shared) using the organization module's
 * own repository. This keeps shared free of compile dependencies on organization.
 */
@Component
@RequiredArgsConstructor
class OrgRoleLookupAdapter implements OrgRoleLookup {

    private final OrgUserRoleRepository orgUserRoleRepository;

    @Override
    public List<String> findRoles(UUID orgId, UUID userId) {
        return orgUserRoleRepository.findByOrgIdAndUserId(orgId, userId)
            .stream()
            .map(r -> r.getRole().name())
            .toList();
    }
}

