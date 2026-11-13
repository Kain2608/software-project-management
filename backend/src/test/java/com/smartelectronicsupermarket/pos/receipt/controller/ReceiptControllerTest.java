package com.smartelectronicsupermarket.pos.receipt.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartelectronicsupermarket.common.web.ApiExceptionHandler;
import com.smartelectronicsupermarket.common.web.ResourceConflictException;
import com.smartelectronicsupermarket.pos.receipt.data.ReceiptLineRecord;
import com.smartelectronicsupermarket.pos.receipt.data.ReceiptRecord;
import com.smartelectronicsupermarket.pos.receipt.service.ReceiptService;
import com.smartelectronicsupermarket.security.identity.AuthenticatedActor;
import com.smartelectronicsupermarket.security.identity.TrustedActorAuthenticationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReceiptControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000831");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000832");

    private ReceiptService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(ReceiptService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ReceiptController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void returnsReadOnlyPaidReceiptWithMaskedSerial() throws Exception {
        UUID productId = UUID.fromString("00000000-0000-0000-0000-000000000833");
        UUID deviceId = UUID.fromString("00000000-0000-0000-0000-000000000834");
        when(service.findPaidReceipt(ACTOR_ID, ORDER_ID)).thenReturn(Optional.of(new ReceiptRecord(
                ORDER_ID,
                "PAID",
                new BigDecimal("12000000.00"),
                OffsetDateTime.parse("2026-11-10T10:00:00+07:00"),
                List.of(new ReceiptLineRecord(productId, deviceId, "SERIAL-SECRET-1234", new BigDecimal("12000000.00"))))));

        mockMvc.perform(get("/api/v1/pos/orders/{id}/receipt", ORDER_ID).principal(trustedActor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order_id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.items[0].sku_id").value(productId.toString()))
                .andExpect(jsonPath("$.items[0].serial_masked").value("****1234"))
                .andExpect(jsonPath("$.items[0].price_minor").value(12000000L))
                .andExpect(jsonPath("$.total_minor").value(12000000L))
                .andExpect(jsonPath("$.currency").value("VND"))
                .andExpect(content().string(not(containsString("SERIAL-SECRET"))));
    }

    @Test
    void concealsMissingOrOutOfScopeAndRejectsNonPaidState() throws Exception {
        when(service.findPaidReceipt(ACTOR_ID, ORDER_ID)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/pos/orders/{id}/receipt", ORDER_ID).principal(trustedActor()))
                .andExpect(status().isNotFound());

        when(service.findPaidReceipt(ACTOR_ID, ORDER_ID))
                .thenThrow(new ResourceConflictException("internal order state"));
        mockMvc.perform(get("/api/v1/pos/orders/{id}/receipt", ORDER_ID).principal(trustedActor()))
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("internal order state"))));
    }

    private static TrustedActorAuthenticationToken trustedActor() {
        return new TrustedActorAuthenticationToken(new AuthenticatedActor(ACTOR_ID));
    }
}
