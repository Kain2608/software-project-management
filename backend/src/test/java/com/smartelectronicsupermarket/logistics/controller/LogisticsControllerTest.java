package com.smartelectronicsupermarket.logistics.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;

import com.smartelectronicsupermarket.common.web.ApiExceptionHandler;
import com.smartelectronicsupermarket.logistics.service.LogisticsService;
import com.smartelectronicsupermarket.security.identity.AuthenticatedActor;
import com.smartelectronicsupermarket.security.identity.TrustedActorAuthenticationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LogisticsControllerTest {
    private static final UUID ORDER = UUID.fromString("00000000-0000-0000-0000-000000000811");
    private static final UUID TECHNICIAN = UUID.fromString("00000000-0000-0000-0000-000000000812");
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000813");
    private static final UUID DELIVERY = UUID.fromString("00000000-0000-0000-0000-000000000814");
    private LogisticsService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(LogisticsService.class);
        mvc = MockMvcBuilders.standaloneSetup(new LogisticsController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void scheduleUsesOnlyTrustedPrincipalActorAndReturnsCreated() throws Exception {
        Instant start = Instant.parse("2026-12-01T02:00:00Z");
        Instant end = Instant.parse("2026-12-01T03:00:00Z");
        when(service.schedule(ORDER, TECHNICIAN, start, end, "Address", ACTOR)).thenReturn(DELIVERY);
        mvc.perform(post("/api/v1/logistics/deliveries")
                        .principal(new TrustedActorAuthenticationToken(new AuthenticatedActor(ACTOR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"order_id":"%s","technician_id":"%s",
                                 "scheduled_start":"2026-12-01T02:00:00Z",
                                 "scheduled_end":"2026-12-01T03:00:00Z","address":"Address"}
                                """.formatted(ORDER, TECHNICIAN)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/logistics/deliveries/" + DELIVERY))
                .andExpect(jsonPath("$.delivery_id").value(DELIVERY.toString()));
        verify(service).schedule(ORDER, TECHNICIAN, start, end, "Address", ACTOR);
    }

    @Test
    void untrustedClientCannotSupplyActorInBody() throws Exception {
        mvc.perform(post("/api/v1/logistics/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor_id\":\"" + ACTOR + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(service);
    }

    @Test
    void resultMapsDatabaseConstraintToSafeErrorWithoutSqlLeak() throws Exception {
        var failure = new org.springframework.dao.DataIntegrityViolationException("SQL details",
                new java.sql.SQLException("bad state", "23514"));
        when(service.recordResult(DELIVERY, "COMPLETED", "E-1", ACTOR)).thenThrow(failure);
        mvc.perform(post("/api/v1/logistics/deliveries/{id}/result", DELIVERY)
                        .principal(new TrustedActorAuthenticationToken(new AuthenticatedActor(ACTOR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"COMPLETED\",\"evidence_reference\":\"E-1\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQL"))));
        verify(service, times(1)).recordResult(DELIVERY, "COMPLETED", "E-1", ACTOR);
    }
}
