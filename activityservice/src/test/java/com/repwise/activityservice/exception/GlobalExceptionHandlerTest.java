package com.repwise.activityservice.exception;

import com.repwise.activityservice.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Specifies how exceptions are turned into HTTP responses, from the README's "Error Handling"
 * section: every error uses the same JSON shape, and each case has a fixed status.
 *
 * <p>The handler is called directly with a fake request, so no web server or Spring context is
 * needed.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Nested
    @DisplayName("a token the user service rejected")
    class ForwardedTokenRejected {

        private ResponseEntity<ErrorResponse> handle() {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/activities");
            return handler.handleForwardedTokenRejected(new ForwardedTokenRejectedException(null), request);
        }

        @Test
        @DisplayName("is 401 Unauthorized, so the client knows to get a new token")
        void isUnauthorized() {
            assertThat(handle().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("uses the standard error body")
        void usesTheStandardErrorBody() {
            ErrorResponse body = handle().getBody();

            assertThat(body).isNotNull();
            assertThat(body.status()).isEqualTo(401);
            assertThat(body.error()).isEqualTo("Unauthorized");
            assertThat(body.path()).isEqualTo("/api/activities");
            assertThat(body.timestamp()).isNotNull();
            assertThat(body.details()).isEmpty();
        }

        @Test
        @DisplayName("says the token is the problem, not that a service is unavailable")
        void blamesTheToken() {
            ErrorResponse body = handle().getBody();

            assertThat(body).isNotNull();
            assertThat(body.message()).containsIgnoringCase("token");
            assertThat(body.message()).doesNotContainIgnoringCase("unavailable");
        }

        @Test
        @DisplayName("carries a Bearer WWW-Authenticate header, like any other 401 from a resource server")
        void carriesABearerChallenge() {
            assertThat(handle().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                    .startsWith("Bearer")
                    .contains("invalid_token");
        }
    }
}
