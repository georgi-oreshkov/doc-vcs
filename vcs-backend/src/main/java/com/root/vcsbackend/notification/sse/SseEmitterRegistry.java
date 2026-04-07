package com.root.vcsbackend.notification.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry of active SSE connections keyed by userId.
 * <p>
 * Supports multiple emitters per user (one per browser tab).
 * Sends a heartbeat comment every 15 seconds to prevent proxies
 * and load balancers from dropping idle connections.
 */
@Slf4j
@Component
@EnableScheduling
public class SseEmitterRegistry {

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters =
            new ConcurrentHashMap<>();

    /**
     * Creates and registers a new SSE emitter for the given user.
     * Multiple emitters per user are supported (one per browser tab).
     */
    public SseEmitter register(UUID userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable removeCallback = () -> {
            CopyOnWriteArrayList<SseEmitter> list = emitters.get(userId);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) {
                    emitters.remove(userId, list);
                }
            }
        };

        emitter.onCompletion(removeCallback);
        emitter.onTimeout(removeCallback);
        emitter.onError(e -> {
            log.debug("SSE emitter error for userId={}: {}", userId, e.getMessage());
            removeCallback.run();
        });

        return emitter;
    }

    /**
     * Sends an event to all emitters registered for the given user.
     * Failed emitters are removed automatically via the error callback.
     */
    public void send(UUID userId, Object payload) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().data(payload));
            } catch (IOException e) {
                log.debug("SSE send failed for userId={}, removing emitter: {}", userId, e.getMessage());
                list.remove(emitter);
                if (list.isEmpty()) {
                    emitters.remove(userId, list);
                }
            }
        }
    }

    /**
     * Removes all emitters for the given user (e.g., on logout).
     */
    public void remove(UUID userId) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.remove(userId);
        if (list != null) {
            list.forEach(SseEmitter::complete);
        }
    }

    /**
     * Sends a heartbeat comment to all registered emitters every 15 seconds.
     * SSE comment events ({@code : keepalive\n\n}) are invisible to the client's
     * {@code EventSource.onmessage} handler but keep the TCP connection alive
     * through proxies and load balancers that drop idle connections.
     */
    @Scheduled(fixedRate = 15_000)
    void heartbeat() {
        emitters.forEach((userId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("keepalive"));
                } catch (IOException e) {
                    log.debug("SSE heartbeat failed for userId={}, removing emitter", userId);
                    list.remove(emitter);
                    if (list.isEmpty()) {
                        emitters.remove(userId, list);
                    }
                }
            }
        });
    }
}
