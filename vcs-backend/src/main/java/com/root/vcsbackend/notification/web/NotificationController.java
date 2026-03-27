package com.root.vcsbackend.notification.web;

import com.root.vcsbackend.notification.domain.NotificationDto;
import com.root.vcsbackend.notification.service.NotificationService;
import com.root.vcsbackend.notification.sse.SseEmitterRegistry;
import com.root.vcsbackend.shared.security.CurrentUser;
import com.root.vcsbackend.shared.security.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final SseEmitterRegistry registry;
    private final NotificationService notificationService;

    /**
     * Client connects once; EventSource API cannot set custom headers so this endpoint
     * is permit-all in SecurityConfig — auth is handled by the SSE registry itself.
     * All unread notifications are flushed on connect so the client starts up-to-date.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@CurrentUser JwtPrincipal principal) {
        UUID userId = principal.userId();
        // Push all unread before registering so none are missed
        notificationService.getUnread(userId)
            .forEach(n -> registry.send(userId, n));
        return registry.register(userId);
    }

    @GetMapping
    public ResponseEntity<List<NotificationDto>> list(@CurrentUser JwtPrincipal principal) {
        return ResponseEntity.ok(notificationService.getAll(principal.userId()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id,
                                         @CurrentUser JwtPrincipal principal) {
        notificationService.markRead(id, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
