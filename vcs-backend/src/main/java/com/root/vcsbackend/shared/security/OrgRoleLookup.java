package com.root.vcsbackend.shared.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Abstraction that lets OrgRoleEvaluator (shared) ask the organization module
 * about membership without creating a direct compile-time dependency.
 * Implemented by organization.OrgRoleLookupAdapter.
 */
public interface OrgRoleLookup {

    /** Returns the role name (e.g. "ADMIN") for the given user in the given org, or empty. */
    Optional<String> findRole(UUID orgId, UUID userId);
}

