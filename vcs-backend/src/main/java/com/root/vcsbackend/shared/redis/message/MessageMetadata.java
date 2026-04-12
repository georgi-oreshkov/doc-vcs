package com.root.vcsbackend.shared.redis.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Envelope shared by all worker task messages published to Redis.
 * Mirrors the worker project's {@code MessageMetadata} contract.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageMetadata {

    private Instant emittedAt;
    private String producer;

    @Builder.Default
    private Integer schemaVersion = 1;
}

