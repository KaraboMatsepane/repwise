package com.repwise.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security rules for the user service, which acts as an OAuth2 resource server.
 *
 * <p>Every request must carry a valid Keycloak access token ({@code Authorization: Bearer ...}).
 * The token's signature, issuer and expiry are checked against the realm configured in
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}; failures get a {@code 401}.
 * The service is stateless (no sessions) and CSRF protection is off because no cookies are used.
 *
 * <p>The only open path is {@code GET /api/users/{id}/validate}, which the activity service
 * calls without a token. This is temporary and should be closed once the caller forwards the
 * user's token.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/users/*/validate").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
