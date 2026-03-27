package com.root.vcsbackend.shared.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;

@Component("orgRoleEvaluator")
@RequiredArgsConstructor
public class OrgRoleEvaluator {

    private final OrgRoleLookup orgRoleLookup;
    private final DocumentOrgLookup documentOrgLookup;

    /**
     * Usage: @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'ADMIN', 'AUTHOR')")
     */
    public boolean hasRole(UUID orgId, Authentication auth, String... roles) {
        UUID userId = extractUserId(auth);
        if (userId == null) return false;
        return orgRoleLookup.findRole(orgId, userId)
            .map(role -> Arrays.asList(roles).contains(role))
            .orElse(false);
    }

    /**
     * Usage: @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
     * Resolves doc → org, then checks membership for any role.
     */
    public boolean isDocumentMember(UUID docId, Authentication auth) {
        UUID userId = extractUserId(auth);
        if (userId == null) return false;
        return documentOrgLookup.findOrgId(docId)
            .flatMap(orgId -> orgRoleLookup.findRole(orgId, userId))
            .isPresent();
    }

    private UUID extractUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        if (auth.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        return null;
    }
}
