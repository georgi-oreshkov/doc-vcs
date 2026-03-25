package com.root.vcsbackend.notification.service;

import com.root.vcsbackend.notification.domain.NotificationEntity;
import com.root.vcsbackend.notification.domain.NotificationEvent;
import com.root.vcsbackend.notification.persistence.NotificationRepository;
import com.root.vcsbackend.notification.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SseEmitterRegistry sseEmitterRegistry;

    @EventListener
    public void onNotificationEvent(NotificationEvent event) {
        // TODO:
        // 1. Persist NotificationEntity
        // 2. Map to DTO
        // 3. sseEmitterRegistry.send(event.getRecipientId(), dto)
    }

    @Transactional(readOnly = true)
    public List<NotificationEntity> getAll(UUID recipientId) {
        // TODO: implement + map to DTOs
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
    }

    @Transactional(readOnly = true)
    public List<NotificationEntity> getUnread(UUID recipientId) {
        // TODO: implement + map to DTOs
        return notificationRepository.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(recipientId);
    }

    public void markRead(UUID notificationId, UUID callerId) {
        // TODO: load notification, verify callerId == recipientId, set readAt = Instant.now()
    }
}

