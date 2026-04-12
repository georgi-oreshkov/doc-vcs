-- V6: PostgreSQL LISTEN/NOTIFY trigger for real-time SSE delivery.
--
-- After each INSERT into notifications, broadcast the new row as JSON
-- on the 'vcs_notification_inserted' channel so all backend instances
-- can attempt to push it via SSE. Only the instance holding the
-- user's SseEmitter connection will actually deliver bytes; others
-- silently no-op (empty emitter list).
--
-- pg_notify fires only AFTER the transaction commits, so the row is
-- durable before any backend instance reacts.
--
-- Payload is well under the 8 KB pg_notify limit (a few UUIDs + small JSONB).

CREATE OR REPLACE FUNCTION vcs_core.notify_new_notification()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM pg_notify(
        'vcs_notification_inserted',
        json_build_object(
            'id',          NEW.id,
            'recipientId', NEW.recipient_id,
            'type',        NEW.type,
            'payload',     NEW.payload,
            'createdAt',   NEW.created_at
            -- readAt intentionally omitted: always null on INSERT
        )::text
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_notifications_notify_insert
    AFTER INSERT ON vcs_core.notifications
    FOR EACH ROW EXECUTE FUNCTION vcs_core.notify_new_notification();

