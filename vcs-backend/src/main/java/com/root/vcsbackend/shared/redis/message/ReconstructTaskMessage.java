package com.root.vcsbackend.shared.redis.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Task message sent to the worker to reconstruct a full document at a given
 * version number by fetching the nearest snapshot and applying intermediate diffs.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReconstructTaskMessage extends WorkerTaskMessage {

    /** The version number to reconstruct. */
    private Integer targetVersionNumber;
}

