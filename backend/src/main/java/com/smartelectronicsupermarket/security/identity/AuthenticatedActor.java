package com.smartelectronicsupermarket.security.identity;

import java.util.UUID;

/**
 * Internal actor identifier produced only after a trusted authentication flow.
 * Constructing this value alone is not proof that authentication occurred.
 */
public record AuthenticatedActor(UUID actorId) {

    public AuthenticatedActor {
        if (actorId == null) {
            throw new IllegalArgumentException("actorId is required");
        }
    }
}
