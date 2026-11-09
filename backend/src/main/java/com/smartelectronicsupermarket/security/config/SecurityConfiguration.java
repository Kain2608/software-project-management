package com.smartelectronicsupermarket.security.config;

import com.smartelectronicsupermarket.common.web.ApiSecurityExceptionHandler;
import com.smartelectronicsupermarket.security.identity.ActorIdentityResolver;
import com.smartelectronicsupermarket.security.identity.TrustedActorJwtAuthenticationConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SasmsJwtProperties.class)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SasmsJwtProperties jwtProperties,
            SasmsJwtDecoderFactory jwtDecoderFactory,
            ActorIdentityResolver actorIdentityResolver,
            ApiSecurityExceptionHandler securityExceptionHandler) throws Exception {
        jwtProperties.validateConfigurationState();

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET,
                                "/api/health",
                                "/api/pos/status",
                                "/api/serial-imei/status")
                        .permitAll()
                        .anyRequest()
                        .authenticated());

        if (jwtProperties.isConfigured()) {
            JwtDecoder jwtDecoder = jwtDecoderFactory.create(jwtProperties);
            TrustedActorJwtAuthenticationConverter trustedActorConverter =
                    new TrustedActorJwtAuthenticationConverter(actorIdentityResolver);
            http.oauth2ResourceServer(resourceServer -> resourceServer
                    .authenticationEntryPoint(securityExceptionHandler)
                    .accessDeniedHandler(securityExceptionHandler)
                    .jwt(jwt -> jwt
                            .decoder(jwtDecoder)
                            .jwtAuthenticationConverter(trustedActorConverter)));
        }

        return http.build();
    }
}
