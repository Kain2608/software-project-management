package com.smartelectronicsupermarket.inventory.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.UUID;

import com.smartelectronicsupermarket.common.web.ApiExceptionHandler;
import com.smartelectronicsupermarket.inventory.service.InventoryBatchService;
import com.smartelectronicsupermarket.security.identity.AuthenticatedActor;
import com.smartelectronicsupermarket.security.identity.TrustedActorAuthenticationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class InventoryBatchControllerTest {

    private static final UUID RECEIPT_ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000601");
    private static final UUID REQUEST_KEY = UUID.fromString("00000000-0000-0000-0000-000000000602");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000603");
    private static final String DEVICES_JSON = "[{\"serial\":\"SER-001\",\"imei\":null}]";

    private InventoryBatchService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(InventoryBatchService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InventoryBatchController(service, JsonMapper.builder().build()))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void mapsStableUuidIdempotencyKeyAndReturnsDatabaseIdsInOrder() throws Exception {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000611");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000612");
        when(service.receiveDeviceBatch(RECEIPT_ITEM_ID, REQUEST_KEY, DEVICES_JSON, ACTOR_ID))
                .thenReturn(new UUID[]{first, second});

        mockMvc.perform(post("/api/v1/inventory/receipt-items/{id}/device-batches", RECEIPT_ITEM_ID)
                        .principal(trustedActor())
                        .header("Idempotency-Key", REQUEST_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"devices\":[{\"serial\":\"SER-001\",\"imei\":null}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request_key").value(REQUEST_KEY.toString()))
                .andExpect(jsonPath("$.receipt_item_id").value(RECEIPT_ITEM_ID.toString()))
                .andExpect(jsonPath("$.device_ids[0]").value(first.toString()))
                .andExpect(jsonPath("$.device_ids[1]").value(second.toString()))
                .andExpect(jsonPath("$.received_count").value(2))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        verify(service).receiveDeviceBatch(RECEIPT_ITEM_ID, REQUEST_KEY, DEVICES_JSON, ACTOR_ID);
    }

    @Test
    void rejectsNonUuidIdempotencyKeyBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/receipt-items/{id}/device-batches", RECEIPT_ITEM_ID)
                        .principal(trustedActor())
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"devices\":[{\"serial\":\"SER-001\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verify(service, never()).receiveDeviceBatch(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void duplicateOrChangedPayloadConflictIsSafeAndNeverPartialSuccess() throws Exception {
        DataIntegrityViolationException conflict = new DataIntegrityViolationException(
                "duplicate serial SQL details",
                new SQLException("duplicate identifier payload", "23505"));
        when(service.receiveDeviceBatch(RECEIPT_ITEM_ID, REQUEST_KEY, DEVICES_JSON, ACTOR_ID))
                .thenThrow(conflict);

        mockMvc.perform(post("/api/v1/inventory/receipt-items/{id}/device-batches", RECEIPT_ITEM_ID)
                        .principal(trustedActor())
                        .header("Idempotency-Key", REQUEST_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"devices\":[{\"serial\":\"SER-001\",\"imei\":null}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(content().string(not(containsString("SQL"))))
                .andExpect(content().string(not(containsString("payload"))));
    }

    private static TrustedActorAuthenticationToken trustedActor() {
        return new TrustedActorAuthenticationToken(new AuthenticatedActor(ACTOR_ID));
    }
}
