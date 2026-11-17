package com.smartelectronicsupermarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.smartelectronicsupermarket.pos.order.data.PosOrderDataAccess;
import com.smartelectronicsupermarket.pos.order.data.PosOrderItemRecord;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Isolated PostgreSQL integration test for Sprint 2 Order-First draft and reserve flow.
 * Runs against an isolated PostgreSQL 16 container when SASMS_S2_PG_IT_ENABLED=true.
 */
@SpringBootTest
@ActiveProfiles("postgresql")
@EnabledIfEnvironmentVariable(named = "SASMS_S2_PG_IT_ENABLED", matches = "(?i)true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Sprint2OrderPostgreSqlIntegrationTest {

    private static final String ENABLE_ENV = "SASMS_S2_PG_IT_ENABLED";
    private static final String URL_ENV = "SASMS_DB_URL";
    private static final String USERNAME_ENV = "SASMS_DB_USERNAME";
    private static final String PASSWORD_ENV = "SASMS_DB_PASSWORD";
    private static final String ADMIN_USERNAME_ENV = "SASMS_IT_ADMIN_USERNAME";
    private static final String ADMIN_PASSWORD_ENV = "SASMS_IT_ADMIN_PASSWORD";

    private static final String ISSUER = "https://idp.sasms.internal.test";
    private static final String AUDIENCE = "sasms-api";
    private static final String KEY_ID = "tv4-s2-it-rsa-key";

    // Isolated test fixtures
    private static final UUID ACTOR_STORE_A_CASHIER = UUID.fromString("22222222-2222-4222-8222-000000000101");
    private static final UUID ACTOR_STORE_A_SALES = UUID.fromString("22222222-2222-4222-8222-000000000102");
    private static final UUID ACTOR_STORE_B_CASHIER = UUID.fromString("22222222-2222-4222-8222-000000000103");
    private static final UUID ACTOR_INACTIVE = UUID.fromString("22222222-2222-4222-8222-000000000104");
    private static final UUID ACTOR_UNASSIGNED = UUID.fromString("22222222-2222-4222-8222-000000000105");

    private static final String SUB_STORE_A_CASHIER = "urn:sasms:s2-it:store-a-cashier";
    private static final String SUB_STORE_A_SALES = "urn:sasms:s2-it:store-a-sales";
    private static final String SUB_STORE_B_CASHIER = "urn:sasms:s2-it:store-b-cashier";
    private static final String SUB_INACTIVE = "urn:sasms:s2-it:inactive";
    private static final String SUB_UNASSIGNED = "urn:sasms:s2-it:unassigned";

    private static final UUID STORE_A_ID = UUID.fromString("22222222-2222-4222-8222-000000000201");
    private static final UUID STORE_B_ID = UUID.fromString("22222222-2222-4222-8222-000000000202");
    private static final UUID WAREHOUSE_A_ID = UUID.fromString("22222222-2222-4222-8222-000000000211");
    private static final UUID WAREHOUSE_B_ID = UUID.fromString("22222222-2222-4222-8222-000000000212");

    private static final UUID PRODUCT_A_ID = UUID.fromString("22222222-2222-4222-8222-000000000301");
    private static final UUID PRODUCT_B_ID = UUID.fromString("22222222-2222-4222-8222-000000000302");

    private static final UUID DEVICE_A_1_ID = UUID.fromString("22222222-2222-4222-8222-000000000401");
    private static final UUID DEVICE_A_2_ID = UUID.fromString("22222222-2222-4222-8222-000000000402");

    private static final UUID BRAND_ID = UUID.fromString("22222222-2222-4222-8222-000000000020");
    private static final UUID CATEGORY_ID = UUID.fromString("22222222-2222-4222-8222-000000000021");
    private static final UUID SUPPLIER_ID = UUID.fromString("22222222-2222-4222-8222-000000000040");
    private static final UUID PO_ID = UUID.fromString("22222222-2222-4222-8222-000000000050");
    private static final UUID PO_ITEM_A_ID = UUID.fromString("22222222-2222-4222-8222-000000000051");
    private static final UUID PO_ITEM_B_ID = UUID.fromString("22222222-2222-4222-8222-000000000052");
    private static final UUID GR_ID = UUID.fromString("22222222-2222-4222-8222-000000000060");
    private static final UUID GR_ITEM_A_ID = UUID.fromString("22222222-2222-4222-8222-000000000061");
    private static final UUID GR_ITEM_B_ID = UUID.fromString("22222222-2222-4222-8222-000000000062");

    static {
        System.setProperty("user.timezone", "UTC");
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    private static HttpServer jwksServer;
    private static String jwksUri;
    private static JwtEncoder jwtEncoder;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PosOrderDataAccess dataAccess;

    private MockMvc mockMvc;

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        ensureJwksServerStarted();
        registry.add("sasms.jwt.issuer", () -> ISSUER);
        registry.add("sasms.jwt.audience", () -> AUDIENCE);
        registry.add("sasms.jwt.jwks-uri", () -> jwksUri);
        registry.add("sasms.features.pos-serial-api.enabled", () -> "true");
        registry.add("sasms.features.pos-order-api.enabled", () -> "true");
    }

    private static synchronized void ensureJwksServerStarted() {
        if (jwksServer != null) {
            return;
        }
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

            RSAKey jwk = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(KEY_ID)
                    .build();
            String jwksJson = new JWKSet(jwk.toPublicJWK()).toString();

            jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));

            jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            jwksServer.createContext("/.well-known/jwks.json", exchange -> {
                byte[] bytes = jwksJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            });
            jwksServer.start();
            jwksUri = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/.well-known/jwks.json";
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @BeforeAll
    static void setUpIsolatedDatabaseFixtures() throws SQLException {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv(ENABLE_ENV)),
                () -> "Sprint 2 PostgreSQL integration test is disabled; set " + ENABLE_ENV + "=true to run it");

        String jdbcUrl = requiredEnv(URL_ENV);
        String appUser = requiredEnv(USERNAME_ENV);
        String appPassword = requiredEnv(PASSWORD_ENV);
        String adminUser = requiredEnv(ADMIN_USERNAME_ENV);
        String adminPassword = requiredEnv(ADMIN_PASSWORD_ENV);

        assertSafeIsolatedTarget(jdbcUrl, appUser);

        try (Connection adminConn = DriverManager.getConnection(jdbcUrl, adminUser, adminPassword)) {
            adminConn.setAutoCommit(false);
            try {
                cleanupFixtures(adminConn);
                seedFixtures(adminConn);
                adminConn.commit();
            } catch (SQLException ex) {
                adminConn.rollback();
                throw ex;
            }
        }

        try (Connection appConn = DriverManager.getConnection(jdbcUrl, appUser, appPassword)) {
            assertEquals("sasms", scalarString(appConn, "SELECT current_database()"));
        }
    }

    @AfterAll
    static void tearDownIsolatedFixtures() throws SQLException {
        try {
            if ("true".equalsIgnoreCase(System.getenv(ENABLE_ENV))) {
                String jdbcUrl = requiredEnv(URL_ENV);
                String adminUser = requiredEnv(ADMIN_USERNAME_ENV);
                String adminPassword = requiredEnv(ADMIN_PASSWORD_ENV);
                try (Connection adminConn = DriverManager.getConnection(jdbcUrl, adminUser, adminPassword)) {
                    adminConn.setAutoCommit(false);
                    try {
                        cleanupFixtures(adminConn);
                        adminConn.commit();
                    } catch (SQLException ex) {
                        adminConn.rollback();
                        throw ex;
                    }
                }
            }
        } finally {
            if (jwksServer != null) {
                jwksServer.stop(0);
                jwksServer = null;
            }
        }
    }

    @BeforeEach
    void setUpMockMvc() {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv(ENABLE_ENV)),
                () -> "Sprint 2 PostgreSQL integration test is disabled; set " + ENABLE_ENV + "=true to run it");
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @Order(1)
    void runtimeRoleSasmsAppEnforcesLeastPrivilegeOnRealPostgreSql() {
        String currentUser = jdbcTemplate.queryForObject("SELECT current_user", String.class);
        String isSuperuser = jdbcTemplate.queryForObject("SELECT current_setting('is_superuser')", String.class);
        Boolean isMember = jdbcTemplate.queryForObject(
                "SELECT pg_has_role(current_user, 'sasms_app', 'MEMBER')", Boolean.class);

        assertNotNull(currentUser);
        assertFalse("sasms_owner".equalsIgnoreCase(currentUser), "Runtime must not connect as sasms_owner");
        assertFalse("postgres".equalsIgnoreCase(currentUser), "Runtime must not connect as postgres");
        assertEquals("off", isSuperuser);
        assertEquals(Boolean.TRUE, isMember);

        // App role cannot directly set status='PAID' or total_amount on sales_orders insert
        assertInsufficientPrivilege(() -> jdbcTemplate.update(
                """
                INSERT INTO sasms.sales_orders(id, code, idempotency_key, store_id, warehouse_id, channel, status, total_amount, created_by)
                VALUES (gen_random_uuid(), 'ORD-FORGE', gen_random_uuid(), ?, ?, 'POS', 'PAID', 99999999, ?)
                """,
                STORE_A_ID, WAREHOUSE_A_ID, ACTOR_STORE_A_CASHIER));

        // App role cannot directly alter devices status outside functions
        assertInsufficientPrivilege(() -> jdbcTemplate.update(
                "UPDATE sasms.devices SET status = 'SOLD' WHERE id = ?", DEVICE_A_1_ID));
    }

    @Test
    @Order(2)
    void createDraftOrderSuccessAndReplaySamePayload() throws Exception {
        String token = validTokenFor(SUB_STORE_A_CASHIER);
        UUID idempotencyKey = UUID.fromString("22222222-2222-4222-8222-000000000501");

        String payload = """
                {
                  "store_id": "%s",
                  "warehouse_id": "%s",
                  "items": [
                    {"product_id": "%s", "quantity": 1}
                  ]
                }
                """.formatted(STORE_A_ID, WAREHOUSE_A_ID, PRODUCT_A_ID);

        // First creation
        MvcResult firstResult = mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order_id").isString())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.store_id").value(STORE_A_ID.toString()))
                .andExpect(jsonPath("$.warehouse_id").value(WAREHOUSE_A_ID.toString()))
                .andExpect(jsonPath("$.total_amount").value(10000000.00))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].product_id").value(PRODUCT_A_ID.toString()))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.items[0].unit_price").value(10000000.00))
                .andExpect(jsonPath("$.items[0].line_total").value(10000000.00))
                .andReturn();

        JsonNode firstNode = objectMapper.readTree(firstResult.getResponse().getContentAsString());
        String createdOrderId = firstNode.get("order_id").asString();

        // Exact replay with same idempotency key and same payload
        MvcResult replayResult = mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order_id").value(createdOrderId))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        JsonNode replayNode = objectMapper.readTree(replayResult.getResponse().getContentAsString());
        assertEquals(createdOrderId, replayNode.get("order_id").asString());

        // Verify DB state: exactly 1 order row
        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sasms.sales_orders WHERE idempotency_key = CAST(? AS uuid)",
                Integer.class, idempotencyKey);
        assertEquals(1, orderCount);
    }

    @Test
    @Order(3)
    void createDraftOrderConcurrentTwoConnectionsSameKeyReplaySafely() throws Exception {
        String token = validTokenFor(SUB_STORE_A_CASHIER);
        UUID concurrentKey = UUID.fromString("22222222-2222-4222-8222-000000000502");

        String payload = """
                {
                  "store_id": "%s",
                  "warehouse_id": "%s",
                  "items": [
                    {"product_id": "%s", "quantity": 1}
                  ]
                }
                """.formatted(STORE_A_ID, WAREHOUSE_A_ID, PRODUCT_A_ID);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<String> task = () -> {
                MvcResult res = mockMvc.perform(post("/api/v1/pos/orders")
                                .header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", concurrentKey.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                        .andReturn();
                int status = res.getResponse().getStatus();
                assertTrue(status == 200 || status == 201, "Expected 200 or 201 but got " + status);
                JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
                return node.get("order_id").asString();
            };

            Future<String> f1 = executor.submit(task);
            Future<String> f2 = executor.submit(task);

            String orderId1 = f1.get();
            String orderId2 = f2.get();

            assertNotNull(orderId1);
            assertEquals(orderId1, orderId2, "Both concurrent requests must return identical order_id");

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM sasms.sales_orders WHERE idempotency_key = CAST(? AS uuid)",
                    Integer.class, concurrentKey);
            assertEquals(1, count, "Only 1 order record must be inserted in DB");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @Order(4)
    void createDraftOrderReplayDifferentPayloadReturns409Conflict() throws Exception {
        String token = validTokenFor(SUB_STORE_A_CASHIER);
        UUID conflictKey = UUID.fromString("22222222-2222-4222-8222-000000000503");

        String initialPayload = """
                {
                  "store_id": "%s",
                  "warehouse_id": "%s",
                  "items": [
                    {"product_id": "%s", "quantity": 1}
                  ]
                }
                """.formatted(STORE_A_ID, WAREHOUSE_A_ID, PRODUCT_A_ID);

        mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", conflictKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initialPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order_id").isString());

        // Replay with DIFFERENT quantity (2 instead of 1) -> 409 CONFLICT
        String differentPayload = """
                {
                  "store_id": "%s",
                  "warehouse_id": "%s",
                  "items": [
                    {"product_id": "%s", "quantity": 2}
                  ]
                }
                """.formatted(STORE_A_ID, WAREHOUSE_A_ID, PRODUCT_A_ID);

        mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", conflictKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(differentPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @Order(5)
    void createDraftOrderAtomicRollbackOnConstraintFailure() throws Exception {
        String token = validTokenFor(SUB_STORE_A_CASHIER);
        UUID rollbackKey = UUID.fromString("22222222-2222-4222-8222-000000000504");
        UUID nonExistentProduct = UUID.fromString("22222222-2222-4222-8222-999999999999");

        // 1. Database-level mid-transaction rollback proof:
        // When inserting multi-item order where item 1 succeeds but item 2 fails on foreign key
        // sales_order_items_product_id_fkey, the @Transactional boundary must roll back both sales_orders and sales_order_items.
        UUID midTxOrderId = UUID.fromString("22222222-2222-4222-8222-000000000599");
        UUID midTxKey = UUID.fromString("22222222-2222-4222-8222-000000000598");
        List<PosOrderItemRecord> itemsWithFkFailure = List.of(
                new PosOrderItemRecord(UUID.randomUUID(), midTxOrderId, PRODUCT_A_ID, "S2-PROD-A", "S2 Product A", 1, new BigDecimal("10000000.00"), new BigDecimal("10000000.00")),
                new PosOrderItemRecord(UUID.randomUUID(), midTxOrderId, nonExistentProduct, "NON-EXISTENT", "Missing", 1, new BigDecimal("5000000.00"), new BigDecimal("5000000.00"))
        );

        assertThrows(DataAccessException.class, () ->
                dataAccess.createOrderIfAbsent(
                        midTxOrderId,
                        "S2-ORD-TX-FAIL",
                        midTxKey,
                        STORE_A_ID,
                        WAREHOUSE_A_ID,
                        null,
                        ACTOR_STORE_A_CASHIER,
                        itemsWithFkFailure
                )
        );

        Integer midTxOrderCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sasms.sales_orders WHERE id = CAST(? AS uuid) OR idempotency_key = CAST(? AS uuid)",
                Integer.class, midTxOrderId, midTxKey);
        assertEquals(0, midTxOrderCount, "sales_orders must roll back completely when item insert fails mid-transaction");

        Integer midTxItemCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sasms.sales_order_items WHERE order_id = CAST(? AS uuid)",
                Integer.class, midTxOrderId);
        assertEquals(0, midTxItemCount, "sales_order_items must have 0 rows after mid-transaction rollback");

        // 2. Controller-level validation rejecting invalid product payload (HTTP 404 NOT_FOUND)
        String invalidPayload = """
                {
                  "store_id": "%s",
                  "warehouse_id": "%s",
                  "items": [
                    {"product_id": "%s", "quantity": 1}
                  ]
                }
                """.formatted(STORE_A_ID, WAREHOUSE_A_ID, nonExistentProduct);

        mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", rollbackKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // Verify that no order was left committed in database
        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sasms.sales_orders WHERE idempotency_key = CAST(? AS uuid)",
                Integer.class, rollbackKey);
        assertEquals(0, orderCount, "Order creation must roll back atomically when item fails");
    }

    @Test
    @Order(6)
    void createDraftOrderScopeEnforcement() throws Exception {
        String storeASalesToken = validTokenFor(SUB_STORE_A_SALES);
        String storeBCashierToken = validTokenFor(SUB_STORE_B_CASHIER);
        String unassignedToken = validTokenFor(SUB_UNASSIGNED);
        String inactiveToken = validTokenFor(SUB_INACTIVE);

        String storeAPayload = """
                {
                  "store_id": "%s",
                  "warehouse_id": "%s",
                  "items": [
                    {"product_id": "%s", "quantity": 1}
                  ]
                }
                """.formatted(STORE_A_ID, WAREHOUSE_A_ID, PRODUCT_A_ID);

        // 1. Sales staff (no sales.checkout permission) -> 403 FORBIDDEN
        mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer " + storeASalesToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(storeAPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 2. Cashier of Store B attempting to create order in Store A -> 403 FORBIDDEN
        mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer " + storeBCashierToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(storeAPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 3. Store A cashier passing Warehouse B (belongs to Store B) -> 422
        String invalidWarehousePayload = """
                {
                  "store_id": "%s",
                  "warehouse_id": "%s",
                  "items": [
                    {"product_id": "%s", "quantity": 1}
                  ]
                }
                """.formatted(STORE_A_ID, WAREHOUSE_B_ID, PRODUCT_A_ID);

        mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer " + validTokenFor(SUB_STORE_A_CASHIER))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidWarehousePayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // 4. Unassigned actor -> 403 FORBIDDEN
        mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer " + unassignedToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(storeAPayload))
                .andExpect(status().isForbidden());

        // 5. Inactive actor -> 401 UNAUTHORIZED
        mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer " + inactiveToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(storeAPayload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(7)
    void reserveOrderDeviceHoldAndLease() throws Exception {
        String token = validTokenFor(SUB_STORE_A_CASHIER);
        UUID reserveOrderKey = UUID.fromString("22222222-2222-4222-8222-000000000505");

        String payload = """
                {
                  "store_id": "%s",
                  "warehouse_id": "%s",
                  "items": [
                    {"product_id": "%s", "quantity": 1}
                  ]
                }
                """.formatted(STORE_A_ID, WAREHOUSE_A_ID, PRODUCT_A_ID);

        MvcResult draftResult = mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", reserveOrderKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode draftNode = objectMapper.readTree(draftResult.getResponse().getContentAsString());
        UUID orderId = UUID.fromString(draftNode.get("order_id").asString());
        UUID orderItemId = UUID.fromString(draftNode.get("items").get(0).get("order_item_id").asString());

        // Attach device to order item via order_devices to satisfy reserve_order device count
        UUID orderDeviceId = UUID.fromString("22222222-2222-4222-8222-000000000601");
        jdbcTemplate.update(
                """
                INSERT INTO sasms.order_devices(id, order_item_id, order_id, product_id, device_id, active)
                VALUES (?, ?, ?, ?, ?, true)
                """,
                orderDeviceId, orderItemId, orderId, PRODUCT_A_ID, DEVICE_A_1_ID);

        // Call Reserve API
        mockMvc.perform(post("/api/v1/pos/orders/{id}/reserve", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order_id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.total_amount").value(10000000.00));

        // Verify order and device status in database
        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM sasms.sales_orders WHERE id = CAST(? AS uuid)",
                String.class, orderId);
        assertEquals("RESERVED", orderStatus);

        String devStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM sasms.devices WHERE id = CAST(? AS uuid)",
                String.class, DEVICE_A_1_ID);
        assertEquals("RESERVED", devStatus);

        // Calling reserve again on already RESERVED order is idempotent
        mockMvc.perform(post("/api/v1/pos/orders/{id}/reserve", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order_id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    private String validTokenFor(String subject) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(subject)
                .issuedAt(now.minusSeconds(2))
                .notBefore(now.minusSeconds(2))
                .expiresAt(now.plusSeconds(300))
                .claim("roles", List.of("CASHIER"))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY_ID).build(),
                claims)).getTokenValue();
    }

    private static void assertSafeIsolatedTarget(String jdbcUrl, String appUser) {
        assertTrue(jdbcUrl.startsWith("jdbc:postgresql://"), "Only a PostgreSQL JDBC URL is allowed");
        URI target = URI.create(jdbcUrl.substring("jdbc:".length()));
        assertEquals("postgresql", target.getScheme());
        assertTrue(Set.of("127.0.0.1", "localhost", "::1").contains(target.getHost()),
                "Integration test is restricted to loopback PostgreSQL host");
        assertEquals("/sasms", target.getPath(), "Integration test is restricted to database sasms");
        assertTrue(appUser.startsWith("tv4_s2_app_login") || appUser.startsWith("sasms_app"),
                "Runtime user must be an isolated TV4 login");
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        assumeTrue(value != null && !value.isBlank(), () -> name + " is required");
        return value;
    }

    private static String scalarString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next(), "Expected one result row");
            return result.getString(1);
        }
    }

    private static void assertInsufficientPrivilege(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected DataAccessException with SQLSTATE 42501 (insufficient privilege)");
        } catch (DataAccessException ex) {
            Throwable root = ex.getMostSpecificCause();
            String combined = ((ex.getMessage() != null ? ex.getMessage() : "") + " "
                    + (root != null && root.getMessage() != null ? root.getMessage() : "")).toLowerCase();
            String extracted = null;
            if (root instanceof SQLException sqlEx) {
                extracted = sqlEx.getSQLState();
            } else if (ex.getCause() instanceof SQLException sqlEx) {
                extracted = sqlEx.getSQLState();
            }
            final String sqlState = extracted;
            assertTrue(
                    "42501".equals(sqlState)
                            || combined.contains("42501")
                            || combined.contains("permission denied")
                            || combined.contains("must be owner"),
                    () -> "Expected 42501/permission denied but got: " + ex.getMessage() + " (root: " + (root != null ? root.getMessage() : "null") + ", sqlstate: " + sqlState + ")");
        }
    }

    private static void cleanupFixtures(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    UPDATE sasms.devices
                    SET status = 'IN_STOCK', reserved_order_id = NULL, sold_order_id = NULL, sold_at = NULL
                    WHERE id IN (
                        '22222222-2222-4222-8222-000000000401', '22222222-2222-4222-8222-000000000402'
                    );
                    UPDATE sasms.sales_orders
                    SET status = 'DRAFT', total_amount = 0, paid_at = NULL
                    WHERE store_id IN (
                        '22222222-2222-4222-8222-000000000201', '22222222-2222-4222-8222-000000000202'
                    );
                    DELETE FROM sasms.payments WHERE order_id IN (
                        SELECT id FROM sasms.sales_orders WHERE store_id IN (
                            '22222222-2222-4222-8222-000000000201', '22222222-2222-4222-8222-000000000202'
                        )
                    );
                    DELETE FROM sasms.order_devices WHERE order_id IN (
                        SELECT id FROM sasms.sales_orders WHERE store_id IN (
                            '22222222-2222-4222-8222-000000000201', '22222222-2222-4222-8222-000000000202'
                        )
                    ) OR device_id IN (
                        '22222222-2222-4222-8222-000000000401', '22222222-2222-4222-8222-000000000402'
                    );
                    DELETE FROM sasms.sales_order_items WHERE order_id IN (
                        SELECT id FROM sasms.sales_orders WHERE store_id IN (
                            '22222222-2222-4222-8222-000000000201', '22222222-2222-4222-8222-000000000202'
                        )
                    );
                    DELETE FROM sasms.inventory_movements WHERE warehouse_id IN (
                        '22222222-2222-4222-8222-000000000211', '22222222-2222-4222-8222-000000000212'
                    ) OR device_id IN (
                        '22222222-2222-4222-8222-000000000401', '22222222-2222-4222-8222-000000000402'
                    );
                    DELETE FROM sasms.outbox_events WHERE aggregate_id IN (
                        SELECT id FROM sasms.sales_orders WHERE store_id IN (
                            '22222222-2222-4222-8222-000000000201', '22222222-2222-4222-8222-000000000202'
                        )
                    ) OR aggregate_id IN (
                        '22222222-2222-4222-8222-000000000401', '22222222-2222-4222-8222-000000000402'
                    );
                    DELETE FROM sasms.audit_logs WHERE actor_id IN (
                        '22222222-2222-4222-8222-000000000101', '22222222-2222-4222-8222-000000000102',
                        '22222222-2222-4222-8222-000000000103', '22222222-2222-4222-8222-000000000104',
                        '22222222-2222-4222-8222-000000000105'
                    ) OR (entity_type = 'sales_order' AND entity_id IN (
                        SELECT id FROM sasms.sales_orders WHERE store_id IN (
                            '22222222-2222-4222-8222-000000000201', '22222222-2222-4222-8222-000000000202'
                        )
                    ));
                    DELETE FROM sasms.sales_orders WHERE store_id IN (
                        '22222222-2222-4222-8222-000000000201', '22222222-2222-4222-8222-000000000202'
                    );
                    DELETE FROM sasms.devices WHERE warehouse_id IN (
                        '22222222-2222-4222-8222-000000000211', '22222222-2222-4222-8222-000000000212'
                    ) OR id IN (
                        '22222222-2222-4222-8222-000000000401', '22222222-2222-4222-8222-000000000402'
                    );
                    DELETE FROM sasms.goods_receipt_items WHERE id IN (
                        '22222222-2222-4222-8222-000000000061', '22222222-2222-4222-8222-000000000062'
                    );
                    DELETE FROM sasms.goods_receipts WHERE id = '22222222-2222-4222-8222-000000000060';
                    DELETE FROM sasms.purchase_order_items WHERE id IN (
                        '22222222-2222-4222-8222-000000000051', '22222222-2222-4222-8222-000000000052'
                    );
                    DELETE FROM sasms.purchase_orders WHERE id = '22222222-2222-4222-8222-000000000050';
                    DELETE FROM sasms.suppliers WHERE id = '22222222-2222-4222-8222-000000000040';
                    DELETE FROM sasms.products WHERE id IN (
                        '22222222-2222-4222-8222-000000000301', '22222222-2222-4222-8222-000000000302'
                    );
                    DELETE FROM sasms.categories WHERE id = '22222222-2222-4222-8222-000000000021';
                    DELETE FROM sasms.brands WHERE id = '22222222-2222-4222-8222-000000000020';
                    DELETE FROM sasms.user_roles WHERE user_id IN (
                        '22222222-2222-4222-8222-000000000101', '22222222-2222-4222-8222-000000000102',
                        '22222222-2222-4222-8222-000000000103', '22222222-2222-4222-8222-000000000104',
                        '22222222-2222-4222-8222-000000000105'
                    );
                    DELETE FROM sasms.warehouses WHERE id IN (
                        '22222222-2222-4222-8222-000000000211', '22222222-2222-4222-8222-000000000212'
                    );
                    DELETE FROM sasms.stores WHERE id IN (
                        '22222222-2222-4222-8222-000000000201', '22222222-2222-4222-8222-000000000202'
                    );
                    DELETE FROM sasms.users WHERE id IN (
                        '22222222-2222-4222-8222-000000000101', '22222222-2222-4222-8222-000000000102',
                        '22222222-2222-4222-8222-000000000103', '22222222-2222-4222-8222-000000000104',
                        '22222222-2222-4222-8222-000000000105'
                    );
                    """);
        }
    }

    private static void seedFixtures(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    INSERT INTO sasms.brands(id, name) VALUES
                        ('22222222-2222-4222-8222-000000000020', 'S2 Test Brand');

                    INSERT INTO sasms.categories(id, name) VALUES
                        ('22222222-2222-4222-8222-000000000021', 'S2 Test Category');

                    INSERT INTO sasms.stores(id, code, name, address, is_active) VALUES
                        ('22222222-2222-4222-8222-000000000201', 'S2-STORE-A', 'S2 Store A', 'Store A Address', true),
                        ('22222222-2222-4222-8222-000000000202', 'S2-STORE-B', 'S2 Store B', 'Store B Address', true);

                    INSERT INTO sasms.warehouses(id, store_id, code, name) VALUES
                        ('22222222-2222-4222-8222-000000000211', '22222222-2222-4222-8222-000000000201', 'S2-WH-A', 'S2 Warehouse A'),
                        ('22222222-2222-4222-8222-000000000212', '22222222-2222-4222-8222-000000000202', 'S2-WH-B', 'S2 Warehouse B');

                    INSERT INTO sasms.users(id, email, full_name, auth_subject, is_active) VALUES
                        ('22222222-2222-4222-8222-000000000101', 's2-cashier-a@sasms.test', 'S2 Cashier A', 'urn:sasms:s2-it:store-a-cashier', true),
                        ('22222222-2222-4222-8222-000000000102', 's2-sales-a@sasms.test', 'S2 Sales A', 'urn:sasms:s2-it:store-a-sales', true),
                        ('22222222-2222-4222-8222-000000000103', 's2-cashier-b@sasms.test', 'S2 Cashier B', 'urn:sasms:s2-it:store-b-cashier', true),
                        ('22222222-2222-4222-8222-000000000104', 's2-inactive@sasms.test', 'S2 Inactive', 'urn:sasms:s2-it:inactive', false),
                        ('22222222-2222-4222-8222-000000000105', 's2-unassigned@sasms.test', 'S2 Unassigned', 'urn:sasms:s2-it:unassigned', true);

                    INSERT INTO sasms.user_roles(user_id, role_code, store_id) VALUES
                        ('22222222-2222-4222-8222-000000000101', 'CASHIER', '22222222-2222-4222-8222-000000000201'),
                        ('22222222-2222-4222-8222-000000000102', 'SALES', '22222222-2222-4222-8222-000000000201'),
                        ('22222222-2222-4222-8222-000000000103', 'CASHIER', '22222222-2222-4222-8222-000000000202');

                    INSERT INTO sasms.products(id, sku, brand_id, category_id, name, specifications, list_price, warranty_months, is_active) VALUES
                        ('22222222-2222-4222-8222-000000000301', 'S2-PROD-A', '22222222-2222-4222-8222-000000000020', '22222222-2222-4222-8222-000000000021', 'S2 Product A', '{}'::jsonb, 10000000.00, 12, true),
                        ('22222222-2222-4222-8222-000000000302', 'S2-PROD-B', '22222222-2222-4222-8222-000000000020', '22222222-2222-4222-8222-000000000021', 'S2 Product B', '{}'::jsonb, 5000000.00, 12, true);

                    INSERT INTO sasms.suppliers(id, code, name) VALUES
                        ('22222222-2222-4222-8222-000000000040', 'S2-SUP-01', 'S2 Test Supplier');

                    INSERT INTO sasms.purchase_orders(id, code, supplier_id, warehouse_id, status, created_by) VALUES
                        ('22222222-2222-4222-8222-000000000050', 'S2-PO-01', '22222222-2222-4222-8222-000000000040', '22222222-2222-4222-8222-000000000211', 'APPROVED', '22222222-2222-4222-8222-000000000101');

                    INSERT INTO sasms.purchase_order_items(id, purchase_order_id, product_id, quantity, unit_cost) VALUES
                        ('22222222-2222-4222-8222-000000000051', '22222222-2222-4222-8222-000000000050', '22222222-2222-4222-8222-000000000301', 50, 8000000.00),
                        ('22222222-2222-4222-8222-000000000052', '22222222-2222-4222-8222-000000000050', '22222222-2222-4222-8222-000000000302', 50, 4000000.00);

                    INSERT INTO sasms.goods_receipts(id, code, purchase_order_id, warehouse_id, status, received_by) VALUES
                        ('22222222-2222-4222-8222-000000000060', 'S2-GR-01', '22222222-2222-4222-8222-000000000050', '22222222-2222-4222-8222-000000000211', 'OPEN', '22222222-2222-4222-8222-000000000101');

                    INSERT INTO sasms.goods_receipt_items(id, receipt_id, purchase_order_id, purchase_order_item_id, product_id, quantity) VALUES
                        ('22222222-2222-4222-8222-000000000061', '22222222-2222-4222-8222-000000000060', '22222222-2222-4222-8222-000000000050', '22222222-2222-4222-8222-000000000051', '22222222-2222-4222-8222-000000000301', 50),
                        ('22222222-2222-4222-8222-000000000062', '22222222-2222-4222-8222-000000000060', '22222222-2222-4222-8222-000000000050', '22222222-2222-4222-8222-000000000052', '22222222-2222-4222-8222-000000000302', 50);

                    INSERT INTO sasms.devices(id, warehouse_id, product_id, receipt_item_id, serial, qr_token, status) VALUES
                        ('22222222-2222-4222-8222-000000000401', '22222222-2222-4222-8222-000000000211', '22222222-2222-4222-8222-000000000301', '22222222-2222-4222-8222-000000000061', 'S2-SER-0001', '22222222-2222-4222-8222-000000000411', 'IN_STOCK'),
                        ('22222222-2222-4222-8222-000000000402', '22222222-2222-4222-8222-000000000211', '22222222-2222-4222-8222-000000000301', '22222222-2222-4222-8222-000000000061', 'S2-SER-0002', '22222222-2222-4222-8222-000000000412', 'IN_STOCK');
                    """);
        }
    }
}
