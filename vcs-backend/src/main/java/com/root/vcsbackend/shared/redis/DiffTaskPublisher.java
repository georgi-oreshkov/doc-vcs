package com.root.vcsbackend.shared.redis;

import com.root.vcsbackend.shared.config.RedisProperties;
import com.root.vcsbackend.shared.redis.message.MessageMetadata;
import com.root.vcsbackend.shared.redis.message.ReconstructTaskMessage;
import com.root.vcsbackend.shared.redis.message.VerifyTaskMessage;
import com.root.vcsbackend.shared.redis.message.WorkerTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Publishes worker task messages (verify / reconstruct) to the Redis jobs channel.
 * <p>
 * Maintains a short-lived correlation map ({@code correlationId → userId}) so the
 * {@link DiffResultListener} can determine which user to notify when a result arrives.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiffTaskPublisher {

    private static final String PRODUCER = "vcs-backend";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final RedisProperties redisProperties;

    /**
     * Maps a task's {@code correlationId} to the userId that requested it,
     * so the result listener can resolve the SSE recipient.
     */
    private final ConcurrentHashMap<UUID, UUID> correlationToUser = new ConcurrentHashMap<>();

    /** Publishes a verify-diff task to the worker. */
    public void publishVerifyTask(VerifyTaskMessage task, UUID requestingUserId) {
        enrichMetadata(task);
        correlationToUser.put(task.getMetadata().getCorrelationId(), requestingUserId);
        publish(task);
    }

    /** Publishes a reconstruct-document task to the worker. */
    public void publishReconstructTask(ReconstructTaskMessage task, UUID requestingUserId) {
        enrichMetadata(task);
        correlationToUser.put(task.getMetadata().getCorrelationId(), requestingUserId);
        publish(task);
    }

    /**
     * Looks up the userId associated with the given correlationId and removes the entry.
     * Returns {@code null} if no mapping exists (e.g., TTL expired or unknown correlation).
     */
    public UUID resolveAndRemoveUser(UUID correlationId) {
        return correlationToUser.remove(correlationId);
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private void enrichMetadata(WorkerTaskMessage task) {
        if (task.getMetadata() == null) {
            task.setMetadata(MessageMetadata.builder().build());
        }
        MessageMetadata meta = task.getMetadata();
        if (meta.getCorrelationId() == null) {
            meta.setCorrelationId(UUID.randomUUID());
        }
        meta.setEmittedAt(Instant.now());
        meta.setProducer(PRODUCER);
    }

    private void publish(WorkerTaskMessage task) {
        try {
            String payload = jsonMapper.writeValueAsString(task);
            redisTemplate.convertAndSend(redisProperties.diffJobsChannel(), payload);
            log.debug("Published task to channel={}: {}", redisProperties.diffJobsChannel(), payload);
        } catch (Exception e) {
            log.error("Failed to serialize worker task for channel={}",
                    redisProperties.diffJobsChannel(), e);
        }
    }
}

