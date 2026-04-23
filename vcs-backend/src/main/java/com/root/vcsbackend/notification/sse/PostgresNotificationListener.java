package com.root.vcsbackend.notification.sse;

import com.root.vcsbackend.notification.domain.NotificationDto;
import com.root.vcsbackend.version.service.VersionService;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/**
 * Maintains a single dedicated (non-pooled) PostgreSQL connection and issues
 * {@code LISTEN vcs_notification_inserted}. When the DB trigger fires after an
 * INSERT into {@code notifications}, this listener dispatches the payload to
 * {@link SseEmitterRegistry}. All backend instances receive the broadcast;
 * only the one holding the user's SSE connection actually pushes bytes.
 *
 * <h3>Why a dedicated connection?</h3>
 * {@code LISTEN} state is bound to a connection. Pooled connections (HikariCP)
 * are returned after each use, losing the {@code LISTEN} subscription. This
 * component opens its own connection via {@link DriverManager} and holds it
 * for the entire application lifetime.
 *
 * <h3>Threading</h3>
 * Uses a virtual thread (Java 25) for the blocking poll loop so no platform
 * thread is consumed while waiting for notifications. Reconnects with
 * exponential back-off on connection loss.
 */
@Slf4j
@Component
public class PostgresNotificationListener implements InitializingBean, DisposableBean {

    private static final String CHANNEL         = "vcs_notification_inserted";
    private static final int    POLL_MS         = 5_000;   // block up to 5 s per poll cycle
    private static final long   BASE_BACKOFF_MS = 2_000;
    private static final long   MAX_BACKOFF_MS  = 30_000;

    private final SseEmitterRegistry sseEmitterRegistry;
    private final JsonMapper jsonMapper;
    private final String jdbcUrl;
    private final String dbUser;
    private final String dbPassword;

    private VersionService versionService;

    @Autowired
    public void setVersionService(@Lazy VersionService versionService) {
        this.versionService = versionService;
    }

    private volatile Connection listenConnection;
    private volatile boolean    running = true;

    public PostgresNotificationListener(
            SseEmitterRegistry sseEmitterRegistry,
            JsonMapper jsonMapper,
            @Value("${spring.datasource.url}")      String jdbcUrl,
            @Value("${spring.datasource.username}") String dbUser,
            @Value("${spring.datasource.password}") String dbPassword) {
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.jsonMapper         = jsonMapper;
        this.jdbcUrl            = jdbcUrl;
        this.dbUser             = dbUser;
        this.dbPassword         = dbPassword;
    }

    @Override
    public void afterPropertiesSet() {
        Thread.ofVirtual()
              .name("pg-listen-notifications")
              .start(this::runLoop);
    }

    // ── Main loop ──────────────────────────────────────────────────────────────

    private void runLoop() {
        long backoff = BASE_BACKOFF_MS;
        while (running) {
            try {
                connect();
                backoff = BASE_BACKOFF_MS;   // reset on successful connect
                pollLoop();
            } catch (Exception e) {
                if (!running) break;
                log.warn("pg-listen error, reconnecting in {}ms: {}", backoff, e.getMessage());
                sleep(backoff);
                backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
            } finally {
                closeQuietly();
            }
        }
    }

    private void connect() throws SQLException {
        listenConnection = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
        listenConnection.setAutoCommit(true);
        // Set the search path so the trigger function is found correctly
        listenConnection.createStatement().execute("SET search_path TO vcs_core, public");
        listenConnection.createStatement().execute("LISTEN " + CHANNEL);
        log.info("PostgreSQL LISTEN registered on channel={}", CHANNEL);
    }

    private void pollLoop() throws SQLException {
        while (running) {
            // Blocks for up to POLL_MS, then returns null — lets us check the running flag
            PGNotification[] notifications =
                    listenConnection.unwrap(PGConnection.class).getNotifications(POLL_MS);
            if (notifications != null) {
                for (PGNotification n : notifications) {
                    dispatch(n.getParameter());
                }
            }
        }
    }

    // ── Dispatch ───────────────────────────────────────────────────────────────

    private void dispatch(String json) {
        try {
            JsonNode node = jsonMapper.readTree(json);
            UUID recipientId = UUID.fromString(node.get("recipientId").asString());
            String type = node.get("type").asString();

            if ("DIFF_VERIFIED".equals(type)) {
                JsonNode payload = node.get("payload");
                if (payload != null && !payload.isNull()) {
                    UUID docId = UUID.fromString(payload.get("docId").asString());
                    int versionNumber = payload.get("version").intValue();
                    versionService.handleDiffVerified(docId, versionNumber);
                }
            }

            NotificationDto dto = new NotificationDto(
                    UUID.fromString(node.get("id").asString()),
                    recipientId,
                    type,
                    node.has("payload") && !node.get("payload").isNull()
                            ? node.get("payload").toString() : null,
                    Instant.parse(node.get("createdAt").asString()),
                    null   // always null on INSERT
            );

            sseEmitterRegistry.send(recipientId, dto);
            // → instance with connection pushes; others: ConcurrentHashMap miss → no-op

        } catch (Exception e) {
            log.error("Failed to dispatch pg notification payload={}", json, e);
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    public void destroy() {
        running = false;
        closeQuietly();
    }

    private void closeQuietly() {
        try {
            if (listenConnection != null && !listenConnection.isClosed()) {
                listenConnection.close();
            }
        } catch (SQLException ignored) {}
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}


