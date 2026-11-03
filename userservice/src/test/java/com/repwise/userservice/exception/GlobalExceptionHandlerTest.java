package com.repwise.userservice.exception;

import com.repwise.userservice.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Specifies how exceptions are turned into HTTP responses. The handler is called directly with a
 * fake request, so no web server or Spring context is needed.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("a missing email is reported as 400 Bad Request, not as a server error")
    void missingEmailIsABadRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");

        ResponseEntity<ErrorResponse> response =
                handler.handleMissingEmail(new MissingEmailException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.error()).isEqualTo("Bad Request");
        assertThat(body.path()).isEqualTo("/api/users/me");
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.details()).isEmpty();
    }

    @Test
    @DisplayName("the error message tells the user what to fix")
    void missingEmailMessageExplainsTheProblem() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");

        ErrorResponse body = handler.handleMissingEmail(new MissingEmailException(), request).getBody();

        assertThat(body).isNotNull();
        assertThat(body.message()).containsIgnoringCase("email");
        assertThat(body.message()).containsIgnoringCase("profile");
    }
}
