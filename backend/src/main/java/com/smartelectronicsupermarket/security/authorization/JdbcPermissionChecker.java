package com.smartelectronicsupermarket.security.authorization;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPermissionChecker implements PermissionChecker {

    private static final String HAS_PERMISSION_SQL = """
            SELECT sasms.has_permission(
                CAST(? AS uuid),
                CAST(? AS text),
                CAST(? AS uuid)
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPermissionChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean hasPermission(UUID actorId, String permissionCode, UUID storeId) {
        if (actorId == null) {
            throw new IllegalArgumentException("actorId is required");
        }
        if (permissionCode == null || permissionCode.isBlank()) {
            throw new IllegalArgumentException("permissionCode is required");
        }

        Boolean granted = jdbcTemplate.queryForObject(
                HAS_PERMISSION_SQL,
                Boolean.class,
                actorId,
                permissionCode,
                storeId);
        return Boolean.TRUE.equals(granted);
    }
}
