package com.smartelectronicsupermarket.common.web;

import java.util.UUID;

import com.smartelectronicsupermarket.security.identity.TrustedActorAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

public final class TrustedActorAccess {

    private TrustedActorAccess() {
    }

    public static UUID actorId(Authentication authentication) {
        if (!(authentication instanceof TrustedActorAuthenticationToken trusted)
                || !trusted.isAuthenticated()) {
            throw new AccessDeniedException("Trusted SASMS actor is required");
        }
        return trusted.getPrincipal().actorId();
    }
}
