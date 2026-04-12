package com.root.vcsbackendworker.shared.messaging.inbound;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReconstructTaskMessage extends WorkerTaskMessage {

    // The version number to reconstruct. The worker resolves the snapshot base
    // and intermediate diffs from the database.
    private Integer targetVersionNumber;

}
