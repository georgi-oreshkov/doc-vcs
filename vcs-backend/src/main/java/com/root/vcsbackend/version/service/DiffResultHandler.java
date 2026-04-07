package com.root.vcsbackend.version.service;

import com.root.vcsbackend.document.api.DocumentFacade;
import com.root.vcsbackend.notification.api.NotificationEvent;
import com.root.vcsbackend.shared.redis.DiffResultEvent;
import com.root.vcsbackend.shared.redis.message.ProcessingStatus;
import com.root.vcsbackend.version.domain.VersionEntity;
import com.root.vcsbackend.version.persistence.VersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles {@link DiffResultEvent} fired by the shared Redis result listener.
 * <p>
 * Depending on the result type:
 * <ul>
 *   <li><b>VERIFY</b> — updates the version's checksum on success; notifies the
 *       requesting user (or document author) of the outcome.</li>
 *   <li><b>RECONSTRUCT</b> — pushes the presigned download URL to the requesting
 *       user via SSE so the frontend can start downloading immediately.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiffResultHandler {

    private final VersionRepository versionRepository;
    private final DocumentFacade documentFacade;
    private final ApplicationEventPublisher events;

    @EventListener
    @Transactional
    public void onDiffResult(DiffResultEvent event) {
        switch (event.getResultType()) {
            case VERIFY      -> handleVerification(event);
            case RECONSTRUCT -> handleReconstruction(event);
        }
    }

    // ── Verification ────────────────────────────────────────────────────────

    private void handleVerification(DiffResultEvent event) {
        UUID recipientId = resolveRecipient(event);

        if (event.getStatus() == ProcessingStatus.SUCCEEDED) {
            // Persist the verified checksum on the version entity
            Optional<VersionEntity> versionOpt = versionRepository.findById(event.getVersionId());
            versionOpt.ifPresent(v -> {
                v.setChecksum(event.getActualChecksum());
                versionRepository.save(v);
                log.info("Version {} checksum updated after successful verification", v.getId());
            });

            events.publishEvent(new NotificationEvent(
                    this, recipientId, "DIFF_VERIFIED",
                    buildPayload(event, Map.of("actualChecksum", nullSafe(event.getActualChecksum())))
            ));
        } else {
            log.warn("Diff verification failed: docId={}, versionId={}, reason={}",
                    event.getDocId(), event.getVersionId(), event.getFailureReason());

            events.publishEvent(new NotificationEvent(
                    this, recipientId, "DIFF_VERIFICATION_FAILED",
                    buildPayload(event, Map.of("failureReason", String.valueOf(event.getFailureReason())))
            ));
        }
    }

    // ── Reconstruction ──────────────────────────────────────────────────────

    private void handleReconstruction(DiffResultEvent event) {
        UUID recipientId = resolveRecipient(event);

        if (event.getStatus() == ProcessingStatus.SUCCEEDED) {
            events.publishEvent(new NotificationEvent(
                    this, recipientId, "DOCUMENT_RECONSTRUCTED",
                    buildPayload(event, Map.of(
                            "presignedDownloadUrl", nullSafe(event.getPresignedDownloadUrl())
                    ))
            ));
        } else {
            log.warn("Document reconstruction failed: docId={}, versionId={}, reason={}",
                    event.getDocId(), event.getVersionId(), event.getFailureReason());

            events.publishEvent(new NotificationEvent(
                    this, recipientId, "DOCUMENT_RECONSTRUCTION_FAILED",
                    buildPayload(event, Map.of("failureReason", String.valueOf(event.getFailureReason())))
            ));
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Determines the SSE recipient: prefers the requesting user stored in the
     * correlation cache; falls back to the document author.
     */
    private UUID resolveRecipient(DiffResultEvent event) {
        if (event.getRequestingUserId() != null) {
            return event.getRequestingUserId();
        }
        return documentFacade.getAuthorId(event.getDocId());
    }

    private Map<String, Object> buildPayload(DiffResultEvent event, Map<String, String> extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("docId", event.getDocId());
        payload.put("versionId", event.getVersionId());
        payload.put("status", event.getStatus().name());
        payload.putAll(extra);
        return payload;
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}

