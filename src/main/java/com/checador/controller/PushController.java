package com.checador.controller;

import com.checador.entity.User;
import com.checador.service.WebPushService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final WebPushService webPushService;

    @Data
    public static class PushSubRequest {
        private String endpoint;
        private Keys keys;

        @Data
        public static class Keys {
            private String p256dh;
            private String auth;
        }
    }

    @GetMapping("/public-key")
    public ResponseEntity<Map<String, String>> getPublicKey() {
        return ResponseEntity.ok(Map.of("publicKey", webPushService.getPublicKey()));
    }

    @PostMapping("/subscribe")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> subscribe(
            @AuthenticationPrincipal User user,
            @RequestBody PushSubRequest req
    ) {
        String p256dh = req.getKeys() != null ? req.getKeys().getP256dh() : null;
        String auth = req.getKeys() != null ? req.getKeys().getAuth() : null;

        webPushService.saveSubscription(user, req.getEndpoint(), p256dh, auth);
        return ResponseEntity.ok(Map.of("message", "Suscripción Push registrada exitosamente."));
    }

    @PostMapping("/unsubscribe")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> unsubscribe(@RequestBody Map<String, String> body) {
        String endpoint = body.get("endpoint");
        if (endpoint != null) {
            webPushService.removeSubscription(endpoint);
        }
        return ResponseEntity.ok(Map.of("message", "Suscripción Push eliminada."));
    }

    @PostMapping("/test")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> sendTestNotification(@AuthenticationPrincipal User user) {
        if (user != null) {
            webPushService.sendPushToUser(
                    user.getId(),
                    "🔔 Prueba Vía Gourmet",
                    "¡Las notificaciones Push en segundo plano para " + user.getFullName() + " están funcionando al 100%!",
                    "/logo.png",
                    "/attendance"
            );
        } else {
            webPushService.sendPushToAll(
                    "🔔 Prueba Vía Gourmet",
                    "Notificación Push global de prueba enviada exitosamente.",
                    "/logo.png",
                    "/attendance"
            );
        }
        return ResponseEntity.ok(Map.of("message", "Notificación Push de prueba despachada."));
    }
}
