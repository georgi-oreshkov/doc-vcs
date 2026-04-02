package com.root.vcsbackendworker.verify.application;

import com.root.vcsbackendworker.shared.messaging.VerifyTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class VerifyDiffUseCase {

    public void handle(VerifyTaskMessage task) {
        log.info("Verify task received: docId={}, versionId={}, latestVersionS3Key={}, diffS3Key={}",
                task.getDocId(),
                task.getVersionId(),
                task.getLatestVersionS3Key(),
                task.getDiffS3Key());
    }
}

