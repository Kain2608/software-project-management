package com.smartelectronicsupermarket.reporting.data;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class JdbcStoreDashboardDataAccessTest {
    @Test void summaryUsesOnlyPaidOrdersAndServerSideStoreFilters(){
        assertTrue(JdbcStoreDashboardDataAccess.SUMMARY_SQL.contains("o.status='PAID'"));
        assertTrue(JdbcStoreDashboardDataAccess.SUMMARY_SQL.contains("o.store_id=CAST(? AS uuid)"));
        assertTrue(JdbcStoreDashboardDataAccess.SUMMARY_SQL.contains("w.store_id=CAST(? AS uuid)"));
        assertTrue(JdbcStoreDashboardDataAccess.SUMMARY_SQL.contains("d.status='IN_STOCK'"));
        assertFalse(JdbcStoreDashboardDataAccess.SUMMARY_SQL.toLowerCase().contains("customer"));
    }
    @Test void productAggregationIsPaidScopedAndBounded(){
        assertTrue(JdbcStoreDashboardDataAccess.PRODUCTS_SQL.contains("o.status='PAID'"));
        assertTrue(JdbcStoreDashboardDataAccess.PRODUCTS_SQL.contains("LIMIT ?"));
    }
}
