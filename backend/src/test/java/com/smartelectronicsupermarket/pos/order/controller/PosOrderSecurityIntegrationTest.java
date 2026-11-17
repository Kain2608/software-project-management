package com.smartelectronicsupermarket.pos.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartelectronicsupermarket.common.web.ResourceConflictException;
import com.smartelectronicsupermarket.common.web.ResourceNotFoundException;
import com.smartelectronicsupermarket.pos.order.dto.CreatePosOrderRequest;
import com.smartelectronicsupermarket.pos.order.dto.PosOrderItemRequest;
import com.smartelectronicsupermarket.pos.order.dto.PosOrderItemResponse;
import com.smartelectronicsupermarket.pos.order.dto.PosOrderResponse;
import com.smartelectronicsupermarket.pos.order.dto.ReservePosOrderResponse;
import com.smartelectronicsupermarket.pos.order.service.PosOrderService;
import com.smartelectronicsupermarket.pos.order.service.PosOrderValidationException;
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
import org.springframework.security.access.AccessDeniedException;
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
        "sasms.features.pos-order-api.enabled=true"
})
@Import(PosOrderSecurityIntegrationTest.TestSecurityBeans.class)
class PosOrderSecurityIntegrationTest {

    private static final String ISSUER = "https://issuer.example.test";
    private static final String AUDIENCE = "sasms-api";
    private static final String SUBJECT = "cashier-subject-001";
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000702");
    private static final UUID WAREHOUSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000703");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000704");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000705");

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
    private PosOrderService service;

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
        mockMvc.perform(post("/api/v1/pos/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"store_id\":\"" + STORE_ID + "\",\"warehouse_id\":\"" + WAREHOUSE_ID + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.correlation_id").isString());

        verify(service, never()).createDraftOrder(any(), any());
    }

    @Test
    void createDraftOrder_successWithJwt() throws Exception {
        PosOrderResponse mockResponse = new PosOrderResponse(
                ORDER_ID, "SO-TEST-001", UUID.randomUUID(), STORE_ID, WAREHOUSE_ID, null,
                "POS", "DRAFT", BigDecimal.ZERO, ACTOR_ID, Instant.now(), null,
                List.of(new PosOrderItemResponse(
                        UUID.randomUUID(), PRODUCT_ID, "SKU-001", "Product 1", 1,
                        new BigDecimal("250000"), new BigDecimal("250000"))));

        when(service.createDraftOrder(any(), eq(ACTOR_ID))).thenReturn(mockResponse);

        String token = bearerToken(SUBJECT);
        mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "store_id": "%s",
                                  "warehouse_id": "%s",
                                  "items": [
                                    { "product_id": "%s", "quantity": 1 }
                                  ]
                                }
                                """.formatted(STORE_ID, WAREHOUSE_ID, PRODUCT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order_id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.items[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$.items[0].unit_price").value(250000));
    }

    @Test
    void createDraftOrder_forbidden_whenActorLacksPermission() throws Exception {
        when(service.createDraftOrder(any(), eq(ACTOR_ID)))
                .thenThrow(new AccessDeniedException("Actor lacks sales.checkout"));

        String token = bearerToken(SUBJECT);
        mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "store_id": "%s",
                                  "warehouse_id": "%s",
                                  "items": [
                                    { "product_id": "%s", "quantity": 1 }
                                  ]
                                }
                                """.formatted(STORE_ID, WAREHOUSE_ID, PRODUCT_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlation_id").isString());
    }

    @Test
    void createDraftOrder_unprocessable_whenValidationFails() throws Exception {
        when(service.createDraftOrder(any(), eq(ACTOR_ID)))
                .thenThrow(new PosOrderValidationException("Warehouse does not belong to store", "warehouse_id"));

        String token = bearerToken(SUBJECT);
        mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "store_id": "%s",
                                  "warehouse_id": "%s",
                                  "items": [
                                    { "product_id": "%s", "quantity": 1 }
                                  ]
                                }
                                """.formatted(STORE_ID, WAREHOUSE_ID, PRODUCT_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.field").value("warehouse_id"));
    }

    @Test
    void createDraftOrder_conflict_whenIdempotencyKeyReusedWithDifferentPayload() throws Exception {
        when(service.createDraftOrder(any(), eq(ACTOR_ID)))
                .thenThrow(new ResourceConflictException("Idempotency key reused with a different payload"));

        String token = bearerToken(SUBJECT);
        mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "store_id": "%s",
                                  "warehouse_id": "%s",
                                  "idempotency_key": "%s",
                                  "items": [
                                    { "product_id": "%s", "quantity": 5 }
                                  ]
                                }
                                """.formatted(STORE_ID, WAREHOUSE_ID, UUID.randomUUID(), PRODUCT_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void reserveOrder_success() throws Exception {
        when(service.reserveOrder(ORDER_ID, ACTOR_ID))
                .thenReturn(new ReservePosOrderResponse(ORDER_ID, "RESERVED", new BigDecimal("1250000")));

        String token = bearerToken(SUBJECT);
        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/reserve", ORDER_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order_id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.total_amount").value(1250000));
    }

    @Test
    void reserveOrder_unauthorizedWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/reserve", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void reserveOrder_notFound_whenOrderMissing() throws Exception {
        when(service.reserveOrder(ORDER_ID, ACTOR_ID))
                .thenThrow(new ResourceNotFoundException("Order not found: " + ORDER_ID));

        String token = bearerToken(SUBJECT);
        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/reserve", ORDER_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void reserveOrder_mapsSqlState55P03ToDeviceLockedConflict() throws Exception {
        when(service.reserveOrder(ORDER_ID, ACTOR_ID))
                .thenThrow(new DataAccessResourceFailureException(
                        "lock unavailable",
                        new SQLException("Device held by another cart", "55P03")));

        String token = bearerToken(SUBJECT);
        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/reserve", ORDER_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEVICE_LOCKED"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void reserveOrder_mapsSqlState23514ToValidationFailed() throws Exception {
        when(service.reserveOrder(ORDER_ID, ACTOR_ID))
                .thenThrow(new DataAccessResourceFailureException(
                        "constraint violation",
                        new SQLException("Device count must equal line quantity", "23514")));

        String token = bearerToken(SUBJECT);
        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/reserve", ORDER_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void createDraftOrder_acceptsStandardIdempotencyKeyHeaderWithUuid() throws Exception {
        UUID keyUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PosOrderResponse mockResponse = new PosOrderResponse(
                ORDER_ID, "SO-TEST-002", keyUuid, STORE_ID, WAREHOUSE_ID, null,
                "POS", "DRAFT", BigDecimal.ZERO, ACTOR_ID, Instant.now(), null,
                List.of(new PosOrderItemResponse(
                        UUID.randomUUID(), PRODUCT_ID, "SKU-001", "Product 1", 1,
                        new BigDecimal("250000"), new BigDecimal("250000"))));

        when(service.createDraftOrder(any(), eq(ACTOR_ID))).thenReturn(mockResponse);

        String token = bearerToken(SUBJECT);
        mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", keyUuid.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "store_id": "%s",
                                  "warehouse_id": "%s",
                                  "items": [
                                    { "product_id": "%s", "quantity": 1 }
                                  ]
                                }
                                """.formatted(STORE_ID, WAREHOUSE_ID, PRODUCT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idempotency_key").value(keyUuid.toString()));

        ArgumentCaptor<CreatePosOrderRequest> captor = ArgumentCaptor.forClass(CreatePosOrderRequest.class);
        verify(service).createDraftOrder(captor.capture(), eq(ACTOR_ID));
        assertThat(captor.getValue().rawIdempotencyKey()).isEqualTo(keyUuid.toString());
    }

    @Test
    void createDraftOrder_acceptsArbitraryStringIdempotencyKeyHeaderAndDerivesUuid() throws Exception {
        String stringKey = "custom-pos-client-session-key-999";
        UUID expectedUuid = PosOrderService.resolveIdempotencyKey(stringKey, STORE_ID, ACTOR_ID);

        PosOrderResponse mockResponse = new PosOrderResponse(
                ORDER_ID, "SO-TEST-003", expectedUuid, STORE_ID, WAREHOUSE_ID, null,
                "POS", "DRAFT", BigDecimal.ZERO, ACTOR_ID, Instant.now(), null,
                List.of(new PosOrderItemResponse(
                        UUID.randomUUID(), PRODUCT_ID, "SKU-001", "Product 1", 1,
                        new BigDecimal("250000"), new BigDecimal("250000"))));

        when(service.createDraftOrder(any(), eq(ACTOR_ID))).thenReturn(mockResponse);

        String token = bearerToken(SUBJECT);
        mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", stringKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "store_id": "%s",
                                  "warehouse_id": "%s",
                                  "items": [
                                    { "product_id": "%s", "quantity": 1 }
                                  ]
                                }
                                """.formatted(STORE_ID, WAREHOUSE_ID, PRODUCT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idempotency_key").value(expectedUuid.toString()));

        ArgumentCaptor<CreatePosOrderRequest> captor = ArgumentCaptor.forClass(CreatePosOrderRequest.class);
        verify(service).createDraftOrder(captor.capture(), eq(ACTOR_ID));
        assertThat(captor.getValue().rawIdempotencyKey()).isEqualTo(stringKey);
    }

    @Test
    void createDraftOrder_rejectsBlankIdempotencyKeyHeader() throws Exception {
        when(service.createDraftOrder(any(), eq(ACTOR_ID)))
                .thenThrow(new PosOrderValidationException("Idempotency key must not be blank", "Idempotency-Key"));

        String token = bearerToken(SUBJECT);
        mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "store_id": "%s",
                                  "warehouse_id": "%s",
                                  "items": [
                                    { "product_id": "%s", "quantity": 1 }
                                  ]
                                }
                                """.formatted(STORE_ID, WAREHOUSE_ID, PRODUCT_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.field").value("Idempotency-Key"));
    }

    @Test
    void createDraftOrder_headerOverridesBodyIdempotencyKey() throws Exception {
        String headerKey = "header-authoritative-key";
        String bodyKey = "body-ignored-key";
        UUID expectedUuid = PosOrderService.resolveIdempotencyKey(headerKey, STORE_ID, ACTOR_ID);

        PosOrderResponse mockResponse = new PosOrderResponse(
                ORDER_ID, "SO-TEST-005", expectedUuid, STORE_ID, WAREHOUSE_ID, null,
                "POS", "DRAFT", BigDecimal.ZERO, ACTOR_ID, Instant.now(), null,
                List.of(new PosOrderItemResponse(
                        UUID.randomUUID(), PRODUCT_ID, "SKU-001", "Product 1", 1,
                        new BigDecimal("250000"), new BigDecimal("250000"))));

        when(service.createDraftOrder(any(), eq(ACTOR_ID))).thenReturn(mockResponse);

        String token = bearerToken(SUBJECT);
        mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", headerKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "store_id": "%s",
                                  "warehouse_id": "%s",
                                  "idempotency_key": "%s",
                                  "items": [
                                    { "product_id": "%s", "quantity": 1 }
                                  ]
                                }
                                """.formatted(STORE_ID, WAREHOUSE_ID, bodyKey, PRODUCT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idempotency_key").value(expectedUuid.toString()));

        ArgumentCaptor<CreatePosOrderRequest> captor = ArgumentCaptor.forClass(CreatePosOrderRequest.class);
        verify(service).createDraftOrder(captor.capture(), eq(ACTOR_ID));
        assertThat(captor.getValue().rawIdempotencyKey()).isEqualTo(headerKey);
    }

    private static String bearerToken(String subject) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .subject(subject)
                .claim("aud", List.of(AUDIENCE))
                .build();
        return ENCODER.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(),
                claims)).getTokenValue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSecurityBeans {

        @Bean
        @Primary
        com.smartelectronicsupermarket.security.config.SasmsJwtDecoderFactory testJwtDecoderFactory() {
            return new com.smartelectronicsupermarket.security.config.SasmsJwtDecoderFactory() {
                @Override
                public JwtDecoder create(SasmsJwtProperties properties) {
                    properties.validateConfigurationState();
                    return DECODER;
                }
            };
        }
    }
}
