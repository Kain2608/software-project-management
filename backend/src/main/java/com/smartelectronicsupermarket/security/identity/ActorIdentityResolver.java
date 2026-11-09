package com.smartelectronicsupermarket.security.identity;

import java.util.Optional;

public interface ActorIdentityResolver {

    Optional<AuthenticatedActor> resolve(ExternalIdentity externalIdentity);
}
