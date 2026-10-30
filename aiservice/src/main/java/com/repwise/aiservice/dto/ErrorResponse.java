package com.repwise.aiservice.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The JSON body returned for every failed request, so clients always see the same shape.
 *
 * @param status    the HTTP status code
 * @param error     the HTTP reason phrase, e.g. "Not Found"
 * @param message   a human-readable description
 * @param path      the request path that failed
 * @param timestamp when the error happened
 * @param details   per-field validation messages; empty for non-validation errors
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp,
        List<String> details) {
}
