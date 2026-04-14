package com.root.vcsbackend.shared.redis;

import com.root.vcsbackend.shared.redis.message.FailureReason;
import com.root.vcsbackend.shared.redis.message.ProcessingStatus;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Published internally (via {@link org.springframework.context.ApplicationEventPublisher})
 * when the {@link DiffResultListener} receives a result from the worker on the Redis
 * results channel.
 * <p>
 * Lives in {@code shared} so that any module can listen for it without a
 * compile-time dependency on the listener itself.
 */
@Getter
public class DiffResultEvent extends ApplicationEvent {

    public enum ResultType { VERIFY, RECONSTRUCT }

    private final ResultType resultType;
    private final UUID docId;
    private final UUID versionId;
    private final UUID correlationId;
    private final UUID requestingUserId;
    private final ProcessingStatus status;
    private final FailureReason failureReason;

    /** Only present for RECONSTRUCT results on success. */
    private final String presignedDownloadUrl;

    /** Only present for VERIFY results. */
    private final String actualChecksum;

    public DiffResultEvent(Object source,
                           ResultType resultType,
                           UUID docId,
                           UUID versionId,
                           UUID correlationId,
                           UUID requestingUserId,
                           ProcessingStatus status,
                           FailureReason failureReason,
                           String presignedDownloadUrl,
                           String actualChecksum) {
        super(source);
        this.resultType = resultType;
        this.docId = docId;
        this.versionId = versionId;
        this.correlationId = correlationId;
        this.requestingUserId = requestingUserId;
        this.status = status;
        this.failureReason = failureReason;
        this.presignedDownloadUrl = presignedDownloadUrl;
        this.actualChecksum = actualChecksum;
    }

}

