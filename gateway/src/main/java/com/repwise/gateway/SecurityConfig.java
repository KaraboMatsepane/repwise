package com.repwise.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * Security rules for the gateway, which acts as a reactive OAuth2 resource server.
 *
 * <p>Every request must carry a valid Keycloak access token. Its signature, issuer and expiry are
 * checked against {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} (set in the config
 * server's {@code gateway.properties}); failures get a {@code 401}. Only {@code /actuator/health}
 * and CORS preflight requests are open. Valid requests are routed on with their
 * {@code Authorization} header unchanged, and the downstream services verify the token again
 * themselves.
 *
 * <p>Browsers calling from another origin (the frontend) are allowed through CORS, which only the
 * gateway handles because browsers never call the services directly. A preflight {@code OPTIONS}
 * request carries no token, so the CORS filter in this chain answers it before the token check and
 * it never reaches a route. Rules are in {@link #corsConfigurationSource(List)}.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * CORS rules for browsers calling the gateway from another origin, picked up by
     * {@code .cors(Customizer.withDefaults())} in the filter chain.
     *
     * <p>Only the listed origins, the methods {@code GET} and {@code POST}, and the
     * {@code Authorization} and {@code Content-Type} headers are allowed, on every path. Anything
     * else gets a {@code 403} without CORS headers, so the browser blocks the request. A new
     * method such as {@code PUT} or {@code DELETE} must be added here before a browser can use
     * it. The preflight answer may be cached by the browser for an hour. Requests without an
     * {@code Origin} header, such as curl or service-to-service calls, are not affected: CORS
     * protects the user's browser, while the token check protects the API.
     *
     * @param allowedOrigins the exact origins (scheme, host and port) allowed to call the gateway,
     *        from {@code repwise.cors.allowed-origins}, set by {@code CORS_ALLOWED_ORIGINS} as a
     *        comma-separated list and defaulting to {@code http://localhost:5173}
     * @return the rules, applied to every path
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${repwise.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(allowedOrigins);
        corsConfig.setAllowedMethods(List.of("GET", "POST"));
        corsConfig.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        corsConfig.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return source;
    }

}
