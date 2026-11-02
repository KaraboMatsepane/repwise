package com.repwise.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security rules for the gateway, which acts as a reactive OAuth2 resource server.
 *
 * <p>Every request must carry a valid Keycloak access token. Its signature, issuer and expiry are
 * checked against {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} (set in the config
 * server's {@code gateway.properties}); failures get a {@code 401}. Only {@code /actuator/health}
 * is open. Valid requests are routed on with their {@code Authorization} header unchanged, and
 * the downstream services verify the token again themselves.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
