package com.smartelectronicsupermarket.security.config;

import java.util.List;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class SasmsJwtDecoderFactory {

    public JwtDecoder create(SasmsJwtProperties properties) {
        properties.validateConfigurationState();
        if (!properties.isConfigured()) {
            throw new IllegalStateException("Cannot create a JWT decoder without complete configuration");
        }

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        configureValidators(decoder, properties);
        return decoder;
    }

    static void configureValidators(NimbusJwtDecoder decoder, SasmsJwtProperties properties) {
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                "aud",
                audience -> audience != null && audience.contains(properties.audience()));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(properties.issuer()),
                audienceValidator));
    }
}
