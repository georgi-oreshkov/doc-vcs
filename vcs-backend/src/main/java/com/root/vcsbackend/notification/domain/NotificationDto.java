package com.root.vcsbackend.notification.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Internal DTO used to transfer notification data from NotificationService
 * to NotificationController and to SseEmitterRegistry.
 * Not a JPA entity — never persisted directly.
 */
public record NotificationDto(
    UUID id,
    UUID recipientId,
    String type,
    String payload,
    Instant createdAt,
    Instant readAt
) {
    public static NotificationDto from(NotificationEntity entity) {
        return new NotificationDto(
            entity.getId(),
            entity.getRecipientId(),
            entity.getType(),
            entity.getPayload(),
            entity.getCreatedAt(),
            entity.getReadAt()
        );
    }

    public boolean isUnread() {
        return readAt == null;
    }
}

