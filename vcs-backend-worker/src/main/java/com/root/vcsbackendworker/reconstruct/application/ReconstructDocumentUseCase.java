package com.root.vcsbackendworker.reconstruct.application;

import com.root.vcsbackendworker.shared.messaging.ReconstructTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ReconstructDocumentUseCase {

    public void handle(ReconstructTaskMessage task) {
        log.info("Reconstruct task received: docId={}, versionId={}, targetVersionNumber={}",
                task.getDocId(),
                task.getVersionId(),
                task.getTargetVersionNumber());
    }
}

