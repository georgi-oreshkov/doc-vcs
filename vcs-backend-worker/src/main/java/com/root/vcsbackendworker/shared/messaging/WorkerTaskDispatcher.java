package com.root.vcsbackendworker.shared.messaging;

import com.root.vcsbackendworker.reconstruct.application.ReconstructDocumentUseCase;
import com.root.vcsbackendworker.shared.messaging.inbound.ReconstructTaskMessage;
import com.root.vcsbackendworker.shared.messaging.inbound.VerifyTaskMessage;
import com.root.vcsbackendworker.shared.messaging.inbound.WorkerTaskMessage;
import com.root.vcsbackendworker.verify.application.VerifyDiffUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerTaskDispatcher {

    private final VerifyDiffUseCase verifyDiffUseCase;
    private final ReconstructDocumentUseCase reconstructDocumentUseCase;

    public void dispatch(WorkerTaskMessage job) {
        switch (job) {
            case VerifyTaskMessage verify -> verifyDiffUseCase.handle(verify);
            case ReconstructTaskMessage reconstruct -> reconstructDocumentUseCase.handle(reconstruct);
            default -> log.error("Unknown task type: {}", job.getTaskType());
        }
    }
}

