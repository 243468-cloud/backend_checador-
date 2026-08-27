package com.checador.service;

import com.checador.entity.PushSubscriptionEntity;
import com.checador.entity.User;
import com.checador.repository.PushSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebPushService {

    private final PushSubscriptionRepository pushRepo;
    private final ObjectMapper objectMapper;

    // VAPID Keys for Web Push Protocol (Standard demo / production keypair)
    @Value("${app.vapid.public-key:BNV1K-k7rC8q1b9xP0eK8Y8-0Wz-2x9vQ5m0L3k7j2H5_4y9x0p1m2n3o4p5q6r7s8t9u0v1w2x3y4z}")
    private String publicKey;

    @Data
    @Builder
    public static class PushMessagePayload {
        private String title;
        private String body;
        private String icon;
        private String badge;
        private String url;
        private Map<String, Object> data;
    }

    public String getPublicKey() {
        return publicKey;
    }

    @Transactional
    public void saveSubscription(User user, String endpoint, String p256dhKey, String authKey) {
        if (endpoint == null || endpoint.isBlank()) return;

        PushSubscriptionEntity sub = pushRepo.findByEndpoint(endpoint)
                .orElse(PushSubscriptionEntity.builder()
                        .endpoint(endpoint)
                        .build());

        sub.setUser(user);
        sub.setP256dhKey(p256dhKey);
        sub.setAuthKey(authKey);
        sub.setRole(user != null && user.getRole() != null ? user.getRole().name() : "EMPLOYEE");
        sub.setBranchId(user != null && user.getBranch() != null ? user.getBranch().getId() : null);

        pushRepo.save(sub);
        log.info("Saved Web Push Subscription for user={}, role={}", user.getUsername(), sub.getRole());
    }

    @Transactional
    public void removeSubscription(String endpoint) {
        pushRepo.findByEndpoint(endpoint).ifPresent(pushRepo::delete);
    }

    @Transactional
    public void sendPushToRole(String role, Long branchId, String title, String body, String icon, String targetUrl) {
        List<PushSubscriptionEntity> subs;
        if (branchId != null) {
            subs = pushRepo.findByRoleAndBranchId(role, branchId);
        } else {
            subs = pushRepo.findByRole(role);
        }

        PushMessagePayload payload = PushMessagePayload.builder()
                .title(title)
                .body(body)
                .icon(icon != null && !icon.isBlank() ? icon : "/logo.png")
                .badge("/icons/icon-192x192.png")
                .url(targetUrl != null ? targetUrl : "/")
                .build();

        sendPushNotifications(subs, payload);
    }

    private void sendPushNotifications(List<PushSubscriptionEntity> subs, PushMessagePayload payload) {
        if (subs == null || subs.isEmpty()) return;

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            for (PushSubscriptionEntity sub : subs) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(sub.getEndpoint()))
                            .header("Content-Type", "application/json")
                            .header("TTL", "86400")
                            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                            .timeout(Duration.ofSeconds(5))
                            .build();

                    client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                            .thenAccept(res -> {
                                if (res.statusCode() == 404 || res.statusCode() == 410) {
                                    log.info("Push subscription expired or invalid (HTTP {}). Removing endpoint.", res.statusCode());
                                    removeSubscription(sub.getEndpoint());
                                }
                            });
                } catch (Exception e) {
                    log.warn("Failed to dispatch push notification to endpoint {}: {}", sub.getEndpoint(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error building Web Push JSON payload", e);
        }
    }
}
