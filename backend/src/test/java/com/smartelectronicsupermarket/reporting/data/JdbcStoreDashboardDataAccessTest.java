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
    @Test void aiAttributionIsPaidStoreScopedFromTheOrderAndCountsEachLineOnce(){
        String sql=JdbcStoreDashboardDataAccess.AI_SQL;
        assertTrue(sql.contains("o.status='PAID'"));
        assertTrue(sql.contains("o.store_id=CAST(? AS uuid)"));
        assertTrue(sql.contains("o.paid_at>=? AND o.paid_at<?"));
        assertTrue(sql.contains("LATERAL")&&sql.contains("LIMIT 1"));
        assertTrue(sql.contains("r.created_at>=o.created_at AND r.created_at<=o.paid_at"));
        assertTrue(sql.contains("s.id<>i.id"));
        assertFalse(sql.toLowerCase().contains("customer"));
        assertEquals(3,sql.chars().filter(c->c=='?').count());
    }
}
