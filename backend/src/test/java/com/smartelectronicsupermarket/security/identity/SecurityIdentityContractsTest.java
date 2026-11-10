package com.smartelectronicsupermarket.security.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class SecurityIdentityContractsTest {

    @Test
    void externalIdentityPreservesIssuerAndSubjectWithoutNamespacing() {
        ExternalIdentity identity = new ExternalIdentity(
                "https://identity.example.test",
                "provider-subject-123");

        assertEquals("https://identity.example.test", identity.issuer());
        assertEquals("provider-subject-123", identity.subject());
    }

    @Test
    void externalIdentityRejectsMissingIssuerOrSubject() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExternalIdentity(null, "subject"));
        assertThrows(IllegalArgumentException.class,
                () -> new ExternalIdentity("issuer", " "));
    }

    @Test
    void authenticatedActorPreservesInternalActorId() {
        UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        AuthenticatedActor actor = new AuthenticatedActor(actorId);

        assertEquals(actorId, actor.actorId());
    }

    @Test
    void authenticatedActorDoesNotCreateDefaultActorId() {
        assertThrows(IllegalArgumentException.class, () -> new AuthenticatedActor(null));
    }
}
