package com.repwise.activityservice.service;

import com.repwise.activityservice.exception.UserServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Verifies that a user exists by calling the user service.
 *
 * <p>The request goes to {@code GET /api/users/{userId}/validate} through a load-balanced
 * {@link WebClient}, so the target instance is resolved through Eureka. The call is blocking
 * because the surrounding service layer is synchronous.
 */
@Service
@RequiredArgsConstructor
public class UserValidationService {

    private final WebClient userServiceWebClient;

    /**
     * Checks whether a user exists.
     *
     * @param userId the identifier of the user
     * @return {@code true} if the user service confirms the user exists
     * @throws UserServiceUnavailableException if the user service responds with an error status
     *         or cannot be reached
     */
    public boolean validateUser(String userId) {
        try {
            return Boolean.TRUE.equals(
                    userServiceWebClient.get()
                            .uri("/api/users/{userId}/validate", userId)
                            .retrieve()
                            .bodyToMono(Boolean.class)
                            .block()
            );
        } catch (WebClientResponseException e) {
            throw new UserServiceUnavailableException(
                    "User service responded with status " + e.getStatusCode().value(), e);
        } catch (WebClientRequestException e) {
            throw new UserServiceUnavailableException("Request could not reach the user service", e);
        }
    }
}
