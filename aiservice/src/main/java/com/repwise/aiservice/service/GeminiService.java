package com.repwise.aiservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Thin client for the Gemini {@code generateContent} API.
 *
 * <p>The endpoint, API key and time limit come from {@code gemini.api.url},
 * {@code gemini.api.key} and {@code gemini.api.timeout}. The
 * key is sent in the {@code x-goog-api-key} header so it does not end up in logged URLs. This
 * class only sends a prompt and returns the raw response body; interpreting it is left to the
 * caller.
 */
@Service
public class GeminiService {

    private final WebClient webClient;
    private final String apiUrl;
    private final String apiKey;
    private final Duration timeout;

    public GeminiService(@Value("${gemini.api.url}") String apiUrl,
                         @Value("${gemini.api.key}") String apiKey,
                         @Value("${gemini.api.timeout}") Duration timeout) {
        this.webClient = WebClient.create();
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.timeout = timeout;
    }

    /**
     * Sends a prompt to Gemini and waits for the answer.
     *
     * <p>The request asks for a JSON response ({@code responseMimeType}), which makes the model
     * return bare JSON instead of Markdown-fenced text. The call blocks the calling thread,
     * which is fine because this service is a servlet application and the caller is a message
     * listener thread.
     *
     * <p>Gemini must give its complete answer within the time limit ({@code gemini.api.timeout},
     * 30 seconds by default), counted from when the request is sent to the last byte of the
     * answer. Otherwise the call fails, so a hanging Gemini is retried and dead-lettered by the
     * listener like any other error instead of blocking it forever.
     *
     * @param prompt the full prompt text
     * @return the raw JSON response body from Gemini
     * @throws org.springframework.web.reactive.function.client.WebClientResponseException if
     *         Gemini answers with an error status
     * @throws RuntimeException if Gemini cannot be reached or has not answered completely within
     *         the time limit (the cause is a {@link java.util.concurrent.TimeoutException})
     */
    public String getAnswer(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json")
        );

        return webClient.post()
                .uri(apiUrl)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(timeout)
                .block();
    }
}
