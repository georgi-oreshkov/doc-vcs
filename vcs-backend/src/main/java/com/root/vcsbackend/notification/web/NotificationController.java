package com.root.vcsbackend.notification.web;

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
     * Client connects once; token passed as query param because
     * the browser EventSource API cannot set custom headers.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@CurrentUser JwtPrincipal principal) {
        // TODO: send all unread notifications on connect, then register emitter
        return null;
    }

    @GetMapping
    public ResponseEntity<List<?>> list(@CurrentUser JwtPrincipal principal) {
        // TODO: return all notifications for current user (mapped to DTO)
        return null;
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id,
                                         @CurrentUser JwtPrincipal principal) {
        // TODO: notificationService.markRead(id, principal.getUserId())
        return ResponseEntity.noContent().build();
    }
}

