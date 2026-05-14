package com.example.neuroflowplanner.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import javax.net.ssl.SSLException;

public final class CloudSyncUrlSupport {
    private static final String LOCALHOST = "localhost";
    private static final String LOOPBACK_IPV4 = "127.0.0.1";
    private static final String WILDCARD_IPV4 = "0.0.0.0";
    private static final String LOOPBACK_IPV6 = "::1";
    private static final String WILDCARD_IPV6 = "::";

    private CloudSyncUrlSupport() {
    }

    public static String normalizeBaseUrl(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            return "";
        }

        String withScheme = hasExplicitScheme(value) ? value : "http://" + value;
        try {
            URI parsed = new URI(withScheme);
            String host = parsed.getHost();
            if (host == null || host.isBlank()) {
                return value;
            }

            String normalizedHost = normalizeHost(host);
            String normalizedScheme = normalizeScheme(parsed.getScheme(), host, parsed.getPort());
            String normalizedPath = normalizePath(parsed.getPath());
            URI normalized = new URI(
                    normalizedScheme,
                    parsed.getUserInfo(),
                    normalizedHost,
                    parsed.getPort(),
                    normalizedPath,
                    parsed.getQuery(),
                    parsed.getFragment());
            return normalized.toString();
        } catch (URISyntaxException e) {
            return value;
        }
    }

    public static String describeTransportFailure(Throwable cause, String configuredBaseUrl) {
        if (cause == null) {
            return "";
        }
        String normalizedBaseUrl = normalizeBaseUrl(configuredBaseUrl);
        if (normalizedBaseUrl.isBlank()) {
            return "";
        }

        URI uri = parse(normalizedBaseUrl);
        if (uri == null || !isLocalHost(uri.getHost())) {
            return "";
        }

        if (isLikelySslMismatch(cause) && "https".equalsIgnoreCase(uri.getScheme())) {
            return "Локальный backend отвечает по HTTP, а в настройках сохранён HTTPS-адрес `"
                    + normalizedBaseUrl
                    + "`. Для uvicorn без TLS используйте `"
                    + recommendedLocalHttpUrl(uri)
                    + "`.";
        }

        if (configuredBaseUrl != null && configuredBaseUrl.contains(WILDCARD_IPV4)) {
            return "Адрес `"
                    + configuredBaseUrl.trim()
                    + "` использует `0.0.0.0`, это bind-адрес сервера, а не адрес клиента. Для локального подключения используйте `"
                    + recommendedLocalHttpUrl(uri)
                    + "`.";
        }

        return "";
    }

    private static boolean hasExplicitScheme(String value) {
        int separatorIndex = value.indexOf("://");
        if (separatorIndex <= 0) {
            return false;
        }
        for (int i = 0; i < separatorIndex; i++) {
            char current = value.charAt(i);
            boolean valid = Character.isLetterOrDigit(current)
                    || current == '+'
                    || current == '-'
                    || current == '.';
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeHost(String host) {
        if (isWildcardHost(host)) {
            return LOOPBACK_IPV4;
        }
        return host.toLowerCase(Locale.ROOT);
    }

    private static String normalizeScheme(String scheme, String originalHost, int port) {
        String normalizedScheme = scheme == null || scheme.isBlank()
                ? "http"
                : scheme.toLowerCase(Locale.ROOT);
        if (isWildcardHost(originalHost) && "https".equals(normalizedScheme) && port == 8000) {
            return "http";
        }
        return normalizedScheme;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return null;
        }
        int endIndex = path.length();
        while (endIndex > 1 && path.charAt(endIndex - 1) == '/') {
            endIndex--;
        }
        return path.substring(0, endIndex);
    }

    private static URI parse(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static boolean isWildcardHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return WILDCARD_IPV4.equals(normalized) || WILDCARD_IPV6.equals(normalized);
    }

    private static boolean isLocalHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return LOCALHOST.equals(normalized)
                || LOOPBACK_IPV4.equals(normalized)
                || LOOPBACK_IPV6.equals(normalized)
                || isWildcardHost(normalized);
    }

    private static boolean isLikelySslMismatch(Throwable cause) {
        if (cause instanceof SSLException) {
            return true;
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("ssl")
                || normalized.contains("tls")
                || normalized.contains("handshake")
                || normalized.contains("unrecognized ssl message");
    }

    private static String recommendedLocalHttpUrl(URI uri) {
        int port = uri.getPort();
        String path = normalizePath(uri.getPath());
        try {
            return new URI("http", null, LOOPBACK_IPV4, port, path, uri.getQuery(), uri.getFragment()).toString();
        } catch (URISyntaxException e) {
            return "http://" + LOOPBACK_IPV4 + (port > 0 ? ":" + port : "");
        }
    }
}
