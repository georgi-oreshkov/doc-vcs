package com.root.vcsbackend.notification.domain;

/**
 * @deprecated Moved to {@link com.root.vcsbackend.notification.api.NotificationEvent}.
 * This alias exists only for backward compatibility — prefer the api package import.
 */
@Deprecated
public class NotificationEvent extends com.root.vcsbackend.notification.api.NotificationEvent {

    public NotificationEvent(Object source, java.util.UUID recipientId, String type, Object payload) {
        super(source, recipientId, type, payload);
    }
}
