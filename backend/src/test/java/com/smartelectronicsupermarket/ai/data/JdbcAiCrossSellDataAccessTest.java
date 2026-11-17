package com.smartelectronicsupermarket.ai.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class JdbcAiCrossSellDataAccessTest {

    private static String sql(String fieldName) throws Exception {
        Field field = JdbcAiCrossSellDataAccess.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return ((String) field.get(null)).replaceAll("\\s+", " ").toLowerCase();
    }

    @Test
    void sellableQueryReadsPriceAndStockFromRealTablesScopedToWarehouse() throws Exception {
        String sql = sql("SELLABLE_SQL");
        assertTrue(sql.contains("p.list_price"));
        assertTrue(sql.contains("from sasms.products p"));
        assertTrue(sql.contains("join sasms.stock_availability sa"));
        assertTrue(sql.contains("sa.warehouse_id = cast(? as uuid)"));
        assertTrue(sql.contains("sa.available > 0"));
        assertTrue(sql.contains("p.is_active = true"));
        assertFalse(sql.contains("select *"));
    }

    @Test
    void warehouseQueryChecksStoreOwnership() throws Exception {
        String sql = sql("WAREHOUSE_IN_STORE_SQL");
        assertTrue(sql.contains("from sasms.warehouses w"));
        assertTrue(sql.contains("w.store_id = cast(? as uuid)"));
    }
}
