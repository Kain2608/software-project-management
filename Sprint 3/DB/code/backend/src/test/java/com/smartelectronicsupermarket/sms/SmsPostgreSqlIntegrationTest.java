package com.smartelectronicsupermarket.sms;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Opt-in, owner fixtures on a NEW isolated, migrated PostgreSQL 16 database. No HTTP. */
class SmsPostgreSqlIntegrationTest {
    private static final UUID STORE = UUID.fromString("00000000-0000-4000-8000-000000000010");
    private static final UUID WAREHOUSE = UUID.fromString("00000000-0000-4000-8000-000000000011");
    private static final UUID BRAND = UUID.fromString("00000000-0000-4000-8000-000000000020");
    private static final UUID CATEGORY = UUID.fromString("00000000-0000-4000-8000-000000000021");
    private static final UUID SUPPLIER = UUID.fromString("00000000-0000-4000-8000-000000000040");

    @Test void committedWarrantyEventPassesThroughMockWorkerWithoutChangingWarranty() {
        String url = System.getenv("SASMS_S3_JDBC_URL");
        String user = System.getenv("SASMS_S3_JDBC_USER");
        String password = System.getenv("SASMS_S3_JDBC_PASSWORD");
        assumeTrue(url != null && !url.isBlank() && user != null && !user.isBlank() && password != null,
                "Set SASMS_S3_JDBC_URL, SASMS_S3_JDBC_USER and SASMS_S3_JDBC_PASSWORD for the isolated PostgreSQL test");
        assertTrue(url.startsWith("jdbc:postgresql://"));
        URI target = URI.create(url.substring("jdbc:".length()));
        assertTrue(Set.of("127.0.0.1", "localhost", "[::1]", "::1").contains(target.getHost()),
                "Integration fixtures require a loopback database");
        assertTrue(target.getPath().matches("/sasms_s3_[a-z0-9_]+"),
                "Use a NEW isolated database named sasms_s3_<suffix>");
        var datasource = new DriverManagerDataSource(url, user, password);
        JdbcTemplate jdbc = new JdbcTemplate(datasource);
        var manager = new DataSourceTransactionManager(datasource);
        var transaction = new TransactionTemplate(manager);
        assertEquals(16, jdbc.queryForObject("SELECT current_setting('server_version_num')::integer/10000", Integer.class));
        assertEquals(7, jdbc.queryForObject("SELECT max(version) FROM sasms_meta.schema_migrations", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM sasms.outbox_events WHERE event_type IN "
                + "('WarrantyActivated','DeliveryScheduled') AND notification_status='QUEUED'", Integer.class),
                "Use an empty synthetic notification queue; this test does not consume other fixtures");

        Fixture fixture = new Fixture();
        boolean seeded = false;
        try {
            transaction.executeWithoutResult(ignored -> seed(jdbc, fixture));
            seeded = true;
            UUID warranty = jdbc.queryForObject("SELECT id FROM sasms.warranties WHERE device_id=?", UUID.class, fixture.device);
            UUID event = jdbc.queryForObject("SELECT id FROM sasms.outbox_events WHERE aggregate_id=? AND event_type='WarrantyActivated'",
                    UUID.class, warranty);
            assertEquals("QUEUED", jdbc.queryForObject("SELECT notification_status FROM sasms.outbox_events WHERE id=?", String.class, event));
            assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM sasms.warranties WHERE id=?", String.class, warranty));

            AtomicInteger sends = new AtomicInteger();
            var mapper = JsonMapper.builder().build();
            SmsAdapter mock = new SmsConfiguration().smsAdapter(
                    new SmsProperties(SmsProperties.Mode.MOCK, null, null, null, null, null), mapper);
            SmsAdapter observedMock = request -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                        "Sending must occur after the claim transaction committed");
                assertEquals(event, request.eventId());
                assertEquals("+84912345678", request.destination());
                assertEquals("WAR-12", request.templateId());
                assertEquals("PROCESSING", jdbc.queryForObject(
                        "SELECT notification_status FROM sasms.outbox_events WHERE id=?", String.class, event),
                        "A separate connection must observe the committed claim before sending");
                assertEquals(1, jdbc.queryForObject("SELECT attempts FROM sasms.outbox_events WHERE id=?", Integer.class, event));
                sends.incrementAndGet();
                return mock.send(request);
            };
            var worker = new SmsOutboxWorker(jdbc, observedMock, mapper, manager);
            worker.poll();
            worker.poll();
            assertEquals(1, sends.get(), "The committed mock event must not be sent a second time");
            assertEquals("MOCK_ACCEPTED", jdbc.queryForObject(
                    "SELECT notification_status FROM sasms.outbox_events WHERE id=?", String.class, event));
            assertNull(jdbc.queryForObject("SELECT published_at FROM sasms.outbox_events WHERE id=?", Object.class, event));
            assertEquals("mock-" + event, jdbc.queryForObject("SELECT provider_reference FROM sasms.outbox_events WHERE id=?", String.class, event));
            assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM sasms.warranties WHERE id=?", String.class, warranty));
            assertEquals("PAID", jdbc.queryForObject("SELECT status FROM sasms.sales_orders WHERE id=?", String.class, fixture.order));
            assertEquals("SOLD", jdbc.queryForObject("SELECT status FROM sasms.devices WHERE id=?", String.class, fixture.device));
        } finally {
            if (seeded) transaction.executeWithoutResult(ignored -> cleanup(jdbc, fixture));
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM sasms.users WHERE id=?", Integer.class, fixture.actor));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM sasms.products WHERE id=?", Integer.class, fixture.product));
        assertTrue(jdbc.queryForObject("SELECT bool_and(tgenabled<>'D') FROM pg_trigger WHERE tgrelid='sasms.payments'::regclass "
                + "AND tgname='guard_payments'", Boolean.class), "Payment guard must remain enabled");
    }

    private static void seed(JdbcTemplate jdbc, Fixture f) {
        jdbc.update("INSERT INTO sasms.users(id,email,full_name,is_active) VALUES(?,?,?,true)",
                f.actor, f.actor + "@example.invalid", "Synthetic SMS integration actor");
        jdbc.update("INSERT INTO sasms.user_roles(user_id,role_code,store_id) VALUES(?,'CASHIER',?),(?,'CUSTOMER_SERVICE',?)",
                f.actor, STORE, f.actor, STORE);
        jdbc.update("INSERT INTO sasms.customers(id,code,full_name,phone) VALUES(?,?,?,'0912345678')",
                f.customer, "SMSIT-" + f.customer, "Synthetic SMS customer");
        jdbc.update("INSERT INTO sasms.products(id,sku,brand_id,category_id,name,list_price,requires_installation) VALUES(?,?,?,?,?,100,false)",
                f.product, "SMSIT-" + f.product.toString().toUpperCase(), BRAND, CATEGORY, "Synthetic SMS product");
        jdbc.update("INSERT INTO sasms.purchase_orders(id,code,supplier_id,warehouse_id,status,created_by) VALUES(?,?,?,?,'APPROVED',?)",
                f.purchaseOrder, "SMSIT-" + f.purchaseOrder, SUPPLIER, WAREHOUSE, f.actor);
        jdbc.update("INSERT INTO sasms.purchase_order_items(id,purchase_order_id,product_id,quantity,unit_cost) VALUES(?,?,?,1,50)",
                f.purchaseItem, f.purchaseOrder, f.product);
        jdbc.update("INSERT INTO sasms.goods_receipts(id,code,purchase_order_id,warehouse_id,received_by) VALUES(?,?,?,?,?)",
                f.receipt, "SMSIT-" + f.receipt, f.purchaseOrder, WAREHOUSE, f.actor);
        jdbc.update("INSERT INTO sasms.goods_receipt_items(id,receipt_id,purchase_order_id,purchase_order_item_id,product_id,quantity) VALUES(?,?,?,?,?,1)",
                f.receiptItem, f.receipt, f.purchaseOrder, f.purchaseItem, f.product);
        jdbc.update("INSERT INTO sasms.devices(id,product_id,receipt_item_id,warehouse_id,serial) VALUES(?,?,?,?,?)",
                f.device, f.product, f.receiptItem, WAREHOUSE, "SMSIT-" + f.device);
        jdbc.update("INSERT INTO sasms.sales_orders(id,code,store_id,warehouse_id,customer_id,channel,created_by) VALUES(?,?,?,?,?,'POS',?)",
                f.order, "SMSIT-" + f.order, STORE, WAREHOUSE, f.customer, f.actor);
        jdbc.update("INSERT INTO sasms.sales_order_items(id,order_id,product_id,quantity,unit_price) VALUES(?,?,?,1,100)",
                f.orderItem, f.order, f.product);
        jdbc.update("INSERT INTO sasms.order_devices(order_item_id,order_id,product_id,device_id) VALUES(?,?,?,?)",
                f.orderItem, f.order, f.product, f.device);
        jdbc.queryForObject("SELECT sasms.reserve_order(?,?)", Object.class, f.order, f.actor);
        jdbc.update("INSERT INTO sasms.payments(order_id,method,status,amount,confirmed_by,captured_at) VALUES(?,'CASH','CAPTURED',100,?,clock_timestamp())",
                f.order, f.actor);
        jdbc.queryForObject("SELECT sasms.complete_sale(?,?)", Object.class, f.order, f.actor);
        jdbc.queryForObject("SELECT sasms.activate_warranty(?,?)", UUID.class, f.device, f.actor);
    }

    private static void cleanup(JdbcTemplate jdbc, Fixture f) {
        jdbc.update("DELETE FROM sasms.outbox_events WHERE aggregate_id=? OR aggregate_id IN (SELECT id FROM sasms.warranties WHERE device_id=?)", f.order, f.device);
        jdbc.update("DELETE FROM sasms.warranties WHERE device_id=?", f.device);
        jdbc.update("DELETE FROM sasms.invoices WHERE order_id=?", f.order);
        // Only committed synthetic captured payments need an owner override. Transaction
        // rollback restores both fixture rows and trigger state if any cleanup SQL fails.
        jdbc.execute("ALTER TABLE sasms.payments DISABLE TRIGGER guard_payments");
        jdbc.update("DELETE FROM sasms.payments WHERE order_id=?", f.order);
        jdbc.execute("ALTER TABLE sasms.payments ENABLE TRIGGER guard_payments");
        jdbc.update("DELETE FROM sasms.inventory_movements WHERE device_id=?", f.device);
        jdbc.update("UPDATE sasms.sales_orders SET status='DRAFT',paid_at=NULL WHERE id=?", f.order);
        jdbc.update("DELETE FROM sasms.order_devices WHERE order_id=?", f.order);
        jdbc.update("DELETE FROM sasms.sales_order_items WHERE order_id=?", f.order);
        jdbc.update("DELETE FROM sasms.device_identifiers WHERE device_id=?", f.device);
        jdbc.update("DELETE FROM sasms.devices WHERE id=?", f.device);
        jdbc.update("DELETE FROM sasms.sales_orders WHERE id=?", f.order);
        jdbc.update("DELETE FROM sasms.goods_receipt_items WHERE id=?", f.receiptItem);
        jdbc.update("DELETE FROM sasms.goods_receipts WHERE id=?", f.receipt);
        jdbc.update("DELETE FROM sasms.purchase_order_items WHERE id=?", f.purchaseItem);
        jdbc.update("DELETE FROM sasms.purchase_orders WHERE id=?", f.purchaseOrder);
        jdbc.update("DELETE FROM sasms.products WHERE id=?", f.product);
        jdbc.update("DELETE FROM sasms.customers WHERE id=?", f.customer);
        jdbc.update("DELETE FROM sasms.audit_logs WHERE actor_id=?", f.actor);
        jdbc.update("DELETE FROM sasms.user_roles WHERE user_id=?", f.actor);
        jdbc.update("DELETE FROM sasms.users WHERE id=?", f.actor);
    }

    private static final class Fixture {
        final UUID actor = UUID.randomUUID(), customer = UUID.randomUUID(), product = UUID.randomUUID();
        final UUID purchaseOrder = UUID.randomUUID(), purchaseItem = UUID.randomUUID(), receipt = UUID.randomUUID();
        final UUID receiptItem = UUID.randomUUID(), device = UUID.randomUUID(), order = UUID.randomUUID(), orderItem = UUID.randomUUID();
    }
}
