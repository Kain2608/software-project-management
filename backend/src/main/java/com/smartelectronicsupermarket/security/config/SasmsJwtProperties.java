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

        requireAbsoluteHttpUri(issuer, "issuer");
        requireAbsoluteHttpUri(jwksUri, "JWKS URI");
    }

    private static void requireAbsoluteHttpUri(String value, String fieldName) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (!uri.isAbsolute() || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
                throw new IllegalStateException(fieldName + " must be an absolute HTTP(S) URI");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(fieldName + " is not a valid URI", exception);
        }
    }

    private static boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
