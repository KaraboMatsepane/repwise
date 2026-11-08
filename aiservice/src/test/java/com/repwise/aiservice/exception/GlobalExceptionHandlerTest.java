package com.repwise.aiservice.exception;

import com.repwise.aiservice.dto.ErrorResponse;
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
 * Specifies how exceptions are turned into HTTP responses, from the README's "Error Handling"
 * section. The handler is called directly with a fake request, so no web server or Spring context
 * is needed.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

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
                "query on mongodb+srv://ai:s3cr3t-pw@cluster0.example.net/recommendations failed "
                        + "in RecommendationRepository";

        private ResponseEntity<ErrorResponse> handle() {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/recommendations");
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
            assertThat(body.path()).isEqualTo("/api/recommendations");
            assertThat(body.timestamp()).isNotNull();
            assertThat(body.details()).isEmpty();
        }

        @Test
        @DisplayName("exposes nothing about the cause to the client")
        void exposesNoInternals() {
            String everythingTheClientSees = String.valueOf(handle().getBody());

            assertThat(everythingTheClientSees)
                    .doesNotContain("s3cr3t-pw")
                    .doesNotContain("mongodb+srv://")
                    .doesNotContain("cluster0.example.net")
                    .doesNotContain("RecommendationRepository")
                    .doesNotContain("IllegalStateException");
        }

        @Test
        @DisplayName("is logged with its stack trace, so it can still be investigated")
        void isLoggedWithItsStackTrace(CapturedOutput output) {
            handle();

            assertThat(output.getOut() + output.getErr())
                    .contains("/api/recommendations")
                    .contains("IllegalStateException")
                    .contains(INTERNALS)
                    .contains("at com.repwise.aiservice.exception.GlobalExceptionHandlerTest");
        }
    }
}
