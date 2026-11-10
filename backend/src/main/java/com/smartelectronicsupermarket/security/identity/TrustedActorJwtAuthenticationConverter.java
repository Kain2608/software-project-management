package com.smartelectronicsupermarket.security.identity;

import org.springframework.core.convert.converter.Converter;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

public class TrustedActorJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final ActorIdentityResolver actorIdentityResolver;

    public TrustedActorJwtAuthenticationConverter(ActorIdentityResolver actorIdentityResolver) {
        this.actorIdentityResolver = actorIdentityResolver;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (jwt == null || jwt.getIssuer() == null || !StringUtils.hasText(jwt.getSubject())) {
            throw new BadCredentialsException("Verified JWT must contain issuer and subject");
        }

        ExternalIdentity externalIdentity = new ExternalIdentity(
                jwt.getIssuer().toString(),
                jwt.getSubject());
        try {
            return actorIdentityResolver.resolve(externalIdentity)
                    .<AbstractAuthenticationToken>map(TrustedActorAuthenticationToken::new)
                    .orElseThrow(() -> new BadCredentialsException("No active SASMS user for verified identity"));
        } catch (DataAccessException exception) {
            throw new BadCredentialsException("Unable to authenticate SASMS actor", exception);
        }
    }
}
