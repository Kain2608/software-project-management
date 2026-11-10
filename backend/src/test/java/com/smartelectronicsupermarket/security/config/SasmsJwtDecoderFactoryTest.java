package com.smartelectronicsupermarket.security.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class SasmsJwtDecoderFactoryTest {

    private static final String ISSUER = "https://issuer.example.test";
    private static final String AUDIENCE = "sasms-api";
    private static final SasmsJwtProperties PROPERTIES = new SasmsJwtProperties(
            ISSUER,
            AUDIENCE,
            "https://issuer.example.test/.well-known/jwks.json");

    private static RSAPublicKey publicKey;
    private static JwtEncoder rsaEncoder;
    private static JwtDecoder decoder;

    @BeforeAll
    static void createEphemeralTestKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        rsaEncoder = NimbusJwtEncoder.withKeyPair(publicKey, privateKey).build();
        NimbusJwtDecoder rsaDecoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        SasmsJwtDecoderFactory.configureValidators(rsaDecoder, PROPERTIES);
        decoder = rsaDecoder;
    }

    @Test
    void acceptsValidRs256SignatureIssuerAudienceAndTimestamps() {
        String token = rsaToken(ISSUER, List.of(AUDIENCE), Instant.now().minusSeconds(1), Instant.now().plusSeconds(300));

        assertEquals("active-user", assertDoesNotThrow(() -> decoder.decode(token)).getSubject());
    }

    @Test
    void rejectsWrongRsaSignature() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair otherPair = generator.generateKeyPair();
        JwtEncoder otherEncoder = NimbusJwtEncoder.withKeyPair(
                (RSAPublicKey) otherPair.getPublic(),
                (RSAPrivateKey) otherPair.getPrivate()).build();

        assertThrows(JwtException.class, () -> decoder.decode(encode(
                otherEncoder,
                JwsHeader.with(SignatureAlgorithm.RS256).build(),
                claims(ISSUER, List.of(AUDIENCE), Instant.now().minusSeconds(1), Instant.now().plusSeconds(300)))));
    }

    @Test
    void rejectsExpiredAndNotYetValidTokens() {
        String expired = rsaToken(
                ISSUER,
                List.of(AUDIENCE),
                Instant.now().minusSeconds(600),
                Instant.now().minusSeconds(300));
        String notYetValid = rsaToken(
                ISSUER,
                List.of(AUDIENCE),
                Instant.now().plusSeconds(300),
                Instant.now().plusSeconds(600));

        assertThrows(JwtException.class, () -> decoder.decode(expired));
        assertThrows(JwtException.class, () -> decoder.decode(notYetValid));
    }

    @Test
    void rejectsWrongIssuerAndAudience() {
        String wrongIssuer = rsaToken(
                "https://wrong.example.test",
                List.of(AUDIENCE),
                Instant.now().minusSeconds(1),
                Instant.now().plusSeconds(300));
        String wrongAudience = rsaToken(
                ISSUER,
                List.of("other-api"),
                Instant.now().minusSeconds(1),
                Instant.now().plusSeconds(300));

        assertThrows(JwtException.class, () -> decoder.decode(wrongIssuer));
        assertThrows(JwtException.class, () -> decoder.decode(wrongAudience));
    }

    @Test
    void rejectsTokenSignedWithUnconfiguredSymmetricAlgorithm() {
        SecretKey key = new SecretKeySpec(new byte[32], "HmacSHA256");
        JwtEncoder hmacEncoder = NimbusJwtEncoder.withSecretKey(key).build();
        String token = encode(
                hmacEncoder,
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims(ISSUER, List.of(AUDIENCE), Instant.now().minusSeconds(1), Instant.now().plusSeconds(300)));

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void configurationIsDisabledOnlyWhenAllValuesAreAbsentAndPartialConfigFailsFast() {
        SasmsJwtProperties disabled = new SasmsJwtProperties(null, null, null);
        SasmsJwtProperties partial = new SasmsJwtProperties(ISSUER, AUDIENCE, null);

        assertTrue(disabled.isDisabled());
        assertFalse(disabled.isConfigured());
        assertDoesNotThrow(disabled::validateConfigurationState);
        assertThrows(IllegalStateException.class, partial::validateConfigurationState);
    }

    private static String rsaToken(String issuer, List<String> audience, Instant notBefore, Instant expiresAt) {
        return encode(
                rsaEncoder,
                JwsHeader.with(SignatureAlgorithm.RS256).build(),
                claims(issuer, audience, notBefore, expiresAt));
    }

    private static JwtClaimsSet claims(
            String issuer,
            List<String> audience,
            Instant notBefore,
            Instant expiresAt) {
        Instant issuedAt = expiresAt.isBefore(Instant.now())
                ? expiresAt.minusSeconds(300)
                : Instant.now().minusSeconds(5);
        return JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("active-user")
                .audience(audience)
                .issuedAt(issuedAt)
                .notBefore(notBefore)
                .expiresAt(expiresAt)
                .build();
    }

    private static String encode(JwtEncoder encoder, JwsHeader header, JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
