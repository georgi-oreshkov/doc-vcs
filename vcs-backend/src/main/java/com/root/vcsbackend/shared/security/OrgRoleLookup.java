package com.root.vcsbackend.shared.security;

import java.util.List;
import java.util.UUID;

/**
 * Abstraction that lets OrgRoleEvaluator (shared) ask the organization module
 * about membership without creating a direct compile-time dependency.
 * Implemented by organization.OrgRoleLookupAdapter.
 */
public interface OrgRoleLookup {

    /** Returns the role names (e.g. ["ADMIN", "REVIEWER"]) for the given user in the given org. Empty list means no membership. */
    List<String> findRoles(UUID orgId, UUID userId);
}

