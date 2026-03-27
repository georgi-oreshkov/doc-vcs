package com.root.vcsbackend.shared.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Abstraction that lets OrgRoleEvaluator (shared) resolve which org owns a document,
 * without creating a compile-time dependency on the document module.
 * Implemented by document.DocumentOrgLookupAdapter.
 */
public interface DocumentOrgLookup {

    /** Returns the orgId of the given document, or empty if not found. */
    Optional<UUID> findOrgId(UUID docId);
}

