package com.root.vcsbackend.shared.redis.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope shared by all Redis messages (both inbound tasks and outbound results).
 * Mirrors the worker project's {@code MessageMetadata} contract.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageMetadata {

    private UUID correlationId;
    private Instant emittedAt;
    private String producer;

    @Builder.Default
    private Integer schemaVersion = 1;
}

