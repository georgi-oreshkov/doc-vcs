package com.root.vcsbackend.shared.redis;

import com.root.vcsbackend.shared.config.RedisProperties;
import com.root.vcsbackend.shared.redis.message.MessageMetadata;
import com.root.vcsbackend.shared.redis.message.WorkerTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;

/**
 * Publishes worker task messages to a <b>Redis Stream</b> via {@code XADD}.
 * <p>
 * The worker reads from this stream using {@code XREADGROUP} with a consumer
 * group, which guarantees each message is delivered to <b>exactly one</b>
 * worker instance — no duplicate processing even with multiple workers.
 * <p>
 * Fire-and-forget from the backend's perspective: the worker owns the outcome
 * and writes results directly to PostgreSQL ({@code versions} checksum update,
 * {@code notifications} INSERT). The backend learns about completed tasks via
 * the {@code pg_notify} trigger on the {@code notifications} table
 * (see {@code PostgresNotificationListener}).
 * <p>
 * {@code recipientId} is set on the task message by the caller so the worker
 * knows who to notify; no correlation cache is needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiffTaskPublisher {

    private static final String PRODUCER = "vcs-backend";

    /** Field name under which the JSON payload is stored in the stream entry. */
    private static final String PAYLOAD_FIELD = "payload";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final RedisProperties redisProperties;

    /**
     * Publishes a worker task message to the Redis jobs stream via {@code XADD}.
     * The task's {@link WorkerTaskMessage#getRecipientId()} must be set by the caller.
     */
    public void publish(WorkerTaskMessage task) {
        enrichMetadata(task);
        try {
            String json = jsonMapper.writeValueAsString(task);
            RecordId recordId = redisTemplate.opsForStream().add(
                    StreamRecords.string(Map.of(PAYLOAD_FIELD, json))
                            .withStreamKey(redisProperties.diffJobsStream()));
            log.debug("Published task to stream={}, id={}: type={}, docId={}, versionId={}",
                    redisProperties.diffJobsStream(), recordId,
                    task.getTaskType(), task.getDocId(), task.getVersionId());
        } catch (Exception e) {
            log.error("Failed to publish task to stream={}: type={}, docId={}",
                    redisProperties.diffJobsStream(), task.getTaskType(), task.getDocId(), e);
        }
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private void enrichMetadata(WorkerTaskMessage task) {
        if (task.getMetadata() == null) {
            task.setMetadata(MessageMetadata.builder().build());
        }
        MessageMetadata meta = task.getMetadata();
        meta.setEmittedAt(Instant.now());
        meta.setProducer(PRODUCER);
    }
}
