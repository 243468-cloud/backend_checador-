package com.checador.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Rate limiter para endpoints sensibles (login).
 * Permite máx. 5 intentos por IP por ventana de 60 segundos.
 * Usa sliding window con ConcurrentHashMap — sin dependencias externas.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 5;
    private static final long WINDOW_MS    = 60_000L; // 1 minuto

    // IP → lista de timestamps de peticiones recientes
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> attempts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        // Solo aplica al endpoint de login
        if (!req.getRequestURI().equals("/api/auth/login") || !"POST".equalsIgnoreCase(req.getMethod())) {
            chain.doFilter(req, res);
            return;
        }

        String ip = resolveClientIp(req);
        long now  = System.currentTimeMillis();

        attempts.compute(ip, (k, timestamps) -> {
            if (timestamps == null) timestamps = new CopyOnWriteArrayList<>();
            // Deslizar ventana: eliminar entradas antiguas
            timestamps.removeIf(t -> (now - t) > WINDOW_MS);
            return timestamps;
        });

        CopyOnWriteArrayList<Long> ts = attempts.get(ip);
        if (ts.size() >= MAX_REQUESTS) {
            res.setStatus(429);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"error\":\"Demasiados intentos de inicio de sesión. Espera 1 minuto e inténtalo de nuevo.\"}");
            return;
        }

        ts.add(now);
        chain.doFilter(req, res);
    }

    private String resolveClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
