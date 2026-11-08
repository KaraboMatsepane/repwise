package com.repwise.userservice.exception;

import com.repwise.userservice.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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

    /**
     * README, "Error Handling": "Anything unexpected is logged with its stack trace and returned as
     * a generic 500, so internal details are not exposed."
     */
    @Nested
    @DisplayName("an unexpected error")
    @ExtendWith(OutputCaptureExtension.class)
    class UnexpectedError {

        /** A message full of the kind of internals that must never reach a client. */
        private static final String INTERNALS =
                "could not connect to jdbc:postgresql://10.0.0.7:5432/repwise_users_db as postgres/s3cr3t-pw";

        private ResponseEntity<ErrorResponse> handle() {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
            return handler.handleUnexpected(new IllegalStateException(INTERNALS), request);
        }

        @Test
        @DisplayName("is 500 Internal Server Error with the standard error body")
        void isAServerError() {
            ResponseEntity<ErrorResponse> response = handle();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            ErrorResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.status()).isEqualTo(500);
            assertThat(body.error()).isEqualTo("Internal Server Error");
            assertThat(body.path()).isEqualTo("/api/users/me");
            assertThat(body.timestamp()).isNotNull();
            assertThat(body.details()).isEmpty();
        }

        @Test
        @DisplayName("exposes nothing about the cause to the client")
        void exposesNoInternals() {
            String everythingTheClientSees = String.valueOf(handle().getBody());

            assertThat(everythingTheClientSees)
                    .doesNotContain("s3cr3t-pw")
                    .doesNotContain("jdbc:postgresql")
                    .doesNotContain("10.0.0.7")
                    .doesNotContain("repwise_users_db")
                    .doesNotContain("IllegalStateException");
        }

        @Test
        @DisplayName("is logged with its stack trace, so it can still be investigated")
        void isLoggedWithItsStackTrace(CapturedOutput output) {
            handle();

            assertThat(output.getOut() + output.getErr())
                    .contains("/api/users/me")
                    .contains("IllegalStateException")
                    .contains(INTERNALS)
                    .contains("at com.repwise.userservice.exception.GlobalExceptionHandlerTest");
        }
    }
}
