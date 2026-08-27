package com.checador.service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RealtimeEventService {

    private final Map<String, ClientEmitter> emitters = new ConcurrentHashMap<>();

    @Data
    @Builder
    public static class ClientEmitter {
        private String id;
        private Long userId;
        private String role;
        private Long branchId;
        private SseEmitter emitter;
    }

    @Data
    @Builder
    public static class RealtimePayload {
        private String type;
        private Object data;
        private long timestamp;
    }

    public SseEmitter subscribe(Long userId, String role, Long branchId) {
        // Emitter with 30 min timeout (reconnects automatically on client side)
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        String emitterId = userId + "_" + System.currentTimeMillis();

        ClientEmitter client = ClientEmitter.builder()
                .id(emitterId)
                .userId(userId)
                .role(role)
                .branchId(branchId)
                .emitter(emitter)
                .build();

        emitters.put(emitterId, client);

        emitter.onCompletion(() -> emitters.remove(emitterId));
        emitter.onTimeout(() -> emitters.remove(emitterId));
        emitter.onError(e -> emitters.remove(emitterId));

        // Send connected handshake event
        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(RealtimePayload.builder()
                            .type("CONNECTED")
                            .data("Connected to Checador Realtime Stream")
                            .timestamp(System.currentTimeMillis())
                            .build()));
        } catch (IOException e) {
            emitters.remove(emitterId);
        }

        log.info("Client connected to Realtime SSE: userId={}, role={}, branchId={}", userId, role, branchId);
        return emitter;
    }

    public void broadcastEvent(String eventType, Object payloadData, String targetRole, Long targetBranchId) {
        RealtimePayload payload = RealtimePayload.builder()
                .type(eventType)
                .data(payloadData)
                .timestamp(System.currentTimeMillis())
                .build();

        emitters.forEach((id, client) -> {
            boolean roleMatch = targetRole == null || targetRole.equalsIgnoreCase(client.getRole()) || "SUPERUSER".equalsIgnoreCase(client.getRole());
            boolean branchMatch = targetBranchId == null || client.getBranchId() == null || targetBranchId.equals(client.getBranchId());

            if (roleMatch && branchMatch) {
                try {
                    client.getEmitter().send(SseEmitter.event()
                            .name(eventType)
                            .data(payload));
                } catch (Exception e) {
                    emitters.remove(id);
                }
            }
        });
    }

    // Heartbeat ping every 15 seconds to keep connection alive through Render/Vercel proxies
    @Scheduled(fixedRate = 15000)
    public void sendHeartbeat() {
        if (emitters.isEmpty()) return;

        RealtimePayload ping = RealtimePayload.builder()
                .type("PING")
                .data("ping")
                .timestamp(System.currentTimeMillis())
                .build();

        emitters.forEach((id, client) -> {
            try {
                client.getEmitter().send(SseEmitter.event().name("PING").data(ping));
            } catch (Exception e) {
                emitters.remove(id);
            }
        });
    }
}
