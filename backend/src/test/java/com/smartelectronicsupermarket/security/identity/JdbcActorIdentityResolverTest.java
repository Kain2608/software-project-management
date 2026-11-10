package com.smartelectronicsupermarket.security.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.smartelectronicsupermarket.security.config.SasmsJwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcActorIdentityResolverTest {

    private static final String ISSUER = "https://issuer.example.test";
    private static final String SUBJECT = "opaque-provider-subject";
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000111");

    private JdbcTemplate jdbcTemplate;
    private JdbcActorIdentityResolver resolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        resolver = new JdbcActorIdentityResolver(
                jdbcTemplate,
                new SasmsJwtProperties(ISSUER, "sasms-api", "https://issuer.example.test/jwks"));
    }

    @Test
    void resolvesExactlyOneActiveUserByBoundAuthSubject() {
        when(jdbcTemplate.query(
                eq(JdbcActorIdentityResolver.FIND_BY_SUBJECT_SQL),
                org.mockito.ArgumentMatchers.<RowMapper<JdbcActorIdentityResolver.UserIdentityRow>>any(),
                eq(SUBJECT)))
                .thenReturn(List.of(new JdbcActorIdentityResolver.UserIdentityRow(ACTOR_ID, true)));

        assertEquals(
                ACTOR_ID,
                resolver.resolve(new ExternalIdentity(ISSUER, SUBJECT)).orElseThrow().actorId());
    }

    @Test
    void rejectsUnknownAndInactiveUsers() {
        when(jdbcTemplate.query(
                eq(JdbcActorIdentityResolver.FIND_BY_SUBJECT_SQL),
                org.mockito.ArgumentMatchers.<RowMapper<JdbcActorIdentityResolver.UserIdentityRow>>any(),
                eq("unknown")))
                .thenReturn(List.of());
        when(jdbcTemplate.query(
                eq(JdbcActorIdentityResolver.FIND_BY_SUBJECT_SQL),
                org.mockito.ArgumentMatchers.<RowMapper<JdbcActorIdentityResolver.UserIdentityRow>>any(),
                eq("inactive")))
                .thenReturn(List.of(new JdbcActorIdentityResolver.UserIdentityRow(ACTOR_ID, false)));

        assertTrue(resolver.resolve(new ExternalIdentity(ISSUER, "unknown")).isEmpty());
        assertTrue(resolver.resolve(new ExternalIdentity(ISSUER, "inactive")).isEmpty());
    }

    @Test
    void rejectsWrongIssuerWithoutQueryingDatabase() {
        assertTrue(resolver.resolve(new ExternalIdentity("https://other.example.test", SUBJECT)).isEmpty());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void propagatesDatabaseFailure() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(jdbcTemplate.query(
                eq(JdbcActorIdentityResolver.FIND_BY_SUBJECT_SQL),
                org.mockito.ArgumentMatchers.<RowMapper<JdbcActorIdentityResolver.UserIdentityRow>>any(),
                eq(SUBJECT)))
                .thenThrow(failure);

        DataAccessResourceFailureException thrown = assertThrows(
                DataAccessResourceFailureException.class,
                () -> resolver.resolve(new ExternalIdentity(ISSUER, SUBJECT)));
        assertEquals(failure, thrown);
    }
}
