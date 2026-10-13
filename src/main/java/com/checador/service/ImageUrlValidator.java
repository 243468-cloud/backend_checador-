package com.checador.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Valida URLs de imágenes de marca (logoUrl / iconUrl) antes de persistirlas.
 *
 * <p>El backend solo guarda la URL, no el archivo. Por eso aquí se aplican las reglas que se pueden
 * comprobar sin red: solo https, sin credenciales, sin hosts locales/internos, sin IPs literales y
 * sin SVG. El tipo MIME real, el tamaño máximo y la resolución DNS se validan al descargar la imagen
 * (frontend: {@code /api/icons/[slug]/[size]}).</p>
 */
public final class ImageUrlValidator {

    /** Longitud de la columna logo_url / icon_url (VARCHAR(255) por defecto). */
    static final int MAX_LENGTH = 255;

    private ImageUrlValidator() {
    }

    /**
     * @param value URL a validar
     * @param field nombre del campo, solo para el mensaje de error
     * @return la URL sin espacios laterales
     * @throws IllegalArgumentException si la URL no cumple las reglas (se traduce a HTTP 400)
     */
    public static String validate(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field, "no puede estar vacío");
        }
        String url = value.trim();
        if (url.length() > MAX_LENGTH) {
            throw invalid(field, "excede " + MAX_LENGTH + " caracteres");
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw invalid(field, "no es una URL válida");
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw invalid(field, "solo se permite https");
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
            // Rechaza IPs literales (v4, v6, decimal, hex) y nombres sin TLD alfabético.
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

    /** Dominio con al menos un punto y TLD que contiene letras (descarta 127.0.0.1, 0x7f.1, 2130706433, [::1]). */
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
