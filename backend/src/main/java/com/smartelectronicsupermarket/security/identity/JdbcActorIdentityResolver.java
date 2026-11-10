package com.smartelectronicsupermarket.security.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartelectronicsupermarket.security.config.SasmsJwtProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcActorIdentityResolver implements ActorIdentityResolver {

    static final String FIND_BY_SUBJECT_SQL = """
            SELECT u.id, u.is_active
            FROM sasms.users u
            WHERE u.auth_subject = CAST(? AS text)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final SasmsJwtProperties jwtProperties;

    public JdbcActorIdentityResolver(JdbcTemplate jdbcTemplate, SasmsJwtProperties jwtProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public Optional<AuthenticatedActor> resolve(ExternalIdentity externalIdentity) {
        if (externalIdentity == null) {
            throw new IllegalArgumentException("externalIdentity is required");
        }
        if (!jwtProperties.isConfigured() || !jwtProperties.issuer().equals(externalIdentity.issuer())) {
            return Optional.empty();
        }

        List<UserIdentityRow> matches = jdbcTemplate.query(
                FIND_BY_SUBJECT_SQL,
                (resultSet, rowNumber) -> new UserIdentityRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getBoolean("is_active")),
                externalIdentity.subject());

        if (matches.size() != 1 || !matches.getFirst().active()) {
            return Optional.empty();
        }
        return Optional.of(new AuthenticatedActor(matches.getFirst().actorId()));
    }

    record UserIdentityRow(UUID actorId, boolean active) {
    }
}
