package com.checador.service;

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

    public record ClientEmitter(
            String id,
            Long userId,
            String role,
            Long branchId,
            SseEmitter emitter
    ) {
        public String getId() { return id; }
        public Long getUserId() { return userId; }
        public String getRole() { return role; }
        public Long getBranchId() { return branchId; }
        public SseEmitter getEmitter() { return emitter; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String id;
            private Long userId;
            private String role;
            private Long branchId;
            private SseEmitter emitter;

            public Builder id(String id) {
                this.id = id;
                return this;
            }

            public Builder userId(Long userId) {
                this.userId = userId;
                return this;
            }

            public Builder role(String role) {
                this.role = role;
                return this;
            }

            public Builder branchId(Long branchId) {
                this.branchId = branchId;
                return this;
            }

            public Builder emitter(SseEmitter emitter) {
                this.emitter = emitter;
                return this;
            }

            public ClientEmitter build() {
                return new ClientEmitter(id, userId, role, branchId, emitter);
            }
        }
    }

    public record RealtimePayload(
            String type,
            Object data,
            long timestamp
    ) {
        public String getType() { return type; }
        public Object getData() { return data; }
        public long getTimestamp() { return timestamp; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String type;
            private Object data;
            private long timestamp;

            public Builder type(String type) {
                this.type = type;
                return this;
            }

            public Builder data(Object data) {
                this.data = data;
                return this;
            }

            public Builder timestamp(long timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public RealtimePayload build() {
                return new RealtimePayload(type, data, timestamp);
            }
        }
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
