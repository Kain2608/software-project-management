package com.smartelectronicsupermarket.security.config;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "sasms.jwt")
public record SasmsJwtProperties(String issuer, String audience, String jwksUri) {

    public boolean isDisabled() {
        return !hasText(issuer) && !hasText(audience) && !hasText(jwksUri);
    }

    public boolean isConfigured() {
        return hasText(issuer) && hasText(audience) && hasText(jwksUri);
    }

    public void validateConfigurationState() {
        if (isDisabled()) {
            return;
        }
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "JWT configuration is incomplete; issuer, audience and JWKS URI are all required");
        }

        requireSecureUri(issuer, "issuer");
        requireSecureUri(jwksUri, "JWKS URI");
    }

    private static void requireSecureUri(String value, String fieldName) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                    || "[::1]".equals(host) || "::1".equals(host);
            boolean secureTransport = "https".equalsIgnoreCase(scheme)
                    || ("http".equalsIgnoreCase(scheme) && loopback);
            if (!uri.isAbsolute() || host == null || !secureTransport
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalStateException(fieldName
                        + " requires HTTPS and a host, without credentials, query or fragment; HTTP is allowed only on loopback");
            }
        } catch (URISyntaxException exception) {
            // Configuration may contain secrets. Do not retain the raw URI in a startup exception.
            throw new IllegalStateException(fieldName + " is not a valid URI");
        }
    }

    private static boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
