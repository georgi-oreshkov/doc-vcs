package com.root.vcsbackend.version.api;

import java.util.UUID;

public record ReviewRequestedEvent(UUID docId, UUID versionId, UUID requesterId) {}