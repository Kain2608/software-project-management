package com.smartelectronicsupermarket.pos.order.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class JdbcPosOrderDataAccessTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID WAREHOUSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");

    @Mock
    private JdbcTemplate jdbcTemplate;

    private JdbcPosOrderDataAccess dataAccess;

    @BeforeEach
    void setUp() {
        dataAccess = new JdbcPosOrderDataAccess(jdbcTemplate);
    }

    @Test
    void hasCheckoutPermission_invokesHasPermissionFunction() {
        when(jdbcTemplate.queryForObject(contains("sasms.has_permission"), eq(Boolean.class), eq(ACTOR_ID), eq(STORE_ID)))
                .thenReturn(Boolean.TRUE);

        boolean granted = dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID);
        assertThat(granted).isTrue();
    }

    @Test
    void isWarehouseInStore_checksWarehouseMembership() {
        when(jdbcTemplate.queryForObject(contains("FROM sasms.warehouses"), eq(Boolean.class), eq(WAREHOUSE_ID), eq(STORE_ID)))
                .thenReturn(Boolean.TRUE);

        boolean inStore = dataAccess.isWarehouseInStore(WAREHOUSE_ID, STORE_ID);
        assertThat(inStore).isTrue();
    }

    @Test
    void reserveOrder_invokesReserveOrderFunction() {
        when(jdbcTemplate.queryForObject(contains("sasms.reserve_order"), eq(BigDecimal.class), eq(ORDER_ID), eq(ACTOR_ID)))
                .thenReturn(new BigDecimal("1500000"));

        BigDecimal amount = dataAccess.reserveOrder(ORDER_ID, ACTOR_ID);
        assertThat(amount).isEqualByComparingTo("1500000");
    }
}
