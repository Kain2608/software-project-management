package com.smartelectronicsupermarket.security.authorization;

import java.util.UUID;

public interface PermissionChecker {

    /**
     * Checks a permission for an internal actor. A null storeId requests global scope.
     */
    boolean hasPermission(UUID actorId, String permissionCode, UUID storeId);
}
