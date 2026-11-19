package com.smartelectronicsupermarket.pos.payment.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartelectronicsupermarket.pos.payment.service.PosPaymentService;
import com.smartelectronicsupermarket.security.config.SasmsJwtDecoderFactory;
import com.smartelectronicsupermarket.security.config.SasmsJwtProperties;
import com.smartelectronicsupermarket.security.identity.ActorIdentityResolver;
import com.smartelectronicsupermarket.security.identity.AuthenticatedActor;
import com.smartelectronicsupermarket.security.identity.ExternalIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "sasms.jwt.issuer=https://issuer.example.test",
        "sasms.jwt.audience=sasms-api",
        "sasms.jwt.jwks-uri=https://issuer.example.test/.well-known/jwks.json",
        "sasms.features.pos-cash-payment-api.enabled=false"
})
@Import(PosCashPaymentDisabledFeatureFlagIntegrationTest.TestSecurityBeans.class)
class PosCashPaymentDisabledFeatureFlagIntegrationTest {

    private static final String ISSUER = "https://issuer.example.test";
    private static final String AUDIENCE = "sasms-api";
    private static final String SUBJECT = "cashier-subject-001";
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000703");

    private static final RSAPublicKey PUBLIC_KEY;
    private static final JwtEncoder ENCODER;
    private static final JwtDecoder DECODER;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            PUBLIC_KEY = (RSAPublicKey) keyPair.getPublic();
            ENCODER = NimbusJwtEncoder.withKeyPair(
                    PUBLIC_KEY,
                    (RSAPrivateKey) keyPair.getPrivate()).build();
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(PUBLIC_KEY)
                    .signatureAlgorithm(SignatureAlgorithm.RS256)
                    .build();
            decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                    new org.springframework.security.oauth2.jwt.JwtTimestampValidator(),
                    new org.springframework.security.oauth2.jwt.JwtIssuerValidator(ISSUER),
                    new org.springframework.security.oauth2.jwt.JwtClaimValidator<List<String>>(
                            "aud", aud -> aud != null && aud.contains(AUDIENCE))));
            DECODER = decoder;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @Autowired
    private WebApplicationContext applicationContext;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ActorIdentityResolver actorIdentityResolver;

    @MockitoBean
    private PosPaymentService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();

        when(actorIdentityResolver.resolve(new ExternalIdentity(ISSUER, SUBJECT)))
                .thenReturn(Optional.of(new AuthenticatedActor(ACTOR_ID)));
    }

    @Test
    void completeCashPayment_returns404_whenFeatureFlagIsDisabledEvenWithAuth() throws Exception {
        String token = validToken(SUBJECT);
        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/payments/cash", ORDER_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10000000.00}"))
                .andExpect(status().isNotFound());
    }

    private static String validToken(String subject) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(subject)
                .issuedAt(now.minusSeconds(10))
                .notBefore(now.minusSeconds(10))
                .expiresAt(now.plusSeconds(3600))
                .claim("roles", List.of("CASHIER"))
                .build();
        return ENCODER.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(),
                claims)).getTokenValue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSecurityBeans {

        @Bean
        @Primary
        SasmsJwtDecoderFactory testJwtDecoderFactory() {
            return new SasmsJwtDecoderFactory() {
                @Override
                public JwtDecoder create(SasmsJwtProperties properties) {
                    properties.validateConfigurationState();
                    return DECODER;
                }
            };
        }
    }
}
