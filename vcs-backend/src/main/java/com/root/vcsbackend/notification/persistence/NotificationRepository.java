package com.root.vcsbackend.notification.persistence;

import com.root.vcsbackend.notification.domain.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    List<NotificationEntity> findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(UUID recipientId);
}

