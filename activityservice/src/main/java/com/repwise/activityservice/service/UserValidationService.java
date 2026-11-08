package com.repwise.activityservice.service;

import com.repwise.activityservice.exception.ForwardedTokenRejectedException;
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
 *
 * <p>The call forwards the caller's own access token as a Bearer header, because the user service
 * requires a valid token on every endpoint. If the token is rejected, for example because it
 * expired between the two calls, the user service answers {@code 401} and this class reports it
 * as a rejected token, not as the user service being unavailable.
 */
@Service
@RequiredArgsConstructor
public class UserValidationService {

    private final WebClient userServiceWebClient;

    /**
     * Checks whether a user exists.
     *
     * @param userId the identifier of the user
     * @param accessToken the caller's raw access token, forwarded to the user service
     * @return {@code true} if the user service confirms the user exists
     * @throws ForwardedTokenRejectedException if the user service rejects the forwarded token
     *         ({@code 401})
     * @throws UserServiceUnavailableException if the user service responds with any other error
     *         status or cannot be reached
     */
    public boolean validateUser(String userId, String accessToken) {
        try {
            return Boolean.TRUE.equals(
                    userServiceWebClient.get()
                            .uri("/api/users/{userId}/validate", userId)
                            .headers(headers -> headers.setBearerAuth(accessToken))
                            .retrieve()
                            .bodyToMono(Boolean.class)
                            .block()
            );
        } catch (WebClientResponseException.Unauthorized e) {
            throw new ForwardedTokenRejectedException(e);
        } catch (WebClientResponseException e) {
            throw new UserServiceUnavailableException(
                    "User service responded with status " + e.getStatusCode().value(), e);
        } catch (WebClientRequestException e) {
            throw new UserServiceUnavailableException("Request could not reach the user service", e);
        }
    }
}
