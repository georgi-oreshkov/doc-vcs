package com.root.vcsbackend.notification.persistence;

import com.root.vcsbackend.notification.domain.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    List<NotificationEntity> findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(UUID recipientId);

    Page<NotificationEntity> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    Page<NotificationEntity> findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);
}

