package com.smartelectronicsupermarket.qr.controller;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import com.smartelectronicsupermarket.common.web.ApiExceptionHandler;
import com.smartelectronicsupermarket.qr.data.QrDeviceRecord;
import com.smartelectronicsupermarket.qr.service.QrDeviceLookupService;
import com.smartelectronicsupermarket.security.identity.AuthenticatedActor;
import com.smartelectronicsupermarket.security.identity.TrustedActorAuthenticationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class QrDeviceLookupControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final UUID QR_TOKEN = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final String PAYLOAD = "SASMS:DEVICE:" + QR_TOKEN;

    private QrDeviceLookupService lookupService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lookupService = org.mockito.Mockito.mock(QrDeviceLookupService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new QrDeviceLookupController(lookupService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void returnsOnlyMinimalProductBasedDeviceFieldsForTrustedActor() throws Exception {
        QrDeviceRecord record = new QrDeviceRecord(
                UUID.fromString("00000000-0000-0000-0000-000000000502"),
                UUID.fromString("00000000-0000-0000-0000-000000000503"),
                UUID.fromString("00000000-0000-0000-0000-000000000504"),
                "AVAILABLE");
        when(lookupService.lookup(PAYLOAD, ACTOR_ID)).thenReturn(Optional.of(record));

        mockMvc.perform(post("/api/v1/devices/qr-lookup")
                        .principal(trustedActor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"" + PAYLOAD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_id").value(record.deviceId().toString()))
                .andExpect(jsonPath("$.product_id").value(record.productId().toString()))
                .andExpect(jsonPath("$.warehouse_id").value(record.warehouseId().toString()))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.serial").doesNotExist())
                .andExpect(jsonPath("$.imei").doesNotExist())
                .andExpect(jsonPath("$.sku_id").doesNotExist());
    }

    @Test
    void invalidQrAndMissingOrOutOfScopeDeviceHaveSafeResponses() throws Exception {
        when(lookupService.lookup("bad-qr", ACTOR_ID))
                .thenThrow(new IllegalArgumentException("invalid QR details"));
        when(lookupService.lookup(PAYLOAD, ACTOR_ID)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/devices/qr-lookup")
                        .principal(trustedActor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"bad-qr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(content().string(not(containsString("invalid QR details"))));

        mockMvc.perform(post("/api/v1/devices/qr-lookup")
                        .principal(trustedActor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"" + PAYLOAD + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void databaseFailureDoesNotLeakSql() throws Exception {
        when(lookupService.lookup(PAYLOAD, ACTOR_ID))
                .thenThrow(new DataAccessResourceFailureException(
                        "SELECT secret FROM sasms.devices password=hidden"));

        mockMvc.perform(post("/api/v1/devices/qr-lookup")
                        .principal(trustedActor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"" + PAYLOAD + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("SELECT"))))
                .andExpect(content().string(not(containsString("password"))));
    }

    private static TrustedActorAuthenticationToken trustedActor() {
        return new TrustedActorAuthenticationToken(new AuthenticatedActor(ACTOR_ID));
    }
}
