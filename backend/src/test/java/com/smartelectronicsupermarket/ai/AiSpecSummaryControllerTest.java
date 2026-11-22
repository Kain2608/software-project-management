package com.smartelectronicsupermarket.ai;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.smartelectronicsupermarket.ai.controller.AiExceptionHandler;
import com.smartelectronicsupermarket.ai.controller.AiSpecSummaryController;
import com.smartelectronicsupermarket.ai.summary.AiSpecSummaryService;
import com.smartelectronicsupermarket.ai.summary.MockSpecSummaryLlm;
import com.smartelectronicsupermarket.ai.summary.SpecProduct;
import com.smartelectronicsupermarket.ai.summary.SpecSummaryDataAccess;
import com.smartelectronicsupermarket.common.web.ApiExceptionHandler;
import com.smartelectronicsupermarket.security.authorization.PermissionChecker;
import com.smartelectronicsupermarket.security.identity.AuthenticatedActor;
import com.smartelectronicsupermarket.security.identity.TrustedActorAuthenticationToken;

class AiSpecSummaryControllerTest {

    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000811");
    private static final UUID STORE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID PRODUCT = UUID.fromString("00000000-0000-0000-0000-000000000030");

    private MockMvc mockMvc;
    private PermissionChecker permissions;
    private SpecSummaryDataAccess data;

    @BeforeEach
    void setUp() {
        permissions = mock(PermissionChecker.class);
        data = mock(SpecSummaryDataAccess.class);
        when(permissions.hasPermission(ACTOR, "ai.use", STORE)).thenReturn(true);
        when(data.findProduct(PRODUCT)).thenReturn(Optional.of(new SpecProduct(
                PRODUCT, "DEMO-TV-55", "Tivi DEMO 55 inch", "DEMO", "TELEVISION", true,
                "{\"screen_inches\":55,\"resolution\":\"4K\"}")));
        mockMvc = MockMvcBuilders.standaloneSetup(new AiSpecSummaryController(
                        new AiSpecSummaryService(new MockSpecSummaryLlm(), data, permissions)))
                .setControllerAdvice(new AiExceptionHandler(), new ApiExceptionHandler())
                .build();
    }

    private static RequestPostProcessor actor(UUID id) {
        return request -> {
            request.setUserPrincipal(new TrustedActorAuthenticationToken(new AuthenticatedActor(id)));
            return request;
        };
    }

    private static String body(String store, String product) {
        return "{\"store_id\":\"" + store + "\",\"product_id\":\"" + product + "\"}";
    }

    @Test
    void returnsSummaryTableAndAdvisoryDisclaimer() throws Exception {
        mockMvc.perform(post("/api/v1/ai/summaries/specifications").with(actor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON).content(body(STORE.toString(), PRODUCT.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.source", is("AI")))
                .andExpect(jsonPath("$.product_id", is(PRODUCT.toString())))
                .andExpect(jsonPath("$.specifications.resolution", is("4K")))
                .andExpect(jsonPath("$.disclaimer", containsString("chỉ mang tính tham khảo")));
    }

    @Test
    void missingAiUseIsForbiddenScope() throws Exception {
        mockMvc.perform(post("/api/v1/ai/summaries/specifications")
                        .with(actor(UUID.fromString("00000000-0000-0000-0000-000000000999")))
                        .contentType(MediaType.APPLICATION_JSON).content(body(STORE.toString(), PRODUCT.toString())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN_SCOPE")));
    }

    @Test
    void unknownProductIs422() throws Exception {
        mockMvc.perform(post("/api/v1/ai/summaries/specifications").with(actor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(STORE.toString(), UUID.randomUUID().toString())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", is("PRODUCT_NOT_FOUND")));
    }

    @Test
    void invalidBodyIs400() throws Exception {
        mockMvc.perform(post("/api/v1/ai/summaries/specifications").with(actor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"store_id\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_ARGUMENT")));
        mockMvc.perform(post("/api/v1/ai/summaries/specifications").with(actor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON).content(body("nope", PRODUCT.toString())))
                .andExpect(status().isBadRequest());
    }
}
