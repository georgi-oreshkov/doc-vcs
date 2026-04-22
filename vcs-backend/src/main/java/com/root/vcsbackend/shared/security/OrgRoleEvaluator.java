package com.root.vcsbackend.shared.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
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
        List<String> userRoles = orgRoleLookup.findRoles(orgId, userId);
        List<String> required = Arrays.asList(roles);
        return userRoles.stream().anyMatch(required::contains);
    }

    /**
     * Usage: @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
     * Resolves doc → org, then checks membership for any role.
     */
    public boolean isDocumentMember(UUID docId, Authentication auth) {
        UUID userId = extractUserId(auth);
        if (userId == null) return false;
        return documentOrgLookup.findOrgId(docId)
            .map(orgId -> !orgRoleLookup.findRoles(orgId, userId).isEmpty())
            .orElse(false);
    }

    private UUID extractUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        if (auth.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        return null;
    }
}
