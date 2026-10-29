package com.repwise.activityservice.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configures the HTTP clients used for service-to-service communication.
 *
 * <p>The builder is load balanced, so host names such as {@code USER-SERVICE} are resolved to
 * live instances through the Eureka registry rather than through DNS.
 */
@Configuration
public class WebClientConfig {

    /**
     * Provides a {@link WebClient.Builder} that resolves service names through Eureka.
     *
     * @return the load-balanced builder
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder(){
        return WebClient.builder();
    }

    /**
     * Provides a client pre-configured to call the user service.
     *
     * @param webClientBuilder the load-balanced builder
     * @return a client whose base URL is {@code http://USER-SERVICE}
     */
    @Bean
    public WebClient userServiceWebClient(WebClient.Builder webClientBuilder){
        return webClientBuilder
                .baseUrl("http://USER-SERVICE")
                .build();
    }
}
