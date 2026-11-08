package com.repwise.activityservice.exception;

import com.repwise.activityservice.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Central place where exceptions thrown anywhere below a controller are turned into HTTP
 * responses. Controllers and services just throw; this class decides status code and body.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ActivityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleActivityNotFound(
            ActivityNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(InvalidUserException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUser(
            InvalidUserException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of());
    }

    /**
     * The user service rejected the forwarded token, usually because it expired. The client must
     * get a new token, so this is a {@code 401} with the same {@code WWW-Authenticate} challenge
     * the security filter sends, not a {@code 503}.
     */
    @ExceptionHandler(ForwardedTokenRejectedException.class)
    public ResponseEntity<ErrorResponse> handleForwardedTokenRejected(
            ForwardedTokenRejectedException ex, HttpServletRequest request) {
        log.warn("User service rejected the forwarded token on {}", request.getRequestURI());
        ErrorResponse body = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Your access token was rejected, it may have expired. Get a new token and try again.",
                request.getRequestURI(), LocalDateTime.now(), List.of());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer error=\"invalid_token\", error_description=\"The access token was rejected\"")
                .body(body);
    }

    @ExceptionHandler(UserServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleUserServiceUnavailable(
            UserServiceUnavailableException ex, HttpServletRequest request) {
        log.error("User service call failed", ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "User service is unavailable, try again later",
                request, List.of());
    }

    /** Malformed JSON, or a value that does not fit its type (e.g. an unknown activity type). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed or unreadable request body", request, List.of());
    }

    /** Raised by {@code @Valid} when a request body breaks a Bean Validation rule. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, details);
    }

    /** Fallback for anything not handled above. Logs the cause but never leaks it to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred", request, List.of());
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String message, HttpServletRequest request, List<String> details) {
        ErrorResponse body = new ErrorResponse(
                status.value(), status.getReasonPhrase(), message,
                request.getRequestURI(), LocalDateTime.now(), details);
        return ResponseEntity.status(status).body(body);
    }
}
