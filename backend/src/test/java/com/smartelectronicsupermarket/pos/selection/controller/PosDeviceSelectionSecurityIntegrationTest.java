package com.smartelectronicsupermarket.pos.selection.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartelectronicsupermarket.pos.selection.service.PosDeviceSelectionService;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
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
        "sasms.features.pos-serial-api.enabled=true"
})
@Import(PosDeviceSelectionSecurityIntegrationTest.TestSecurityBeans.class)
class PosDeviceSelectionSecurityIntegrationTest {

    private static final String ISSUER = "https://issuer.example.test";
    private static final String AUDIENCE = "sasms-api";
    private static final String SUBJECT = "cashier-subject-001";
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000702");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000703");
    private static final UUID ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000704");
    private static final UUID DEVICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000705");

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
    private PosDeviceSelectionService service;

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
    void unauthenticatedPostIsRejectedWith401() throws Exception {
        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/items/{itemId}/devices", ORDER_ID, ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"store_id\":\"" + STORE_ID + "\",\"serial\":\"SER-001\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.correlation_id").isString());

        verify(service, never()).lockDevice(any(), any(), any(), any(), any());
    }

    @Test
    void unauthenticatedDeleteIsRejectedWith401() throws Exception {
        mockMvc.perform(delete("/api/v1/pos/orders/{orderId}/devices/{deviceId}", ORDER_ID, DEVICE_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(service, never()).releaseDevice(any(), any(), any());
    }

    @Test
    void authenticatedPostUsesTrustedActorAndIgnoresClientSuppliedActorIdAndPrice() throws Exception {
        when(service.lockDevice(STORE_ID, ORDER_ID, ITEM_ID, "SER-001", ACTOR_ID))
                .thenReturn(DEVICE_ID);

        String token = validToken(SUBJECT);
        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/items/{itemId}/devices", ORDER_ID, ITEM_ID)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Actor-Id", "00000000-0000-0000-0000-000000000999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "store_id": "%s",
                                  "serial": "SER-001",
                                  "actor_id": "00000000-0000-0000-0000-000000000888",
                                  "unit_price": 500,
                                  "status": "SOLD"
                                }
                                """.formatted(STORE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_id").value(DEVICE_ID.toString()))
                .andExpect(jsonPath("$.status").value("SELECTED"));

        // Verify service was called with trusted ACTOR_ID, never the forged ones
        verify(service).lockDevice(STORE_ID, ORDER_ID, ITEM_ID, "SER-001", ACTOR_ID);
    }

    @Test
    void authenticatedDeleteUsesTrustedActorAndReturnsReleasedStatus() throws Exception {
        when(service.releaseDevice(ORDER_ID, DEVICE_ID, ACTOR_ID)).thenReturn(true);

        String token = validToken(SUBJECT);
        mockMvc.perform(delete("/api/v1/pos/orders/{orderId}/devices/{deviceId}", ORDER_ID, DEVICE_ID)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Actor-Id", "00000000-0000-0000-0000-000000000999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_id").value(DEVICE_ID.toString()))
                .andExpect(jsonPath("$.released").value(true));

        verify(service).releaseDevice(ORDER_ID, DEVICE_ID, ACTOR_ID);
    }

    @Test
    void unknownSubjectInDatabaseReturns401Unauthorized() throws Exception {
        when(actorIdentityResolver.resolve(new ExternalIdentity(ISSUER, "unknown-subject")))
                .thenReturn(Optional.empty());

        String token = validToken("unknown-subject");
        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/items/{itemId}/devices", ORDER_ID, ITEM_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"store_id\":\"" + STORE_ID + "\",\"serial\":\"SER-001\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(service, never()).lockDevice(any(), any(), any(), any(), any());
    }

    @Test
    void lockConflictSqlState55P03MapsTo409DeviceLocked() throws Exception {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException(
                "device locked by other POS",
                new SQLException("Held by concurrent order", "55P03"));
        when(service.lockDevice(STORE_ID, ORDER_ID, ITEM_ID, "SER-001", ACTOR_ID))
                .thenThrow(failure);

        String token = validToken(SUBJECT);
        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/items/{itemId}/devices", ORDER_ID, ITEM_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"store_id\":\"" + STORE_ID + "\",\"serial\":\"SER-001\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEVICE_LOCKED"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(content().string(not(containsString("Held by concurrent order"))));
    }

    @Test
    void deviceNotFoundSqlStateP0002MapsTo404NotFound() throws Exception {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException(
                "serial missing",
                new SQLException("Serial not found in warehouse", "P0002"));
        when(service.lockDevice(STORE_ID, ORDER_ID, ITEM_ID, "SER-MISSING", ACTOR_ID))
                .thenThrow(failure);

        String token = validToken(SUBJECT);
        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/items/{itemId}/devices", ORDER_ID, ITEM_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"store_id\":\"" + STORE_ID + "\",\"serial\":\"SER-MISSING\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(content().string(not(containsString("Serial not found in warehouse"))));
    }

    @Test
    void validationConstraintSqlState23514MapsTo422ValidationFailed() throws Exception {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException(
                "order expired or not draft",
                new SQLException("Draft order has expired", "23514"));
        when(service.lockDevice(STORE_ID, ORDER_ID, ITEM_ID, "SER-001", ACTOR_ID))
                .thenThrow(failure);

        String token = validToken(SUBJECT);
        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/items/{itemId}/devices", ORDER_ID, ITEM_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"store_id\":\"" + STORE_ID + "\",\"serial\":\"SER-001\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(content().string(not(containsString("Draft order has expired"))));
    }

    @Test
    void permissionDeniedSqlState42501MapsTo403Forbidden() throws Exception {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException(
                "out of store scope",
                new SQLException("Permission denied for checkout", "42501"));
        when(service.lockDevice(STORE_ID, ORDER_ID, ITEM_ID, "SER-001", ACTOR_ID))
                .thenThrow(failure);

        String token = validToken(SUBJECT);
        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/items/{itemId}/devices", ORDER_ID, ITEM_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"store_id\":\"" + STORE_ID + "\",\"serial\":\"SER-001\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(content().string(not(containsString("Permission denied"))));
    }

    @Test
    void generalDatabaseFailureReturns503WithoutLeakingSecrets() throws Exception {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException(
                "connection leak",
                new SQLException("Connection failed: jdbc:postgresql://secret-db:5432/sasms"));
        when(service.lockDevice(STORE_ID, ORDER_ID, ITEM_ID, "SER-001", ACTOR_ID))
                .thenThrow(failure);

        String token = validToken(SUBJECT);
        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/items/{itemId}/devices", ORDER_ID, ITEM_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"store_id\":\"" + STORE_ID + "\",\"serial\":\"SER-001\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(content().string(not(containsString("secret-db"))));
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
