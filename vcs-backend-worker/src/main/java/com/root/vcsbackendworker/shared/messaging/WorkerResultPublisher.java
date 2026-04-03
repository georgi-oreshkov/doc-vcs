package com.root.vcsbackendworker.shared.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.root.vcsbackendworker.shared.config.WorkerRedisProperties;
import com.root.vcsbackendworker.shared.messaging.inbound.WorkerTaskMessage;
import com.root.vcsbackendworker.shared.messaging.outbound.ReconstructionResultMessage;
import com.root.vcsbackendworker.shared.messaging.outbound.VerificationResultMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkerResultPublisher {

    private static final String PRODUCER = "vcs-backend-worker";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final WorkerRedisProperties workerRedisProperties;

    public void publish(VerificationResultMessage result) {
        publish(result, workerRedisProperties.resultChannel());
    }

    public void publish(ReconstructionResultMessage result) {
        publish(result, workerRedisProperties.resultChannel());
    }

    /**
     * Builds outbound {@link MessageMetadata}, preserving the {@code correlationId}
     * from the originating inbound task for end-to-end traceability.
     */
    public MessageMetadata buildMetadata(WorkerTaskMessage task) {
        return MessageMetadata.builder()
                .correlationId(task.getMetadata() != null ? task.getMetadata().getCorrelationId() : null)
                .emittedAt(Instant.now())
                .producer(PRODUCER)
                .build();
    }

    private void publish(Object result, String channel) {
        try {
            String payload = objectMapper.writeValueAsString(result);
            redisTemplate.convertAndSend(channel, payload);
            log.debug("Published result to channel={}: {}", channel, payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize result for channel={}", channel, e);
        }
    }
}

