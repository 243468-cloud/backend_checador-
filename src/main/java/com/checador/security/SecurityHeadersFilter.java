package com.checador.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Agrega headers HTTP de seguridad a cada respuesta.
 * Protege contra: clickjacking, MIME sniffing, XSS reflectado, HTTPS downgrade.
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        // Previene clickjacking
        res.setHeader("X-Frame-Options", "DENY");

        // Previene MIME sniffing
        res.setHeader("X-Content-Type-Options", "nosniff");

        // Fuerza HTTPS por 1 año (solo en producción, pero inofensivo en dev)
        res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");

        // Política de referrer: no exponer URL completa
        res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Deshabilitar funciones innecesarias del navegador
        res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(self)");

        // Content Security Policy — solo recursos propios + Google Fonts
        res.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                "script-src 'self' 'unsafe-inline'; " +
                "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                "font-src 'self' https://fonts.gstatic.com; " +
                "img-src 'self' data: blob:; " +
                "connect-src 'self'; " +
                "frame-ancestors 'none';"
        );

        // Previene XSS en browsers antiguos
        res.setHeader("X-XSS-Protection", "1; mode=block");

        chain.doFilter(req, res);
    }
}
