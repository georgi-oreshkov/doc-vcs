package com.root.vcsbackendworker.shared.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

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


