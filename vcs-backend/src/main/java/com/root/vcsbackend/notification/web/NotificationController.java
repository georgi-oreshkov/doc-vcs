package com.root.vcsbackend.notification.web;

import com.root.vcsbackend.notification.domain.NotificationDto;
import com.root.vcsbackend.notification.service.NotificationService;
import com.root.vcsbackend.notification.sse.SseEmitterRegistry;
import com.root.vcsbackend.shared.security.CurrentUser;
import com.root.vcsbackend.shared.security.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * Notification endpoints — intentionally hand-written rather than generated from the OpenAPI spec.
 *
 * <p>The SSE streaming endpoint ({@code GET /notifications/stream}) cannot be expressed cleanly in
 * OpenAPI 3 (no standard SSE response type, and the {@code EventSource} API forbids custom
 * request headers which breaks normal JWT auth flow). The REST endpoints
 * ({@code GET /notifications} and {@code POST /notifications/{id}/read}) are kept here so
 * that all notification-related routes live in a single controller, but they could be added to
 * the spec in a future iteration to enable client codegen consistency.
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final SseEmitterRegistry registry;
    private final NotificationService notificationService;

    /**
     * Client connects once per tab; EventSource API cannot set custom headers so this
     * endpoint is permit-all in SecurityConfig — auth is handled via the JWT principal.
     * <p>
     * The emitter is registered first, then all unread notifications are flushed so
     * the client starts up-to-date and no events are lost between register and flush.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@CurrentUser JwtPrincipal principal) {
        UUID userId = principal.userId();
        SseEmitter emitter = registry.register(userId);
        // Flush unread after registering so they reach this emitter
        notificationService.getUnread(userId)
            .forEach(n -> registry.send(userId, n));
        return emitter;
    }

    /**
     * Returns a paginated list of notifications for the current user.
     *
     * @param page       zero-based page index (default 0)
     * @param size       page size (default 20)
     * @param unreadOnly if {@code true}, only unread notifications are returned
     */
    @GetMapping
    public ResponseEntity<Page<NotificationDto>> list(
            @CurrentUser JwtPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        UUID userId = principal.userId();
        Page<NotificationDto> result = unreadOnly
                ? notificationService.getUnreadPaged(userId, page, size)
                : notificationService.getAllPaged(userId, page, size);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id,
                                         @CurrentUser JwtPrincipal principal) {
        notificationService.markRead(id, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
