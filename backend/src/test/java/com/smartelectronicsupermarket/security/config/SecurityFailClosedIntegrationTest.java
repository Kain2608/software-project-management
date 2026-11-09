package com.smartelectronicsupermarket.security.config;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "sasms.jwt.issuer=",
        "sasms.jwt.audience=",
        "sasms.jwt.jwks-uri="
})
class SecurityFailClosedIntegrationTest {

    @Autowired
    private WebApplicationContext applicationContext;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void exactPublicStatusGetsRemainAvailableWithoutJwtConfiguration() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/pos/status")).andExpect(status().isOk());
        mockMvc.perform(get("/api/serial-imei/status")).andExpect(status().isOk());
    }

    @Test
    void protectedRouteWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/not-public"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.correlation_id").isString())
                .andExpect(jsonPath("$.correlationId").doesNotExist());
    }

    @Test
    void publicPathWithWrongHttpMethodIsNotPublic() throws Exception {
        mockMvc.perform(post("/api/health")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingJwtConfigurationDoesNotAcceptArbitraryBearerToken() throws Exception {
        mockMvc.perform(get("/api/not-public")
                        .header("Authorization", "Bearer unverified-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void xActorIdHeaderCannotBypassAuthentication() throws Exception {
        mockMvc.perform(get("/api/not-public")
                        .header("X-Actor-Id", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void businessEndpointsAreNeverPublicWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/v1/devices/qr-lookup")
                        .contentType("application/json")
                        .content("{\"payload\":\"SASMS:DEVICE:123e4567-e89b-12d3-a456-426614174000\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/inventory/receipt-items/00000000-0000-0000-0000-000000000001/device-batches")
                        .header("Idempotency-Key", "00000000-0000-0000-0000-000000000002")
                        .contentType("application/json")
                        .content("{\"devices\":[{\"serial\":\"SER-001\"}]}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());
    }
}
