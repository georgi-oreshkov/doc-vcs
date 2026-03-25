package com.root.vcsbackend.shared.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("orgRoleEvaluator")
@RequiredArgsConstructor
public class OrgRoleEvaluator {

    // TODO: inject OrgMembershipRepository once organization module is wired

    /** Used as: @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'ADMIN')") */
    public boolean hasRole(UUID orgId, Authentication auth, String... roles) {
        // TODO: implement
        return false;
    }

    public boolean isDocumentMember(UUID docId, Authentication auth) {
        // TODO: implement
        return false;
    }

    private UUID extractUserId(Authentication auth) {
        // TODO: implement
        return null;
    }
}

