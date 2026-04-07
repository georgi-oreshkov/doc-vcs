package com.root.vcsbackend.shared.redis;

import com.root.vcsbackend.shared.redis.message.ReconstructionResultMessage;
import com.root.vcsbackend.shared.redis.message.VerificationResultMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Listens on the Redis results channel for messages published by the
 * {@code vcs-backend-worker}. Deserializes the JSON payload, determines
 * whether it is a verification or reconstruction result, resolves the
 * requesting user from the {@link DiffTaskPublisher}'s correlation cache,
 * and fires a {@link DiffResultEvent} so the version module can handle it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiffResultListener implements MessageListener {

    private final JsonMapper jsonMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final DiffTaskPublisher diffTaskPublisher;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String payload = StringRedisSerializer.UTF_8.deserialize(message.getBody());

        log.debug("Received result on channel={}: {}", channel, payload);

        try {
            JsonNode root = jsonMapper.readTree(payload);

            // Discriminate by the presence of type-specific fields:
            //   - "presignedDownloadUrl" → ReconstructionResultMessage
            //   - "actualChecksum"       → VerificationResultMessage
            if (root.has("presignedDownloadUrl")) {
                handleReconstructionResult(
                        jsonMapper.treeToValue(root, ReconstructionResultMessage.class));
            } else {
                handleVerificationResult(
                        jsonMapper.treeToValue(root, VerificationResultMessage.class));
            }
        } catch (Exception ex) {
            log.error("Failed to parse/process worker result on channel={}. Payload={}",
                    channel, payload, ex);
        }
    }

    private void handleVerificationResult(VerificationResultMessage result) {
        UUID correlationId = result.getMetadata() != null
                ? result.getMetadata().getCorrelationId() : null;
        UUID userId = resolveUser(correlationId, result.getDocId());

        eventPublisher.publishEvent(new DiffResultEvent(
                this,
                DiffResultEvent.ResultType.VERIFY,
                result.getDocId(),
                result.getVersionId(),
                correlationId,
                userId,
                result.getStatus(),
                result.getFailureReason(),
                null,
                result.getActualChecksum()
        ));
    }

    private void handleReconstructionResult(ReconstructionResultMessage result) {
        UUID correlationId = result.getMetadata() != null
                ? result.getMetadata().getCorrelationId() : null;
        UUID userId = resolveUser(correlationId, result.getDocId());

        eventPublisher.publishEvent(new DiffResultEvent(
                this,
                DiffResultEvent.ResultType.RECONSTRUCT,
                result.getDocId(),
                result.getVersionId(),
                correlationId,
                userId,
                result.getStatus(),
                result.getFailureReason(),
                result.getPresignedDownloadUrl(),
                null
        ));
    }

    /**
     * Resolves the requesting user from the correlation cache.
     * Returns {@code null} if the correlation has expired or was never recorded
     * (the handler should fall back to the document author in that case).
     */
    private UUID resolveUser(UUID correlationId, UUID docId) {
        if (correlationId != null) {
            UUID userId = diffTaskPublisher.resolveAndRemoveUser(correlationId);
            if (userId != null) {
                return userId;
            }
            log.warn("No correlation mapping for correlationId={}, docId={}", correlationId, docId);
        }
        return null;
    }
}
