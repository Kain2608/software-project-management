package com.smartelectronicsupermarket.security.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

class TrustedActorJwtAuthenticationConverterTest {

    private static final String ISSUER = "https://issuer.example.test";
    private static final String SUBJECT = "opaque-subject";
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000222");

    @Test
    void storesOnlyResolvedActorAndIgnoresUntrustedRoleClaim() {
        ActorIdentityResolver resolver = mock(ActorIdentityResolver.class);
        ExternalIdentity identity = new ExternalIdentity(ISSUER, SUBJECT);
        when(resolver.resolve(identity)).thenReturn(Optional.of(new AuthenticatedActor(ACTOR_ID)));

        TrustedActorAuthenticationToken authentication = assertInstanceOf(
                TrustedActorAuthenticationToken.class,
                new TrustedActorJwtAuthenticationConverter(resolver).convert(jwt(SUBJECT, List.of("ADMIN"))));

        assertEquals(ACTOR_ID, authentication.getPrincipal().actorId());
        assertTrue(authentication.getAuthorities().isEmpty());
        assertNull(authentication.getCredentials());
        verify(resolver).resolve(identity);
    }

    @Test
    void rejectsMissingSubjectBeforeDatabaseLookup() {
        ActorIdentityResolver resolver = mock(ActorIdentityResolver.class);
        assertThrows(
                BadCredentialsException.class,
                () -> new TrustedActorJwtAuthenticationConverter(resolver).convert(jwt(null, List.of())));
    }

    @Test
    void rejectsIdentityWithoutActiveMappedUser() {
        ActorIdentityResolver resolver = mock(ActorIdentityResolver.class);
        when(resolver.resolve(new ExternalIdentity(ISSUER, SUBJECT))).thenReturn(Optional.empty());

        assertThrows(
                BadCredentialsException.class,
                () -> new TrustedActorJwtAuthenticationConverter(resolver).convert(jwt(SUBJECT, List.of())));
    }

    @Test
    void convertsDatabaseFailureToAuthenticationFailure() {
        ActorIdentityResolver resolver = mock(ActorIdentityResolver.class);
        when(resolver.resolve(new ExternalIdentity(ISSUER, SUBJECT)))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThrows(
                BadCredentialsException.class,
                () -> new TrustedActorJwtAuthenticationConverter(resolver).convert(jwt(SUBJECT, List.of())));
    }

    private static Jwt jwt(String subject, List<String> roles) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("test-token-not-logged")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .audience(List.of("sasms-api"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("roles", roles);
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }
}
