package com.root.vcsbackendworker.shared.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.root.vcsbackendworker.shared.messaging.FailureReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes task outcomes directly to the {@code vcs_core.notifications} table.
 * <p>
 * The PostgreSQL {@code AFTER INSERT} trigger on {@code notifications} fires
 * {@code pg_notify('vcs_notification_inserted', ...)} which the backend's
 * {@code PostgresNotificationListener} picks up and pushes via SSE.
 * <p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class NotificationWriteGateway {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    // ── Notification type constants ─────────────────────────────────────────

    private static final String TYPE_DIFF_VERIFIED = "DIFF_VERIFIED";
    private static final String TYPE_DOCUMENT_RECONSTRUCTED = "DOCUMENT_RECONSTRUCTED";
    private static final String TYPE_WORKER_TASK_FAILED = "WORKER_TASK_FAILED";

    // ── Success outcomes ────────────────────────────────────────────────────

    /**
     * Records a successful diff verification:
     * inserts a notification.
     */
    @Transactional
    public void recordVerificationSuccess(UUID recipientId, UUID docId, UUID versionId, Integer version) {

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("docId", docId.toString());
        payload.put("version", version);

        insertNotification(recipientId, TYPE_DIFF_VERIFIED, payload);

        log.debug("Recorded verification success: docId={}, versionId={}", docId, versionId);
    }

    /**
     * Records a successful document reconstruction with a presigned download URL.
     */
    @Transactional
    public void recordReconstructionSuccess(UUID recipientId, UUID docId, UUID versionId, String presignedDownloadUrl) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("docId", docId.toString());
        payload.put("versionId", versionId.toString());
        payload.put("presignedDownloadUrl", presignedDownloadUrl);

        insertNotification(recipientId, TYPE_DOCUMENT_RECONSTRUCTED, payload);

        log.debug("Recorded reconstruction success: docId={}, versionId={}", docId, versionId);
    }

    // ── Failure outcome ─────────────────────────────────────────────────────

    /**
     * Records a task failure as a notification so the user is informed via SSE.
     */
    @Transactional
    public void recordFailure(UUID recipientId, UUID docId, UUID versionId,
                              String taskType, FailureReason failureReason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("docId", docId.toString());
        payload.put("versionId", versionId.toString());
        payload.put("taskType", taskType);
        payload.put("failureReason", failureReason.name());

        insertNotification(recipientId, TYPE_WORKER_TASK_FAILED, payload);

        log.debug("Recorded task failure: docId={}, versionId={}, reason={}", docId, versionId, failureReason);
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private void insertNotification(UUID recipientId, String type, Map<String, Object> payload) {
        jdbcClient.sql("""
                INSERT INTO vcs_core.notifications (recipient_id, type, payload)
                VALUES (:recipientId, :type, CAST(:payload AS jsonb))
                """)
                .param("recipientId", recipientId)
                .param("type", type)
                .param("payload", toJson(payload))
                .update();
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize notification payload", e);
        }
    }
}

