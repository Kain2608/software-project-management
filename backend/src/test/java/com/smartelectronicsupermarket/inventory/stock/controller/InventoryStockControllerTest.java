package com.smartelectronicsupermarket.inventory.stock.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.smartelectronicsupermarket.common.web.ApiExceptionHandler;
import com.smartelectronicsupermarket.inventory.stock.data.InventoryStockRecord;
import com.smartelectronicsupermarket.inventory.stock.service.InventoryStockService;
import com.smartelectronicsupermarket.security.identity.AuthenticatedActor;
import com.smartelectronicsupermarket.security.identity.TrustedActorAuthenticationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InventoryStockControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000811");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000812");
    private static final UUID WAREHOUSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000813");

    private InventoryStockService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(InventoryStockService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new InventoryStockController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void returnsD52StockShapeForTrustedActor() throws Exception {
        when(service.listStock(ACTOR_ID, WAREHOUSE_ID, "TV", 20, 0)).thenReturn(List.of(
                new InventoryStockRecord(PRODUCT_ID, WAREHOUSE_ID, 3, 2, 1)));

        mockMvc.perform(get("/api/v1/inventory/stock")
                        .principal(new TrustedActorAuthenticationToken(new AuthenticatedActor(ACTOR_ID)))
                        .queryParam("warehouse_id", WAREHOUSE_ID.toString())
                        .queryParam("sku", "TV"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sku_id").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.items[0].accounting_stock").value(3))
                .andExpect(jsonPath("$.items[0].sellable_count").value(2))
                .andExpect(jsonPath("$.items[0].reserved_count").value(1))
                .andExpect(jsonPath("$.snapshot_at").exists());
    }
}
