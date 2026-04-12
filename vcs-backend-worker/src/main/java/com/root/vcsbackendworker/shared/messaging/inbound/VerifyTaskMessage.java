package com.root.vcsbackendworker.shared.messaging.inbound;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class VerifyTaskMessage extends WorkerTaskMessage {
    private Integer newVersionNumber;
}
