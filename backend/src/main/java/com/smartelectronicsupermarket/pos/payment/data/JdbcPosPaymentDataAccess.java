package com.smartelectronicsupermarket.pos.payment.data;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.smartelectronicsupermarket.pos.order.data.PosOrderRecord;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPosPaymentDataAccess implements PosPaymentDataAccess {

    private static final String FIND_ORDER_SQL = """
            SELECT
                id,
                code,
                idempotency_key,
                store_id,
                warehouse_id,
                customer_id,
                channel,
                status,
                total_amount,
                created_by,
                created_at,
                to_jsonb(so)->>'hold_expires_at' AS hold_expires_at
            FROM sasms.sales_orders so
            WHERE id = ?
            """;

    private static final String HAS_PERMISSION_SQL = """
            SELECT sasms.has_permission(
                CAST(? AS uuid),
                'sales.checkout',
                CAST(? AS uuid)
            )
            """;

    private static final String FIND_EXISTING_CAPTURED_PAYMENT_SQL = """
            SELECT
                id,
                order_id,
                amount,
                captured_at,
                method,
                status
            FROM sasms.payments
            WHERE order_id = ? AND method = 'CASH' AND status = 'CAPTURED'
            ORDER BY captured_at DESC
            LIMIT 1
            """;

    private static final String INSERT_CAPTURED_CASH_PAYMENT_SQL = """
            INSERT INTO sasms.payments (
                id,
                order_id,
                method,
                status,
                amount,
                provider_reference,
                confirmed_by,
                captured_at
            ) VALUES (
                ?, ?, 'CASH', 'CAPTURED', ?, ?, ?, ?
            )
            ON CONFLICT (method, provider_reference) WHERE provider_reference IS NOT NULL DO NOTHING
            """;

    private static final String COMPLETE_SALE_SQL = """
            SELECT sasms.complete_sale(
                CAST(? AS uuid),
                CAST(? AS uuid)
            )
            """;

    private static final String FIND_INVOICE_NUMBER_SQL = """
            SELECT number
            FROM sasms.invoices
            WHERE order_id = ?
            ORDER BY id DESC
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPosPaymentDataAccess(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PosOrderRecord> findOrderById(UUID orderId) {
        try {
            PosOrderRecord record = jdbcTemplate.queryForObject(
                    FIND_ORDER_SQL,
                    (rs, rowNum) -> mapOrder(rs),
                    orderId);
            return Optional.ofNullable(record);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public boolean hasCheckoutPermission(UUID actorId, UUID storeId) {
        Boolean granted = jdbcTemplate.queryForObject(
                HAS_PERMISSION_SQL,
                Boolean.class,
                actorId,
                storeId);
        return Boolean.TRUE.equals(granted);
    }

    @Override
    public Optional<ExistingPaymentRecord> findExistingCapturedPayment(UUID orderId) {
        try {
            ExistingPaymentRecord record = jdbcTemplate.queryForObject(
                    FIND_EXISTING_CAPTURED_PAYMENT_SQL,
                    (rs, rowNum) -> new ExistingPaymentRecord(
                            rs.getObject("id", UUID.class),
                            rs.getObject("order_id", UUID.class),
                            rs.getBigDecimal("amount"),
                            rs.getObject("captured_at", OffsetDateTime.class),
                            rs.getString("method"),
                            rs.getString("status")),
                    orderId);
            return Optional.ofNullable(record);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void insertCapturedCashPayment(
            UUID paymentId,
            UUID orderId,
            BigDecimal amount,
            String providerReference,
            UUID actorId,
            OffsetDateTime capturedAt) {
        jdbcTemplate.update(
                INSERT_CAPTURED_CASH_PAYMENT_SQL,
                paymentId,
                orderId,
                amount,
                providerReference,
                actorId,
                capturedAt);
    }

    @Override
    public UUID completeSale(UUID orderId, UUID actorId) {
        return jdbcTemplate.queryForObject(
                COMPLETE_SALE_SQL,
                UUID.class,
                orderId,
                actorId);
    }

    @Override
    public Optional<String> findInvoiceNumber(UUID orderId) {
        try {
            String number = jdbcTemplate.queryForObject(
                    FIND_INVOICE_NUMBER_SQL,
                    String.class,
                    orderId);
            return Optional.ofNullable(number);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private static PosOrderRecord mapOrder(ResultSet rs) throws SQLException {
        String holdExpires = rs.getString("hold_expires_at");
        Instant holdExpiresAt = holdExpires != null ? Instant.parse(holdExpires) : null;
        return new PosOrderRecord(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getObject("idempotency_key", UUID.class),
                rs.getObject("store_id", UUID.class),
                rs.getObject("warehouse_id", UUID.class),
                rs.getObject("customer_id", UUID.class),
                rs.getString("channel"),
                rs.getString("status"),
                rs.getBigDecimal("total_amount"),
                rs.getObject("created_by", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                holdExpiresAt);
    }
}
