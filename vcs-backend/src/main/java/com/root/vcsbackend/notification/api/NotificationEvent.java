package com.root.vcsbackend.notification.api;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Published by any service module to trigger a notification.
 * Lives in notification.api so it is exposed as a NamedInterface and
 * can be imported by any module without violating Modulith boundaries.
 * The notification module's @EventListener handles persistence + SSE push.
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

    public UUID getRecipientId() { return recipientId; }
    public String getType()      { return type; }
    public Object getPayload()   { return payload; }
}

