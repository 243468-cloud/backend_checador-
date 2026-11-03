package com.checador.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.Locale;

/**
 * Valida URLs de imágenes de marca (logoUrl / iconUrl) o cadenas Base64 (data:image/...) antes de persistirlas.
 *
 * <p>Permite tanto URLs https seguras como cadenas Data URL en Base64 (PNG, JPEG, WEBP) generadas al subir
 * una imagen desde el celular o laptop sin guardar archivos en disco.</p>
 */
public final class ImageUrlValidator {

    /** Longitud máxima para URLs remotas https. */
    static final int MAX_URL_LENGTH = 2048;
    /** Longitud máxima para imágenes codificadas en Base64 (~2 MB en texto Base64). */
    static final int MAX_DATA_URI_LENGTH = 2_000_000;

    private ImageUrlValidator() {
    }

    /**
     * @param value URL https o cadena data:image/(png|jpeg|jpg|webp);base64,...
     * @param field nombre del campo, solo para el mensaje de error
     * @return la URL o Data URI sin espacios laterales
     * @throws IllegalArgumentException si no cumple las reglas (se traduce a HTTP 400)
     */
    public static String validate(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field, "no puede estar vacío");
        }
        String url = value.trim();

        // Soporte para imágenes en Base64 (data:image/png;base64,... | jpeg | jpg | webp)
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("data:image/")) {
            if (url.length() > MAX_DATA_URI_LENGTH) {
                throw invalid(field, "la imagen en Base64 es demasiado grande (máximo 1.5 MB)");
            }
            if (!(lower.startsWith("data:image/png;base64,")
                    || lower.startsWith("data:image/jpeg;base64,")
                    || lower.startsWith("data:image/jpg;base64,")
                    || lower.startsWith("data:image/webp;base64,"))) {
                throw invalid(field, "solo se permiten imágenes PNG, JPG o WEBP en Base64");
            }
            int commaIdx = url.indexOf(',');
            if (commaIdx < 0 || commaIdx == url.length() - 1) {
                throw invalid(field, "datos Base64 inválidos");
            }
            String base64Payload = url.substring(commaIdx + 1).replaceAll("\\s+", "");
            try {
                byte[] decoded = Base64.getDecoder().decode(base64Payload);
                if (decoded.length == 0) {
                    throw invalid(field, "imagen Base64 vacía");
                }
            } catch (IllegalArgumentException e) {
                throw invalid(field, "codificación Base64 inválida");
            }
            return url.substring(0, commaIdx + 1) + base64Payload;
        }

        if (url.length() > MAX_URL_LENGTH) {
            throw invalid(field, "excede " + MAX_URL_LENGTH + " caracteres");
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw invalid(field, "no es una URL válida");
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw invalid(field, "solo se permite https o imagen subida en Base64");
        }
        if (uri.getRawUserInfo() != null) {
            throw invalid(field, "no puede incluir credenciales");
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw invalid(field, "solo se permite el puerto 443");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw invalid(field, "host inválido");
        }
        host = host.toLowerCase(Locale.ROOT);
        if (isLocalOrInternalHost(host)) {
            throw invalid(field, "host no permitido");
        }
        if (!isPlainDomainName(host)) {
            throw invalid(field, "debe usar un nombre de dominio, no una IP");
        }

        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith(".svg") || path.endsWith(".svgz")) {
            throw invalid(field, "no se permiten imágenes SVG");
        }
        return url;
    }

    private static boolean isLocalOrInternalHost(String host) {
        return host.equals("localhost")
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || host.endsWith(".internal")
                || host.endsWith(".lan")
                || host.endsWith(".home")
                || host.endsWith(".corp");
    }

    private static boolean isPlainDomainName(String host) {
        if (host.startsWith("[") || host.contains(":")) {
            return false;
        }
        int lastDot = host.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == host.length() - 1) {
            return false;
        }
        String tld = host.substring(lastDot + 1);
        return tld.chars().allMatch(c -> (c >= 'a' && c <= 'z') || c == '-' || (c >= '0' && c <= '9'))
                && tld.chars().anyMatch(c -> c >= 'a' && c <= 'z');
    }

    private static IllegalArgumentException invalid(String field, String reason) {
        return new IllegalArgumentException(field + " " + reason);
    }
}
