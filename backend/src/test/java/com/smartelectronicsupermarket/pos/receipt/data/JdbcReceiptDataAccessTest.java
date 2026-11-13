package com.smartelectronicsupermarket.pos.receipt.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Field;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcReceiptDataAccessTest {

    @Test
    void queryScopesReceiptAtDatabaseAndSelectsOnlyReceiptFields() throws Exception {
        Field field = JdbcReceiptDataAccess.class.getDeclaredField("FIND_RECEIPT_SQL");
        field.setAccessible(true);
        String sql = ((String) field.get(null)).replaceAll("\\s+", " ").toLowerCase();

        assertTrue(sql.contains("from sasms.sales_orders o"));
        assertTrue(sql.contains("'sales.read', o.store_id"));
        assertTrue(sql.contains("o.id = cast(? as uuid)"));
        assertTrue(sql.contains("cast(? as uuid), 'sales.read'"));
        assertFalse(sql.contains("customer"));
        assertFalse(sql.contains("payment"));
        assertFalse(sql.contains("select *"));
    }

    @Test
    void rejectsMissingTrustedIdentifiersBeforeDatabase() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcReceiptDataAccess dataAccess = new JdbcReceiptDataAccess(jdbcTemplate);
        UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000841");

        assertThrows(IllegalArgumentException.class, () -> dataAccess.findByOrderId(null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> dataAccess.findByOrderId(actorId, null));
        verifyNoInteractions(jdbcTemplate);
    }
}
