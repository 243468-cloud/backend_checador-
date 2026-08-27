package com.checador.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Service
public class KeepAliveService {

    @Value("${RENDER_EXTERNAL_URL:}")
    private String renderExternalUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Cron Job running every 10 minutes to self-ping and prevent Render Free Tier spin-down.
     */
    @Scheduled(cron = "0 */10 * * * *")
    public void pingSelf() {
        String targetBase = renderExternalUrl;
        if (targetBase == null || targetBase.isBlank() || targetBase.contains("localhost")) {
            targetBase = System.getenv("RENDER_EXTERNAL_URL");
        }

        if (targetBase == null || targetBase.isBlank() || targetBase.contains("localhost")) {
            return;
        }

        try {
            String targetUrl = targetBase.replaceAll("/+$", "") + "/api/branches/public";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Render Keep-Alive Self-Ping executed to {} — Status: {}", targetUrl, response.statusCode());
        } catch (Exception e) {
            log.warn("Render Keep-Alive Self-Ping warning: {}", e.getMessage());
        }
    }
}
