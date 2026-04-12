package com.root.vcsbackendworker.shared.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.root.vcsbackendworker.shared.config.WorkerRedisProperties;
import com.root.vcsbackendworker.shared.messaging.inbound.WorkerTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

/**
 * Consumes task messages from the Redis Stream via {@code XREADGROUP}.
 * <p>
 * Each stream record contains a single field {@code "payload"} whose value is the
 * JSON-serialized {@link WorkerTaskMessage}. After the dispatcher processes the task
 * (regardless of business-level success or failure), the record is acknowledged
 * via {@code XACK} so it leaves the consumer's pending entries list.
 * <p>
 * Only genuinely unprocessed messages (worker crash, OOM, etc.) remain unacked
 * and are reclaimable via {@code XAUTOCLAIM}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkerTaskStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private static final String PAYLOAD_FIELD = "payload";

    private final ObjectMapper objectMapper;
    private final WorkerTaskDispatcher workerTaskDispatcher;
    private final StringRedisTemplate redisTemplate;
    private final WorkerRedisProperties workerRedisProperties;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String payload = message.getValue().get(PAYLOAD_FIELD);

        try {
            WorkerTaskMessage task = objectMapper.readValue(payload, WorkerTaskMessage.class);
            workerTaskDispatcher.dispatch(task);
        } catch (Exception ex) {
            log.error("Failed to process stream message id={}: {}", message.getId(), ex.getMessage(), ex);
        } finally {
            // ACK regardless — the task was processed (or is unparseable and should not retry)
            redisTemplate.opsForStream().acknowledge(
                    workerRedisProperties.stream(),
                    workerRedisProperties.consumerGroup(),
                    message.getId());
        }
    }
}

