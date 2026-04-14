package com.root.vcsbackend.notification.service;

import com.root.vcsbackend.notification.domain.NotificationDto;
import com.root.vcsbackend.notification.domain.NotificationEntity;
import com.root.vcsbackend.notification.api.NotificationEvent;
import com.root.vcsbackend.notification.persistence.NotificationRepository;
import com.root.vcsbackend.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Listens to ApplicationEvents from any module.
     * Persists the notification — SSE delivery is handled by
     * {@link com.root.vcsbackend.notification.sse.PostgresNotificationListener}
     * which reacts to the {@code pg_notify} trigger that fires after this
     * transaction commits.
     */
    @EventListener
    public void onNotificationEvent(NotificationEvent event) {
        notificationRepository.save(NotificationEntity.builder()
            .recipientId(event.getRecipientId())
            .type(event.getType())
            .payload(event.getPayload() != null ? event.getPayload().toString() : null)
            .build());
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getAll(UUID recipientId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId)
            .stream()
            .map(NotificationDto::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getUnread(UUID recipientId) {
        return notificationRepository.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(recipientId)
            .stream()
            .map(NotificationDto::from)
            .collect(Collectors.toList());
    }

    /** Paginated variant of {@link #getAll} for the REST endpoint. */
    @Transactional(readOnly = true)
    public Page<NotificationDto> getAllPaged(UUID recipientId, int page, int size) {
        return notificationRepository
            .findByRecipientIdOrderByCreatedAtDesc(recipientId, PageRequest.of(page, size))
            .map(NotificationDto::from);
    }

    /** Paginated variant of {@link #getUnread} for the REST endpoint. */
    @Transactional(readOnly = true)
    public Page<NotificationDto> getUnreadPaged(UUID recipientId, int page, int size) {
        return notificationRepository
            .findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(recipientId, PageRequest.of(page, size))
            .map(NotificationDto::from);
    }

    public void markRead(UUID notificationId, UUID callerId) {
        NotificationEntity entity = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                "Notification not found: " + notificationId));
        if (!entity.getRecipientId().equals(callerId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not your notification");
        }
        entity.setReadAt(Instant.now());
        // entity is managed — change is flushed automatically at tx commit
    }
}
