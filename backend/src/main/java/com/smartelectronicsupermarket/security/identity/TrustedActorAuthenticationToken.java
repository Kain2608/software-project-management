package com.smartelectronicsupermarket.security.identity;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;

public final class TrustedActorAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedActor principal;

    public TrustedActorAuthenticationToken(AuthenticatedActor principal) {
        super(List.of());
        if (principal == null) {
            throw new IllegalArgumentException("principal is required");
        }
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public AuthenticatedActor getPrincipal() {
        return principal;
    }
}
