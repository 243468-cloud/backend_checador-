package com.checador.service;

import com.checador.entity.PushSubscriptionEntity;
import com.checador.entity.User;
import com.checador.repository.PushSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import nl.martijndwars.webpush.Utils;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.spec.ECGenParameterSpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebPushService {

    private final PushSubscriptionRepository pushRepo;
    private final ObjectMapper objectMapper;

    @Value("${app.vapid.public-key:}")
    private String configuredPublicKey;

    @Value("${app.vapid.private-key:}")
    private String configuredPrivateKey;

    @Value("${app.vapid.subject:mailto:admin@viagourmet.com}")
    private String subject;

    private PushService pushService;
    private String activePublicKey;

    @PostConstruct
    public void init() {
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }

            String pubKey = (configuredPublicKey != null && !configuredPublicKey.isBlank()) ? configuredPublicKey : null;
            String privKey = (configuredPrivateKey != null && !configuredPrivateKey.isBlank()) ? configuredPrivateKey : null;

            if (pubKey == null || privKey == null) {
                log.info("Generating BouncyCastle EC KeyPair for WebPushService...");
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "BC");
                kpg.initialize(new ECGenParameterSpec("prime256v1"));
                KeyPair keyPair = kpg.generateKeyPair();

                ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
                ECPrivateKey privateKey = (ECPrivateKey) keyPair.getPrivate();

                pubKey = Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.encode(publicKey));
                privKey = Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.encode(privateKey));
            }

            this.activePublicKey = pubKey;
            this.pushService = new PushService(pubKey, privKey, subject);
            log.info("WebPushService initialized successfully with VAPID Subject: {}", subject);
        } catch (Exception e) {
            log.error("Failed to initialize WebPushService VAPID keys", e);
        }
    }

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
        return activePublicKey;
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
        log.info("Registered VAPID Push Subscription for user={}, role={}", user.getUsername(), sub.getRole());
    }

    @Transactional
    public void removeSubscription(String endpoint) {
        pushRepo.findByEndpoint(endpoint).ifPresent(pushRepo::delete);
    }

    @Transactional
    public void sendPushToRole(String role, Long branchId, String title, String body, String icon, String targetUrl) {
        List<PushSubscriptionEntity> subs = pushRepo.findByRole(role);
        if ("ADMIN".equals(role)) {
            // Incluir también superusuarios para que reciban notificaciones de todas las sucursales
            List<PushSubscriptionEntity> superSubs = pushRepo.findByRole("SUPERUSER");
            java.util.Set<PushSubscriptionEntity> set = new java.util.HashSet<>(subs);
            set.addAll(superSubs);
            subs = new java.util.ArrayList<>(set);
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

    @Transactional
    public void sendPushToUser(Long userId, String title, String body, String icon, String targetUrl) {
        if (userId == null) return;
        List<PushSubscriptionEntity> subs = pushRepo.findByUserId(userId);
        PushMessagePayload payload = PushMessagePayload.builder()
                .title(title)
                .body(body)
                .icon(icon != null && !icon.isBlank() ? icon : "/logo.png")
                .badge("/icons/icon-192x192.png")
                .url(targetUrl != null ? targetUrl : "/")
                .build();
        sendPushNotifications(subs, payload);
    }

    @Transactional
    public void sendPushToAll(String title, String body, String icon, String targetUrl) {
        List<PushSubscriptionEntity> subs = pushRepo.findAll();
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
        if (subs == null || subs.isEmpty() || pushService == null) return;

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);

            for (PushSubscriptionEntity subEntity : subs) {
                try {
                    Subscription.Keys keys = new Subscription.Keys(subEntity.getP256dhKey(), subEntity.getAuthKey());
                    Subscription subscription = new Subscription(subEntity.getEndpoint(), keys);
                    Notification notification = new Notification(subscription, jsonPayload);

                    HttpResponse response = pushService.send(notification);
                    int statusCode = (response != null && response.getStatusLine() != null) 
                            ? response.getStatusLine().getStatusCode() 
                            : 0;

                    log.info("Dispatched VAPID Push to endpoint {}: HTTP {}", subEntity.getEndpoint(), statusCode);

                    if (statusCode == 404 || statusCode == 410) {
                        log.info("Push subscription expired/invalid (HTTP {}). Removing subscription.", statusCode);
                        removeSubscription(subEntity.getEndpoint());
                    }
                } catch (Exception e) {
                    log.warn("Error dispatching Web Push notification: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error formatting Web Push JSON payload", e);
        }
    }
}
