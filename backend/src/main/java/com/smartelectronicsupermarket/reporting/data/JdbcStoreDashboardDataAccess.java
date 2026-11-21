package com.smartelectronicsupermarket.reporting.data;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcStoreDashboardDataAccess implements StoreDashboardDataAccess {
    static final String SUMMARY_SQL="""
            SELECT paid.gross,paid.paid_orders,stock.available_devices,
                   movements.received,movements.sold,movements.returned
            FROM (
              SELECT COALESCE(sum(o.total_amount),0) gross,count(*) paid_orders
              FROM sasms.sales_orders o
              WHERE o.store_id=CAST(? AS uuid) AND o.status='PAID' AND o.paid_at>=? AND o.paid_at<?
            ) paid
            CROSS JOIN (
              SELECT count(*) available_devices
              FROM sasms.devices d JOIN sasms.warehouses w ON w.id=d.warehouse_id
              WHERE w.store_id=CAST(? AS uuid) AND d.status='IN_STOCK'
            ) stock
            CROSS JOIN (
              SELECT count(*) FILTER (WHERE m.kind='RECEIVE') received,
                     count(*) FILTER (WHERE m.kind='SALE') sold,
                     count(*) FILTER (WHERE m.kind='RETURN') returned
              FROM sasms.inventory_movements m JOIN sasms.warehouses w ON w.id=m.warehouse_id
              WHERE w.store_id=CAST(? AS uuid) AND m.created_at>=? AND m.created_at<?
            ) movements
            """;
    static final String PRODUCTS_SQL="""
            SELECT p.id,p.sku,p.name,sum(i.quantity)::bigint quantity,sum(i.line_total) amount
            FROM sasms.sales_orders o JOIN sasms.sales_order_items i ON i.order_id=o.id
            JOIN sasms.products p ON p.id=i.product_id
            WHERE o.store_id=CAST(? AS uuid) AND o.status='PAID' AND o.paid_at>=? AND o.paid_at<?
            GROUP BY p.id,p.sku,p.name ORDER BY quantity DESC,p.sku ASC LIMIT ?
            """;
    private final JdbcTemplate jdbc;
    public JdbcStoreDashboardDataAccess(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override public StoreSummaryRecord summary(UUID storeId,Instant from,Instant to){
        Timestamp f=Timestamp.from(from),t=Timestamp.from(to);
        return jdbc.queryForObject(SUMMARY_SQL,(rs,n)->new StoreSummaryRecord(rs.getBigDecimal("gross"),
                rs.getLong("paid_orders"),rs.getLong("available_devices"),rs.getLong("received"),
                rs.getLong("sold"),rs.getLong("returned")),storeId,f,t,storeId,storeId,f,t);
    }
    @Override public List<ProductSalesRecord> topProducts(UUID storeId,Instant from,Instant to,int limit){
        return jdbc.query(PRODUCTS_SQL,(rs,n)->new ProductSalesRecord(rs.getObject("id",UUID.class),
                rs.getString("sku"),rs.getString("name"),rs.getLong("quantity"),rs.getBigDecimal("amount")),
                storeId,Timestamp.from(from),Timestamp.from(to),limit);
    }
}
