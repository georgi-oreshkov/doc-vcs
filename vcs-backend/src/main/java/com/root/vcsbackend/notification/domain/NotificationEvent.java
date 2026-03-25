package com.root.vcsbackend.notification.domain;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Published by any service module to trigger a notification.
 * The notification module listens for this event — no compile-time coupling required.
 */
public class NotificationEvent extends ApplicationEvent {

    private final UUID recipientId;
    private final String type;
    private final Object payload;

    public NotificationEvent(Object source, UUID recipientId, String type, Object payload) {
        super(source);
        this.recipientId = recipientId;
        this.type = type;
        this.payload = payload;
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public String getType() {
        return type;
    }

    public Object getPayload() {
        return payload;
    }
}

