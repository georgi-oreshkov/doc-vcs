package com.root.vcsbackendworker.workerjob.application;

import com.root.vcsbackendworker.reconstruct.application.ReconstructDocumentUseCase;
import com.root.vcsbackendworker.verify.application.VerifyDiffUseCase;
import com.root.vcsbackendworker.workerjob.api.inbound.WorkerTaskMessage;
import com.root.vcsbackendworker.workerjob.api.inbound.WorkerTaskType;
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
        WorkerTaskType taskType = job.getTaskType();
        if (taskType == WorkerTaskType.RECONSTRUCT_DOCUMENT) {
            reconstructDocumentUseCase.handle(job);
            return;
        }

        verifyDiffUseCase.handle(job);
    }
}


