package com.root.vcsbackendworker.messaging.application;

import com.root.vcsbackendworker.messaging.contract.inbound.DiffJobMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DiffJobHandler {

    public void handle(DiffJobMessage job) {
        // Placeholder orchestration point for the future diff verify/promote pipeline.
        log.info("Received diff job: docId={}, versionId={}, oldS3Key={}, diffS3Key={}",
                job.getDocId(),
                job.getVersionId(),
                job.getOldS3Key(),
                job.getDiffS3Key());
    }
}

