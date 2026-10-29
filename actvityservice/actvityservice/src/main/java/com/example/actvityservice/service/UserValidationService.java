package com.example.actvityservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
@RequiredArgsConstructor
public class UserValidationService {

    private final WebClient userServiceWebClient;

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
            throw new RuntimeException("user id " + userId + " not found");
        } catch (WebClientRequestException e) {
            throw new RuntimeException("Request could not reach the user service");
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error: something went wrong");
        }
    }
}
