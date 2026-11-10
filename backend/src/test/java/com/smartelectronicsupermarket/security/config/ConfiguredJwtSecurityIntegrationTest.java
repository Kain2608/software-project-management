package com.smartelectronicsupermarket.security.config;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.smartelectronicsupermarket.security.identity.ActorIdentityResolver;
import com.smartelectronicsupermarket.security.identity.AuthenticatedActor;
import com.smartelectronicsupermarket.security.identity.ExternalIdentity;
import com.smartelectronicsupermarket.security.identity.TrustedActorAuthenticationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "sasms.jwt.issuer=https://issuer.example.test",
        "sasms.jwt.audience=sasms-api",
        "sasms.jwt.jwks-uri=https://issuer.example.test/.well-known/jwks.json"
})
@Import(ConfiguredJwtSecurityIntegrationTest.TestSecurityBeans.class)
class ConfiguredJwtSecurityIntegrationTest {

    private static final String ISSUER = "https://issuer.example.test";
    private static final String AUDIENCE = "sasms-api";
    private static final String SUBJECT = "active-subject";
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000333");

    private static final RSAPublicKey PUBLIC_KEY;
    private static final JwtEncoder ENCODER;
    private static final JwtDecoder DECODER;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            PUBLIC_KEY = (RSAPublicKey) keyPair.getPublic();
            ENCODER = NimbusJwtEncoder.withKeyPair(
                    PUBLIC_KEY,
                    (RSAPrivateKey) keyPair.getPrivate()).build();
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(PUBLIC_KEY)
                    .signatureAlgorithm(SignatureAlgorithm.RS256)
                    .build();
            SasmsJwtDecoderFactory.configureValidators(
                    decoder,
                    new SasmsJwtProperties(
                            ISSUER,
                            AUDIENCE,
                            "https://issuer.example.test/.well-known/jwks.json"));
            DECODER = decoder;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @Autowired
    private WebApplicationContext applicationContext;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ActorIdentityResolver actorIdentityResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void validVerifiedJwtUsesDatabaseResolvedActor() throws Exception {
        when(actorIdentityResolver.resolve(new ExternalIdentity(ISSUER, SUBJECT)))
                .thenReturn(Optional.of(new AuthenticatedActor(ACTOR_ID)));

        mockMvc.perform(get("/api/security-test")
                        .header("Authorization", bearer(token(ENCODER, ISSUER, AUDIENCE, SUBJECT,
                                Instant.now().minusSeconds(1), Instant.now().plusSeconds(300), List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorId").value(ACTOR_ID.toString()))
                .andExpect(jsonPath("$.authorityCount").value(0));
    }

    @Test
    void wrongSignatureExpiredNotBeforeIssuerAndAudienceAreUnauthorized() throws Exception {
        JwtEncoder otherEncoder = newRsaEncoder();
        assertUnauthorized(token(otherEncoder, ISSUER, AUDIENCE, SUBJECT,
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(300), List.of()));
        assertUnauthorized(token(ENCODER, ISSUER, AUDIENCE, SUBJECT,
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(300), List.of()));
        assertUnauthorized(token(ENCODER, ISSUER, AUDIENCE, SUBJECT,
                Instant.now().plusSeconds(300), Instant.now().plusSeconds(600), List.of()));
        assertUnauthorized(token(ENCODER, "https://wrong.example.test", AUDIENCE, SUBJECT,
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(300), List.of()));
        assertUnauthorized(token(ENCODER, ISSUER, "other-api", SUBJECT,
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(300), List.of()));
    }

    @Test
    void missingSubjectAndUnknownOrInactiveUserAreUnauthorized() throws Exception {
        assertUnauthorized(token(ENCODER, ISSUER, AUDIENCE, null,
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(300), List.of()));

        when(actorIdentityResolver.resolve(new ExternalIdentity(ISSUER, "unknown")))
                .thenReturn(Optional.empty());
        assertUnauthorized(token(ENCODER, ISSUER, AUDIENCE, "unknown",
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(300), List.of()));

        when(actorIdentityResolver.resolve(new ExternalIdentity(ISSUER, "inactive")))
                .thenReturn(Optional.empty());
        assertUnauthorized(token(ENCODER, ISSUER, AUDIENCE, "inactive",
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(300), List.of()));
    }

    @Test
    void databaseFailureIsUnauthorizedInsteadOfGrantingAccess() throws Exception {
        when(actorIdentityResolver.resolve(new ExternalIdentity(ISSUER, SUBJECT)))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertUnauthorized(token(ENCODER, ISSUER, AUDIENCE, SUBJECT,
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(300), List.of()));
    }

    @Test
    void forgedRoleAndActorHeaderDoNotChangeResolvedActorOrAuthorities() throws Exception {
        when(actorIdentityResolver.resolve(new ExternalIdentity(ISSUER, SUBJECT)))
                .thenReturn(Optional.of(new AuthenticatedActor(ACTOR_ID)));

        mockMvc.perform(get("/api/security-test")
                        .header("Authorization", bearer(token(ENCODER, ISSUER, AUDIENCE, SUBJECT,
                                Instant.now().minusSeconds(1), Instant.now().plusSeconds(300), List.of("ADMIN"))))
                        .header("X-Actor-Id", "00000000-0000-0000-0000-000000000999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorId").value(ACTOR_ID.toString()))
                .andExpect(jsonPath("$.authorityCount").value(0));
    }

    private void assertUnauthorized(String token) throws Exception {
        mockMvc.perform(get("/api/security-test").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.correlation_id").isString())
                .andExpect(jsonPath("$.correlationId").doesNotExist());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static JwtEncoder newRsaEncoder() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        return NimbusJwtEncoder.withKeyPair(
                (RSAPublicKey) pair.getPublic(),
                (RSAPrivateKey) pair.getPrivate()).build();
    }

    private static String token(
            JwtEncoder encoder,
            String issuer,
            String audience,
            String subject,
            Instant notBefore,
            Instant expiresAt,
            List<String> roles) {
        Instant issuedAt = expiresAt.isBefore(Instant.now())
                ? expiresAt.minusSeconds(300)
                : Instant.now().minusSeconds(5);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .issuedAt(issuedAt)
                .notBefore(notBefore)
                .expiresAt(expiresAt)
                .claim("roles", roles);
        if (subject != null) {
            claims.subject(subject);
        }
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(),
                claims.build())).getTokenValue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSecurityBeans {

        @Bean
        @Primary
        SasmsJwtDecoderFactory testJwtDecoderFactory() {
            return new SasmsJwtDecoderFactory() {
                @Override
                public JwtDecoder create(SasmsJwtProperties properties) {
                    properties.validateConfigurationState();
                    return DECODER;
                }
            };
        }

        @Bean
        ProtectedSecurityTestController protectedSecurityTestController() {
            return new ProtectedSecurityTestController();
        }
    }

    @RestController
    static class ProtectedSecurityTestController {

        @GetMapping("/api/security-test")
        Map<String, Object> actor(Authentication authentication) {
            TrustedActorAuthenticationToken trusted = (TrustedActorAuthenticationToken) authentication;
            return Map.of(
                    "actorId", trusted.getPrincipal().actorId().toString(),
                    "authorityCount", trusted.getAuthorities().size());
        }
    }
}
