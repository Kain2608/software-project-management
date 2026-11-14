package com.smartelectronicsupermarket.inventory.stock.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcInventoryStockDataAccessTest {

    @Test
    void queryUsesRealAvailabilityViewAndStoreScopedPermission() throws Exception {
        Field field = JdbcInventoryStockDataAccess.class.getDeclaredField("LIST_STOCK_SQL");
        field.setAccessible(true);
        String sql = ((String) field.get(null)).replaceAll("\\s+", " ").toLowerCase();

        assertTrue(sql.contains("from sasms.stock_availability sa"));
        assertTrue(sql.contains("join sasms.warehouses w on w.id = sa.warehouse_id"));
        assertTrue(sql.contains("'inventory.read', w.store_id"));
        assertTrue(sql.contains("sa.available + sa.reserved as accounting_stock"));
        assertFalse(sql.contains("from sasms.devices"));
        assertFalse(sql.contains("select *"));
    }

    @Test
    void rejectsMissingTrustedActorBeforeDatabase() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcInventoryStockDataAccess dataAccess = new JdbcInventoryStockDataAccess(jdbcTemplate);

        assertThrows(IllegalArgumentException.class, () -> dataAccess.listStock(null, null, null, 20, 0));
        verifyNoInteractions(jdbcTemplate);
    }
}
