package com.checador.controller;

import com.checador.entity.Notification;
import com.checador.entity.User;
import com.checador.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifService;

    /** GET /api/notifications — lista las últimas 50 notificaciones del usuario autenticado */
    @GetMapping
    public ResponseEntity<?> getAll(@AuthenticationPrincipal User user) {
        String role    = user.getRole().name();
        Long branchId  = scopedBranchId(user);
        List<Notification> list = notifService.getRecent(role, branchId);
        return ResponseEntity.ok(list.stream().map(this::toResponse).toList());
    }

    /** GET /api/notifications/unread-count — número de notificaciones sin leer */
    @GetMapping("/unread-count")
    public ResponseEntity<?> unreadCount(@AuthenticationPrincipal User user) {
        String role   = user.getRole().name();
        Long branchId = scopedBranchId(user);
        long count    = notifService.countUnread(role, branchId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /** POST /api/notifications/mark-all-read — marca todas como leídas */
    @PostMapping("/mark-all-read")
    public ResponseEntity<?> markAllRead(@AuthenticationPrincipal User user) {
        notifService.markAllRead(user.getRole().name(), scopedBranchId(user));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** PATCH /api/notifications/{id}/read — marca una notificación como leída */
    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markOneRead(@PathVariable Long id) {
        notifService.markOneRead(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    /** SUPERUSER ve todas las sucursales (branchId = null); ADMIN solo la suya */
    private Long scopedBranchId(User user) {
        if (user.getRole().name().equals("SUPERUSER")) return null;
        return user.getBranch() != null ? user.getBranch().getId() : null;
    }

    private Map<String, Object> toResponse(Notification n) {
        return Map.of(
            "id",       n.getId(),
            "type",     n.getType().name(),
            "title",    n.getTitle(),
            "body",     n.getBody(),
            "icon",     n.getIcon() != null ? n.getIcon() : "",
            "read",     n.isRead(),
            "createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : ""
        );
    }
}
