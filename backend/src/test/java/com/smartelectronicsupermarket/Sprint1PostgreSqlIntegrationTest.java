package com.smartelectronicsupermarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

@SpringBootTest
@ActiveProfiles("postgresql")
@EnabledIfEnvironmentVariable(named = "SASMS_S1_PG_IT_ENABLED", matches = "(?i)true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Sprint1PostgreSqlIntegrationTest {

    private static final String ENABLE_ENV = "SASMS_S1_PG_IT_ENABLED";
    private static final String URL_ENV = "SASMS_DB_URL";
    private static final String USERNAME_ENV = "SASMS_DB_USERNAME";
    private static final String PASSWORD_ENV = "SASMS_DB_PASSWORD";
    private static final String ADMIN_USERNAME_ENV = "SASMS_IT_ADMIN_USERNAME";
    private static final String ADMIN_PASSWORD_ENV = "SASMS_IT_ADMIN_PASSWORD";

    private static final String ISSUER = "https://idp.sasms.internal.test";
    private static final String AUDIENCE = "sasms-api";
    private static final String KEY_ID = "tv4-s1-it-rsa-key";

    // Seeded brand & category from database/seed.sql
    private static final UUID SEEDED_BRAND_ID = UUID.fromString("00000000-0000-4000-8000-000000000020");
    private static final UUID SEEDED_CATEGORY_ID = UUID.fromString("00000000-0000-4000-8000-000000000021");

    // Isolated Sprint 1 integration fixture UUIDs
    private static final UUID ACTOR_GLOBAL_ADMIN = UUID.fromString("11111111-1111-4111-8111-000000000101");
    private static final UUID ACTOR_GLOBAL_ANALYST = UUID.fromString("11111111-1111-4111-8111-000000000102");
    private static final UUID ACTOR_STORE_A_WAREHOUSE = UUID.fromString("11111111-1111-4111-8111-000000000103");
    private static final UUID ACTOR_STORE_A_SALES = UUID.fromString("11111111-1111-4111-8111-000000000104");
    private static final UUID ACTOR_INACTIVE_ADMIN = UUID.fromString("11111111-1111-4111-8111-000000000105");
    private static final UUID ACTOR_UNASSIGNED = UUID.fromString("11111111-1111-4111-8111-000000000106");

    private static final String SUB_GLOBAL_ADMIN = "urn:sasms:s1-it:global-admin";
    private static final String SUB_GLOBAL_ANALYST = "urn:sasms:s1-it:global-analyst";
    private static final String SUB_STORE_A_WAREHOUSE = "urn:sasms:s1-it:store-a-warehouse";
    private static final String SUB_STORE_A_SALES = "urn:sasms:s1-it:store-a-sales";
    private static final String SUB_INACTIVE_ADMIN = "urn:sasms:s1-it:inactive-admin";
    private static final String SUB_UNASSIGNED = "urn:sasms:s1-it:unassigned";

    private static final UUID STORE_A_ID = UUID.fromString("11111111-1111-4111-8111-000000000201");
    private static final UUID STORE_B_ID = UUID.fromString("11111111-1111-4111-8111-000000000202");
    private static final UUID WAREHOUSE_A_ID = UUID.fromString("11111111-1111-4111-8111-000000000211");
    private static final UUID WAREHOUSE_B_ID = UUID.fromString("11111111-1111-4111-8111-000000000212");
    private static final UUID SUPPLIER_ID = UUID.fromString("11111111-1111-4111-8111-000000000301");
    private static final UUID FIXTURE_PRODUCT_ID = UUID.fromString("11111111-1111-4111-8111-000000000401");
    private static final UUID PO_A_ID = UUID.fromString("11111111-1111-4111-8111-000000000501");
    private static final UUID PO_ITEM_A_ID = UUID.fromString("11111111-1111-4111-8111-000000000502");
    private static final UUID RECEIPT_A_ID = UUID.fromString("11111111-1111-4111-8111-000000000503");
    private static final UUID RECEIPT_ITEM_A_ID = UUID.fromString("11111111-1111-4111-8111-000000000504");

    private static final UUID PO_B_ID = UUID.fromString("11111111-1111-4111-8111-000000000601");
    private static final UUID PO_ITEM_B_ID = UUID.fromString("11111111-1111-4111-8111-000000000602");
    private static final UUID RECEIPT_B_ID = UUID.fromString("11111111-1111-4111-8111-000000000603");
    private static final UUID RECEIPT_ITEM_B_ID = UUID.fromString("11111111-1111-4111-8111-000000000604");
    private static final UUID STORE_B_BATCH_KEY = UUID.fromString("11111111-1111-4111-8111-000000000605");

    private static final UUID BATCH_REQ_KEY_1 = UUID.fromString("11111111-1111-4111-8111-000000000701");
    private static final UUID BATCH_REQ_KEY_ROLLBACK = UUID.fromString("11111111-1111-4111-8111-000000000702");

    private static HttpServer jwksServer;
    private static String jwksUri;
    private static JwtEncoder jwtEncoder;
    private static JwtEncoder wrongKeyEncoder;

    private static UUID createdCatalogProductId;
    private static UUID receivedStoreADevice1Id;
    private static UUID receivedStoreBDeviceId;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        ensureJwksServerStarted();
        registry.add("sasms.jwt.issuer", () -> ISSUER);
        registry.add("sasms.jwt.audience", () -> AUDIENCE);
        registry.add("sasms.jwt.jwks-uri", () -> jwksUri);
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

            KeyPair wrongPair = generator.generateKeyPair();
            RSAKey wrongJwk = new RSAKey.Builder((RSAPublicKey) wrongPair.getPublic())
                    .privateKey((RSAPrivateKey) wrongPair.getPrivate())
                    .keyID(KEY_ID)
                    .build();
            wrongKeyEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(wrongJwk)));

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
                () -> "Sprint 1 PostgreSQL integration test is disabled; set " + ENABLE_ENV + "=true to run it");

        String jdbcUrl = requiredEnv(URL_ENV);
        String appUser = requiredEnv(USERNAME_ENV);
        String appPassword = requiredEnv(PASSWORD_ENV);
        String adminUser = requiredEnv(ADMIN_USERNAME_ENV);
        String adminPassword = requiredEnv(ADMIN_PASSWORD_ENV);

        assertSafeIsolatedTarget(jdbcUrl, appUser);

        try (Connection adminConn = DriverManager.getConnection(jdbcUrl, adminUser, adminPassword)) {
            try (Statement verifyStmt = adminConn.createStatement();
                    ResultSet rs = verifyStmt.executeQuery(
                            "SELECT count(*) FROM sasms_meta.schema_migrations WHERE version IN (1, 2)")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1), "Both V001 and V002 migrations must be applied in sasms_meta.schema_migrations");
            }
            adminConn.setAutoCommit(false);
            try {
                cleanupTestArtifacts(adminConn);
                seedIsolatedFixtures(adminConn);
                adminConn.commit();
            } catch (SQLException ex) {
                adminConn.rollback();
                throw ex;
            }
        }

        // Verify runtime app credentials can connect before running suite
        try (Connection appConn = DriverManager.getConnection(jdbcUrl, appUser, appPassword)) {
            assertEquals("sasms", scalarString(appConn, "SELECT current_database()"));
        }
    }

    @AfterAll
    static void tearDownIsolatedFixturesAndJwksServer() throws SQLException {
        try {
            if ("true".equalsIgnoreCase(System.getenv(ENABLE_ENV))) {
                String jdbcUrl = requiredEnv(URL_ENV);
                String adminUser = requiredEnv(ADMIN_USERNAME_ENV);
                String adminPassword = requiredEnv(ADMIN_PASSWORD_ENV);
                try (Connection adminConn = DriverManager.getConnection(jdbcUrl, adminUser, adminPassword)) {
                    adminConn.setAutoCommit(false);
                    try {
                        cleanupTestArtifacts(adminConn);
                        adminConn.commit();
                    } catch (SQLException exception) {
                        adminConn.rollback();
                        throw exception;
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
                () -> "Sprint 1 PostgreSQL integration test is disabled; set " + ENABLE_ENV + "=true to run it");
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
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'sasms' AND table_type = 'BASE TABLE'",
                Integer.class);

        assertNotNull(currentUser);
        assertFalse("sasms_owner".equalsIgnoreCase(currentUser), "Runtime must not connect as sasms_owner");
        assertFalse("postgres".equalsIgnoreCase(currentUser), "Runtime must not connect as postgres");
        assertEquals("off", isSuperuser);
        assertEquals(Boolean.TRUE, isMember);
        assertEquals(39, tableCount);

        assertInsufficientPrivilege(() -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sasms_meta.schema_migrations", Integer.class));

        assertInsufficientPrivilege(() -> jdbcTemplate.update(
                "INSERT INTO sasms.products(sku, brand_id, category_id, name, list_price, warranty_months) VALUES ('BYPASS', ?, ?, 'Bypass', 100, 12)",
                SEEDED_BRAND_ID, SEEDED_CATEGORY_ID));
        assertInsufficientPrivilege(() -> jdbcTemplate.update(
                "UPDATE sasms.products SET list_price = 1 WHERE id = ?", FIXTURE_PRODUCT_ID));
        assertInsufficientPrivilege(() -> jdbcTemplate.update(
                "DELETE FROM sasms.products WHERE id = ?", FIXTURE_PRODUCT_ID));
        assertInsufficientPrivilege(() -> jdbcTemplate.update(
                "INSERT INTO sasms.user_roles(user_id, role_code) VALUES (?, 'ADMIN')", ACTOR_UNASSIGNED));
        assertInsufficientPrivilege(() -> jdbcTemplate.update(
                "UPDATE sasms.devices SET status = 'SOLD' WHERE id = ?", receivedStoreBDeviceId));
        assertInsufficientPrivilege(() -> jdbcTemplate.update(
                "INSERT INTO sasms.audit_logs(actor_id, action, entity_type, entity_id) VALUES (?, 'forged', 'product', ?)",
                ACTOR_GLOBAL_ADMIN, FIXTURE_PRODUCT_ID));
        assertInsufficientPrivilege(() -> jdbcTemplate.queryForObject(
                "SELECT sasms.receive_device_unchecked_s0(?, 'BYPASS-SER', NULL, ?)",
                UUID.class, RECEIPT_ITEM_A_ID, ACTOR_STORE_A_WAREHOUSE));
    }

    @Test
    @Order(2)
    void jwtSecurityRejectsUnauthenticatedInvalidInactiveAndForgedRequestsOverHttp() throws Exception {
        // Public status endpoints remain accessible
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/pos/status")).andExpect(status().isOk());
        mockMvc.perform(get("/api/serial-imei/status")).andExpect(status().isOk());

        // Missing token -> 401 with WWW-Authenticate: Bearer and correlation_id
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.correlation_id").isString());

        // Wrong RSA signature
        String wrongSigToken = issueToken(
                wrongKeyEncoder, ISSUER, AUDIENCE, SUB_GLOBAL_ADMIN,
                Instant.now().minusSeconds(5), Instant.now().plusSeconds(300));
        assertUnauthorizedGet("/api/v1/products", wrongSigToken);

        // Expired token
        String expiredToken = issueToken(
                jwtEncoder, ISSUER, AUDIENCE, SUB_GLOBAL_ADMIN,
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(60));
        assertUnauthorizedGet("/api/v1/products", expiredToken);

        // Future nbf
        String futureNbfToken = issueToken(
                jwtEncoder, ISSUER, AUDIENCE, SUB_GLOBAL_ADMIN,
                Instant.now().plusSeconds(300), Instant.now().plusSeconds(600));
        assertUnauthorizedGet("/api/v1/products", futureNbfToken);

        // Wrong issuer
        String wrongIssuerToken = issueToken(
                jwtEncoder, "https://wrong-issuer.example.test", AUDIENCE, SUB_GLOBAL_ADMIN,
                Instant.now().minusSeconds(5), Instant.now().plusSeconds(300));
        assertUnauthorizedGet("/api/v1/products", wrongIssuerToken);

        // Wrong audience
        String wrongAudienceToken = issueToken(
                jwtEncoder, ISSUER, "wrong-audience", SUB_GLOBAL_ADMIN,
                Instant.now().minusSeconds(5), Instant.now().plusSeconds(300));
        assertUnauthorizedGet("/api/v1/products", wrongAudienceToken);

        // Unknown auth_subject in sasms.users
        String unknownSubjectToken = issueToken(
                jwtEncoder, ISSUER, AUDIENCE, "urn:sasms:s1-it:non-existent-subject",
                Instant.now().minusSeconds(5), Instant.now().plusSeconds(300));
        assertUnauthorizedGet("/api/v1/products", unknownSubjectToken);

        // Inactive user in sasms.users (even with ADMIN role assigned in sasms.user_roles) -> 401
        String inactiveAdminToken = validTokenFor(SUB_INACTIVE_ADMIN);
        assertUnauthorizedGet("/api/v1/products", inactiveAdminToken);

        // Active unassigned user with forged X-Actor-Id header and forged ADMIN role claim -> 403
        String unassignedToken = validTokenFor(SUB_UNASSIGNED);
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + unassignedToken)
                        .header("X-Actor-Id", ACTOR_GLOBAL_ADMIN.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlation_id").isString());
    }

    @Test
    @Order(3)
    void catalogCrudSoftDeleteRevisionConflictDuplicateSkuAndPermissionsWorkOnRealDb() throws Exception {
        String adminToken = validTokenFor(SUB_GLOBAL_ADMIN);
        String analystToken = validTokenFor(SUB_GLOBAL_ANALYST);
        String warehouseToken = validTokenFor(SUB_STORE_A_WAREHOUSE);

        // 1. Read-only global ANALYST cannot create product -> 403 FORBIDDEN
        String createPayload = """
                {
                  "sku": "  it-s1-oled-65  ",
                  "brand_id": "%s",
                  "category_id": "%s",
                  "name": "  TV OLED 65 S1 Integration  ",
                  "specifications": {"panel": "OLED", "size_inch": 65},
                  "price": 29990000.00,
                  "warranty_months": 24
                }
                """.formatted(SEEDED_BRAND_ID, SEEDED_CATEGORY_ID);

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + analystToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Store-scoped user cannot write shared catalog -> 403 FORBIDDEN
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + warehouseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 2. Global ADMIN creates product -> 201 CREATED
        MvcResult createResult = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.product_id").isString())
                .andExpect(jsonPath("$.revision").value(0))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        createdCatalogProductId = UUID.fromString(createdJson.get("product_id").asString());

        // 3. Duplicate SKU (case-insensitive after trigger normalization) -> 409 DUPLICATE
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));

        // 4. Read by ID and List with global ANALYST (catalog.read) -> 200 OK
        mockMvc.perform(get("/api/v1/products/{productId}", createdCatalogProductId)
                        .header("Authorization", "Bearer " + analystToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product_id").value(createdCatalogProductId.toString()))
                .andExpect(jsonPath("$.sku").value("IT-S1-OLED-65"))
                .andExpect(jsonPath("$.name").value("TV OLED 65 S1 Integration"))
                .andExpect(jsonPath("$.specifications.panel").value("OLED"))
                .andExpect(jsonPath("$.specifications.size_inch").value(65))
                .andExpect(jsonPath("$.warranty_months").value(24))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.revision").value(0));

        mockMvc.perform(get("/api/v1/products")
                        .param("query", "IT-S1-OLED-65")
                        .param("active", "true")
                        .header("Authorization", "Bearer " + analystToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].product_id").value(createdCatalogProductId.toString()));

        // 5. Update product with expected_revision = 0 -> 200 OK, revision = 1
        String updatePayload = """
                {
                  "expected_revision": 0,
                  "name": "TV OLED 65 S1 Integration V2",
                  "specifications": {"panel": "OLED-EVO", "size_inch": 65, "refresh_hz": 120},
                  "price": 27990000.00,
                  "warranty_months": 36,
                  "active": true
                }
                """;
        mockMvc.perform(put("/api/v1/products/{productId}", createdCatalogProductId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product_id").value(createdCatalogProductId.toString()))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.active").value(true));

        // 6. Revision conflict on stale expected_revision = 0 -> 409 REVISION_CONFLICT
        mockMvc.perform(put("/api/v1/products/{productId}", createdCatalogProductId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVISION_CONFLICT"));

        // 7. Soft delete via DELETE ?expected_revision=1 -> 200 OK, revision = 2, active = false
        mockMvc.perform(delete("/api/v1/products/{productId}", createdCatalogProductId)
                        .param("expected_revision", "1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product_id").value(createdCatalogProductId.toString()))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.active").value(false));

        // Verify soft delete retained the row in sasms.products with is_active=false and revision=2
        Boolean isActiveInDb = jdbcTemplate.queryForObject(
                "SELECT is_active FROM sasms.products WHERE id = ?",
                Boolean.class, createdCatalogProductId);
        Integer revisionInDb = jdbcTemplate.queryForObject(
                "SELECT revision FROM sasms.products WHERE id = ?",
                Integer.class, createdCatalogProductId);
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sasms.audit_logs WHERE entity_id = ? AND action IN ('catalog.create', 'catalog.update')",
                Integer.class, createdCatalogProductId);

        assertEquals(Boolean.FALSE, isActiveInDb);
        assertEquals(2, revisionInDb);
        assertEquals(3, auditCount);
    }

    @Test
    @Order(4)
    void inventoryBatchImportIdempotencyOrderingRollbackAndScopeWorkOnRealDb() throws Exception {
        String warehouseStoreAToken = validTokenFor(SUB_STORE_A_WAREHOUSE);
        String salesStoreAToken = validTokenFor(SUB_STORE_A_SALES);

        // 1. Successful batch import of 3 devices into Store A receipt item; verify UUID[] ordering
        String batchPayload = """
                {
                  "devices": [
                    {"serial": "  it-s1-ser-003  ", "imei": "123456789012345"},
                    {"serial": "it-s1-ser-001", "imei": ""},
                    {"serial": "it-s1-ser-002", "imei": null}
                  ]
                }
                """;

        MvcResult batchResult = mockMvc.perform(post(
                        "/api/v1/inventory/receipt-items/{receiptItemId}/device-batches",
                        RECEIPT_ITEM_A_ID)
                        .header("Authorization", "Bearer " + warehouseStoreAToken)
                        .header("Idempotency-Key", BATCH_REQ_KEY_1.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request_key").value(BATCH_REQ_KEY_1.toString()))
                .andExpect(jsonPath("$.receipt_item_id").value(RECEIPT_ITEM_A_ID.toString()))
                .andExpect(jsonPath("$.received_count").value(3))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.device_ids.length()").value(3))
                .andReturn();

        JsonNode batchJson = objectMapper.readTree(batchResult.getResponse().getContentAsString());
        UUID devId0 = UUID.fromString(batchJson.get("device_ids").get(0).asString());
        UUID devId1 = UUID.fromString(batchJson.get("device_ids").get(1).asString());
        UUID devId2 = UUID.fromString(batchJson.get("device_ids").get(2).asString());
        receivedStoreADevice1Id = devId0;

        // Verify UUID[] strictly preserved input element order in sasms.devices
        assertEquals("IT-S1-SER-003", jdbcTemplate.queryForObject(
                "SELECT serial FROM sasms.devices WHERE id = ?", String.class, devId0));
        assertEquals("123456789012345", jdbcTemplate.queryForObject(
                "SELECT imei FROM sasms.devices WHERE id = ?", String.class, devId0));
        assertEquals("IT-S1-SER-001", jdbcTemplate.queryForObject(
                "SELECT serial FROM sasms.devices WHERE id = ?", String.class, devId1));
        assertEquals("IT-S1-SER-002", jdbcTemplate.queryForObject(
                "SELECT serial FROM sasms.devices WHERE id = ?", String.class, devId2));

        // 2. Idempotent retry: same request key + canonically identical payload -> returns identical ordered UUIDs
        String canonicalEquivalentPayload = """
                {
                  "devices": [
                    {"serial": "IT-S1-SER-003", "imei": "123456789012345"},
                    {"serial": "  IT-S1-SER-001 ", "imei": null},
                    {"serial": "it-s1-ser-002"}
                  ]
                }
                """;
        mockMvc.perform(post(
                        "/api/v1/inventory/receipt-items/{receiptItemId}/device-batches",
                        RECEIPT_ITEM_A_ID)
                        .header("Authorization", "Bearer " + warehouseStoreAToken)
                        .header("Idempotency-Key", BATCH_REQ_KEY_1.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(canonicalEquivalentPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_ids[0]").value(devId0.toString()))
                .andExpect(jsonPath("$.device_ids[1]").value(devId1.toString()))
                .andExpect(jsonPath("$.device_ids[2]").value(devId2.toString()));

        Integer deviceCountAfterRetry = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sasms.devices WHERE receipt_item_id = ?",
                Integer.class, RECEIPT_ITEM_A_ID);
        assertEquals(3, deviceCountAfterRetry);

        // 3. Reused request key + changed payload (or changed element order) -> 422 VALIDATION_FAILED (SQLSTATE 23514)
        String reorderedPayload = """
                {
                  "devices": [
                    {"serial": "IT-S1-SER-001", "imei": null},
                    {"serial": "IT-S1-SER-003", "imei": "123456789012345"},
                    {"serial": "IT-S1-SER-002", "imei": null}
                  ]
                }
                """;
        mockMvc.perform(post(
                        "/api/v1/inventory/receipt-items/{receiptItemId}/device-batches",
                        RECEIPT_ITEM_A_ID)
                        .header("Authorization", "Bearer " + warehouseStoreAToken)
                        .header("Idempotency-Key", BATCH_REQ_KEY_1.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reorderedPayload))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // 4. Duplicate Serial/IMEI in a multi-item batch rolls back the ENTIRE batch atomically -> 409 DUPLICATE
        String atomicRollbackPayload = """
                {
                  "devices": [
                    {"serial": "IT-S1-ATOMIC-FIRST-NEVER-SAVED", "imei": "999999999999991"},
                    {"serial": "  it-s1-ser-001  ", "imei": null}
                  ]
                }
                """;
        mockMvc.perform(post(
                        "/api/v1/inventory/receipt-items/{receiptItemId}/device-batches",
                        RECEIPT_ITEM_A_ID)
                        .header("Authorization", "Bearer " + warehouseStoreAToken)
                        .header("Idempotency-Key", BATCH_REQ_KEY_ROLLBACK.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atomicRollbackPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));

        Integer rolledBackDeviceCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sasms.devices WHERE serial = 'IT-S1-ATOMIC-FIRST-NEVER-SAVED'",
                Integer.class);
        Integer rolledBackBatchCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sasms.inventory_import_batches WHERE request_key = ?",
                Integer.class, BATCH_REQ_KEY_ROLLBACK);
        assertEquals(0, rolledBackDeviceCount, "First device in failed batch must be rolled back");
        assertEquals(0, rolledBackBatchCount, "Failed batch must not persist idempotency record");

        // 5. Out-of-scope store and unauthorized role are blocked -> 403 FORBIDDEN
        String singleDevicePayload = """
                {
                  "devices": [
                    {"serial": "IT-S1-OUT-OF-SCOPE-001", "imei": null}
                  ]
                }
                """;
        // Store A warehouse user cannot receive into Store B's receipt item
        mockMvc.perform(post(
                        "/api/v1/inventory/receipt-items/{receiptItemId}/device-batches",
                        RECEIPT_ITEM_B_ID)
                        .header("Authorization", "Bearer " + warehouseStoreAToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleDevicePayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Store A SALES user (lacks inventory.receive) cannot receive into Store A's receipt item
        mockMvc.perform(post(
                        "/api/v1/inventory/receipt-items/{receiptItemId}/device-batches",
                        RECEIPT_ITEM_A_ID)
                        .header("Authorization", "Bearer " + salesStoreAToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleDevicePayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @Order(5)
    void qrLookupFindsInScopeDeviceHidesCrossStoreBlocksUnauthorizedAndDoesNotMutateInventory() throws Exception {
        String warehouseStoreAToken = validTokenFor(SUB_STORE_A_WAREHOUSE);
        String salesStoreAToken = validTokenFor(SUB_STORE_A_SALES);
        String unassignedToken = validTokenFor(SUB_UNASSIGNED);
        String adminToken = validTokenFor(SUB_GLOBAL_ADMIN);

        UUID storeAQrToken = jdbcTemplate.queryForObject(
                "SELECT qr_token FROM sasms.devices WHERE id = ?",
                UUID.class, receivedStoreADevice1Id);
        UUID storeBQrToken = jdbcTemplate.queryForObject(
                "SELECT qr_token FROM sasms.devices WHERE id = ?",
                UUID.class, receivedStoreBDeviceId);

        Integer movementsBefore = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sasms.inventory_movements", Integer.class);
        Integer devicesBefore = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sasms.devices", Integer.class);

        // 1. In-scope QR lookup succeeds for Store A staff (WAREHOUSE and SALES)
        mockMvc.perform(post("/api/v1/devices/qr-lookup")
                        .header("Authorization", "Bearer " + salesStoreAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"SASMS:DEVICE:" + storeAQrToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_id").value(receivedStoreADevice1Id.toString()))
                .andExpect(jsonPath("$.product_id").value(FIXTURE_PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.warehouse_id").value(WAREHOUSE_A_ID.toString()))
                .andExpect(jsonPath("$.status").value("IN_STOCK"))
                .andExpect(jsonPath("$.serial").doesNotExist())
                .andExpect(jsonPath("$.imei").doesNotExist());

        // 2. Non-existent qr_token -> 404 NOT_FOUND
        UUID randomMissingQr = UUID.fromString("11111111-1111-4111-8111-999999999999");
        mockMvc.perform(post("/api/v1/devices/qr-lookup")
                        .header("Authorization", "Bearer " + warehouseStoreAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"SASMS:DEVICE:" + randomMissingQr + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource was not found"));

        // 3. Cross-store device (Store B) looked up by Store A staff -> returns identical 404 NOT_FOUND without leaking existence
        mockMvc.perform(post("/api/v1/devices/qr-lookup")
                        .header("Authorization", "Bearer " + warehouseStoreAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"SASMS:DEVICE:" + storeBQrToken + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource was not found"));

        // Whereas global ADMIN can look up Store B's device -> 200 OK
        mockMvc.perform(post("/api/v1/devices/qr-lookup")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"SASMS:DEVICE:" + storeBQrToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_id").value(receivedStoreBDeviceId.toString()))
                .andExpect(jsonPath("$.warehouse_id").value(WAREHOUSE_B_ID.toString()));

        // 4. User lacking inventory.read (unassigned active user) -> blocked with 404 NOT_FOUND without leaking existence
        mockMvc.perform(post("/api/v1/devices/qr-lookup")
                        .header("Authorization", "Bearer " + unassignedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"SASMS:DEVICE:" + storeAQrToken + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // Malformed QR syntax is a bad request under the D52 error contract.
        mockMvc.perform(post("/api/v1/devices/qr-lookup")
                        .header("Authorization", "Bearer " + warehouseStoreAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"INVALID-QR-PAYLOAD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        // 5. Inventory state and movements remain 100% unchanged after QR lookups
        Integer movementsAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sasms.inventory_movements", Integer.class);
        Integer devicesAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sasms.devices", Integer.class);
        String statusAfter = jdbcTemplate.queryForObject(
                "SELECT status FROM sasms.devices WHERE id = ?", String.class, receivedStoreADevice1Id);

        assertEquals(movementsBefore, movementsAfter, "QR lookup must not create inventory movements");
        assertEquals(devicesBefore, devicesAfter, "QR lookup must not alter device count");
        assertEquals("IN_STOCK", statusAfter, "QR lookup must not alter device status");
    }

    private void assertUnauthorizedGet(String path, String token) throws Exception {
        mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.correlation_id").isString());
    }

    private static void assertInsufficientPrivilege(Runnable action) {
        DataAccessException ex = assertThrows(DataAccessException.class, action::run);
        Throwable current = ex;
        String sqlState = null;
        while (current != null) {
            if (current instanceof SQLException sqlEx && sqlEx.getSQLState() != null) {
                sqlState = sqlEx.getSQLState();
                break;
            }
            current = current.getCause();
        }
        assertEquals("42501", sqlState, "Expected PostgreSQL insufficient_privilege (42501)");
    }

    private static String validTokenFor(String subject) {
        return issueToken(
                jwtEncoder,
                ISSUER,
                AUDIENCE,
                subject,
                Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(600));
    }

    private static String issueToken(
            JwtEncoder encoder,
            String issuer,
            String audience,
            String subject,
            Instant notBefore,
            Instant expiresAt) {
        Instant issuedAt = expiresAt.isBefore(Instant.now())
                ? expiresAt.minusSeconds(300)
                : Instant.now().minusSeconds(10);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(subject)
                .issuedAt(issuedAt)
                .notBefore(notBefore)
                .expiresAt(expiresAt)
                .claim("roles", List.of("ADMIN"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY_ID).build(),
                claims)).getTokenValue();
    }

    private static void cleanupTestArtifacts(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    DELETE FROM sasms.inventory_import_batches
                    WHERE receipt_item_id IN ('11111111-1111-4111-8111-000000000504', '11111111-1111-4111-8111-000000000604');
                    DELETE FROM sasms.inventory_movements
                    WHERE warehouse_id IN ('11111111-1111-4111-8111-000000000211', '11111111-1111-4111-8111-000000000212');
                    DELETE FROM sasms.outbox_events
                    WHERE payload->>'warehouse_id' IN ('11111111-1111-4111-8111-000000000211', '11111111-1111-4111-8111-000000000212');
                    DELETE FROM sasms.devices
                    WHERE warehouse_id IN ('11111111-1111-4111-8111-000000000211', '11111111-1111-4111-8111-000000000212');
                    DELETE FROM sasms.goods_receipt_items
                    WHERE id IN ('11111111-1111-4111-8111-000000000504', '11111111-1111-4111-8111-000000000604');
                    DELETE FROM sasms.goods_receipts
                    WHERE id IN ('11111111-1111-4111-8111-000000000503', '11111111-1111-4111-8111-000000000603');
                    DELETE FROM sasms.purchase_order_items
                    WHERE id IN ('11111111-1111-4111-8111-000000000502', '11111111-1111-4111-8111-000000000602');
                    DELETE FROM sasms.purchase_orders
                    WHERE id IN ('11111111-1111-4111-8111-000000000501', '11111111-1111-4111-8111-000000000601');
                    DELETE FROM sasms.suppliers
                    WHERE id = '11111111-1111-4111-8111-000000000301';
                    DELETE FROM sasms.audit_logs
                    WHERE actor_id IN (
                        '11111111-1111-4111-8111-000000000101',
                        '11111111-1111-4111-8111-000000000102',
                        '11111111-1111-4111-8111-000000000103',
                        '11111111-1111-4111-8111-000000000104',
                        '11111111-1111-4111-8111-000000000105',
                        '11111111-1111-4111-8111-000000000106'
                    );
                    DELETE FROM sasms.products
                    WHERE id = '11111111-1111-4111-8111-000000000401'
                       OR sku = 'IT-S1-OLED-65';
                    DELETE FROM sasms.user_roles
                    WHERE user_id IN (
                        '11111111-1111-4111-8111-000000000101',
                        '11111111-1111-4111-8111-000000000102',
                        '11111111-1111-4111-8111-000000000103',
                        '11111111-1111-4111-8111-000000000104',
                        '11111111-1111-4111-8111-000000000105',
                        '11111111-1111-4111-8111-000000000106'
                    );
                    DELETE FROM sasms.warehouses
                    WHERE id IN ('11111111-1111-4111-8111-000000000211', '11111111-1111-4111-8111-000000000212');
                    DELETE FROM sasms.stores
                    WHERE id IN ('11111111-1111-4111-8111-000000000201', '11111111-1111-4111-8111-000000000202');
                    DELETE FROM sasms.users
                    WHERE id IN (
                        '11111111-1111-4111-8111-000000000101',
                        '11111111-1111-4111-8111-000000000102',
                        '11111111-1111-4111-8111-000000000103',
                        '11111111-1111-4111-8111-000000000104',
                        '11111111-1111-4111-8111-000000000105',
                        '11111111-1111-4111-8111-000000000106'
                    );
                    """);
        }
    }

    private static void seedIsolatedFixtures(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    INSERT INTO sasms.users(id, email, full_name, auth_subject, is_active) VALUES
                        ('11111111-1111-4111-8111-000000000101', 'it-admin@sasms.test', 'IT Global Admin', 'urn:sasms:s1-it:global-admin', true),
                        ('11111111-1111-4111-8111-000000000102', 'it-analyst@sasms.test', 'IT Global Analyst', 'urn:sasms:s1-it:global-analyst', true),
                        ('11111111-1111-4111-8111-000000000103', 'it-wh-a@sasms.test', 'IT Store A Warehouse', 'urn:sasms:s1-it:store-a-warehouse', true),
                        ('11111111-1111-4111-8111-000000000104', 'it-sales-a@sasms.test', 'IT Store A Sales', 'urn:sasms:s1-it:store-a-sales', true),
                        ('11111111-1111-4111-8111-000000000105', 'it-inactive@sasms.test', 'IT Inactive Admin', 'urn:sasms:s1-it:inactive-admin', false),
                        ('11111111-1111-4111-8111-000000000106', 'it-unassigned@sasms.test', 'IT Unassigned User', 'urn:sasms:s1-it:unassigned', true);

                    INSERT INTO sasms.stores(id, code, name, address, is_active) VALUES
                        ('11111111-1111-4111-8111-000000000201', 'IT-STORE-A', 'IT Test Store A', '100 Test St A', true),
                        ('11111111-1111-4111-8111-000000000202', 'IT-STORE-B', 'IT Test Store B', '200 Test St B', true);

                    INSERT INTO sasms.warehouses(id, store_id, code, name) VALUES
                        ('11111111-1111-4111-8111-000000000211', '11111111-1111-4111-8111-000000000201', 'IT-WH-A', 'IT Warehouse A'),
                        ('11111111-1111-4111-8111-000000000212', '11111111-1111-4111-8111-000000000202', 'IT-WH-B', 'IT Warehouse B');

                    INSERT INTO sasms.user_roles(user_id, role_code, store_id) VALUES
                        ('11111111-1111-4111-8111-000000000101', 'ADMIN', NULL),
                        ('11111111-1111-4111-8111-000000000102', 'ANALYST', NULL),
                        ('11111111-1111-4111-8111-000000000103', 'WAREHOUSE', '11111111-1111-4111-8111-000000000201'),
                        ('11111111-1111-4111-8111-000000000104', 'SALES', '11111111-1111-4111-8111-000000000201'),
                        ('11111111-1111-4111-8111-000000000105', 'ADMIN', NULL);

                    INSERT INTO sasms.products(id, sku, brand_id, category_id, name, specifications, list_price, warranty_months, is_active)
                    VALUES (
                        '11111111-1111-4111-8111-000000000401',
                        'IT-S1-FIXTURE-TV',
                        '00000000-0000-4000-8000-000000000020',
                        '00000000-0000-4000-8000-000000000021',
                        'IT Fixture Smart TV 55',
                        '{"resolution":"4K"}'::jsonb,
                        15990000.00,
                        24,
                        true
                    );

                    INSERT INTO sasms.suppliers(id, code, name) VALUES
                        ('11111111-1111-4111-8111-000000000301', 'IT-SUP-01', 'IT Fixture Supplier');

                    INSERT INTO sasms.purchase_orders(id, code, supplier_id, warehouse_id, status, created_by) VALUES
                        ('11111111-1111-4111-8111-000000000501', 'IT-PO-A', '11111111-1111-4111-8111-000000000301', '11111111-1111-4111-8111-000000000211', 'APPROVED', '11111111-1111-4111-8111-000000000101'),
                        ('11111111-1111-4111-8111-000000000601', 'IT-PO-B', '11111111-1111-4111-8111-000000000301', '11111111-1111-4111-8111-000000000212', 'APPROVED', '11111111-1111-4111-8111-000000000101');

                    INSERT INTO sasms.purchase_order_items(id, purchase_order_id, product_id, quantity, unit_cost) VALUES
                        ('11111111-1111-4111-8111-000000000502', '11111111-1111-4111-8111-000000000501', '11111111-1111-4111-8111-000000000401', 20, 10000000.00),
                        ('11111111-1111-4111-8111-000000000602', '11111111-1111-4111-8111-000000000601', '11111111-1111-4111-8111-000000000401', 10, 10000000.00);

                    INSERT INTO sasms.goods_receipts(id, code, purchase_order_id, warehouse_id, status, received_by) VALUES
                        ('11111111-1111-4111-8111-000000000503', 'IT-GR-A', '11111111-1111-4111-8111-000000000501', '11111111-1111-4111-8111-000000000211', 'OPEN', '11111111-1111-4111-8111-000000000103'),
                        ('11111111-1111-4111-8111-000000000603', 'IT-GR-B', '11111111-1111-4111-8111-000000000601', '11111111-1111-4111-8111-000000000212', 'OPEN', '11111111-1111-4111-8111-000000000101');

                    INSERT INTO sasms.goods_receipt_items(id, receipt_id, purchase_order_id, purchase_order_item_id, product_id, quantity) VALUES
                        ('11111111-1111-4111-8111-000000000504', '11111111-1111-4111-8111-000000000503', '11111111-1111-4111-8111-000000000501', '11111111-1111-4111-8111-000000000502', '11111111-1111-4111-8111-000000000401', 20),
                        ('11111111-1111-4111-8111-000000000604', '11111111-1111-4111-8111-000000000603', '11111111-1111-4111-8111-000000000601', '11111111-1111-4111-8111-000000000602', '11111111-1111-4111-8111-000000000401', 10);
                    """);
        }

        // Seed one Store B device via sasms.receive_device_batch using Global Admin
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT (sasms.receive_device_batch(?, ?, '[{\"serial\":\"IT-S1-STORE-B-DEV-001\",\"imei\":null}]'::jsonb, ?))[1]")) {
            ps.setObject(1, RECEIPT_ITEM_B_ID);
            ps.setObject(2, STORE_B_BATCH_KEY);
            ps.setObject(3, ACTOR_GLOBAL_ADMIN);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                receivedStoreBDeviceId = rs.getObject(1, UUID.class);
            }
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        assumeTrue(value != null && !value.isBlank(), () -> name + " is required when Sprint 1 PG IT is enabled");
        return value;
    }

    private static void assertSafeIsolatedTarget(String jdbcUrl, String appUser) {
        assertTrue(jdbcUrl.startsWith("jdbc:postgresql://"), "Only a PostgreSQL JDBC URL is allowed");
        URI target = URI.create(jdbcUrl.substring("jdbc:".length()));
        assertEquals("postgresql", target.getScheme());
        assertTrue(Set.of("127.0.0.1", "localhost", "::1").contains(target.getHost()),
                "Integration test is restricted to a loopback PostgreSQL host");
        assertTrue(target.getPort() != 5432 && target.getPort() > 0,
                "Sprint 1 integration test must use an isolated non-5432 host port (e.g. 15432)");
        assertEquals("/sasms", target.getPath(), "Integration test is restricted to database sasms");
        assertFalse("sasms_owner".equalsIgnoreCase(appUser), "Runtime user must not be sasms_owner");
        assertFalse("postgres".equalsIgnoreCase(appUser), "Runtime user must not be postgres");
    }

    private static String scalarString(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }
}
