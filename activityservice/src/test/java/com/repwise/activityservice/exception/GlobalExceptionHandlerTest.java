package com.repwise.activityservice.exception;

import com.repwise.activityservice.controller.ActivityController;
import com.repwise.activityservice.dto.ActivityRequest;
import com.repwise.activityservice.dto.ErrorResponse;
import com.repwise.activityservice.model.ActivityType;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.as;
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

    /**
     * README, "Features" and "Error Handling": "Invalid input returns a 400 listing every failed
     * field", with per-field messages in {@code details}; {@code type}, {@code duration} (greater
     * than 0), {@code caloriesBurned} (0 or more) and {@code startTime} are required.
     *
     * <p>The exception is produced by running the real Bean Validation rules of
     * {@link ActivityRequest}, the same way {@code @Valid} does, so the test sees the field errors a
     * real request would produce.
     */
    @Nested
    @DisplayName("an invalid activity request")
    class InvalidActivityRequest {

        private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();

        {
            validator.afterPropertiesSet();
        }

        private ResponseEntity<ErrorResponse> handle(ActivityRequest invalid) throws NoSuchMethodException {
            BeanPropertyBindingResult errors = new BeanPropertyBindingResult(invalid, "activityRequest");
            validator.validate(invalid, errors);
            MethodParameter body = new MethodParameter(
                    ActivityController.class.getMethod("trackActivity", ActivityRequest.class, Jwt.class), 0);

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/activities");
            return handler.handleValidation(new MethodArgumentNotValidException(body, errors), request);
        }

        private ActivityRequest validRequest() {
            ActivityRequest request = new ActivityRequest();
            request.setType(ActivityType.RUNNING);
            request.setDuration(45);
            request.setCaloriesBurned(420);
            request.setStartTime(LocalDateTime.of(2026, 11, 2, 6, 30));
            return request;
        }

        /** The details entries that mention a field, as the client sees them. */
        private List<String> detailsAbout(ErrorResponse body, String field) {
            return body.details().stream().filter(detail -> detail.contains(field)).toList();
        }

        @Test
        @DisplayName("is 400 Bad Request with the standard error body")
        void isABadRequest() throws Exception {
            ActivityRequest invalid = validRequest();
            invalid.setDuration(0);

            ResponseEntity<ErrorResponse> response = handle(invalid);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ErrorResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.status()).isEqualTo(400);
            assertThat(body.error()).isEqualTo("Bad Request");
            assertThat(body.path()).isEqualTo("/api/activities");
            assertThat(body.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("lists every missing required field, by name")
        void listsEveryMissingField() throws Exception {
            ErrorResponse body = handle(new ActivityRequest()).getBody();

            assertThat(body).isNotNull();
            assertThat(body.details()).hasSize(4);
            for (String field : List.of("type", "duration", "caloriesBurned", "startTime")) {
                assertThat(detailsAbout(body, field)).as("details mention %s", field).hasSize(1);
            }
        }

        @Test
        @DisplayName("lists only the fields that broke a rule")
        void listsOnlyTheBrokenFields() throws Exception {
            ActivityRequest invalid = validRequest();
            invalid.setDuration(0);
            invalid.setCaloriesBurned(-1);

            ErrorResponse body = handle(invalid).getBody();

            assertThat(body).isNotNull();
            assertThat(body.details()).hasSize(2);
            assertThat(detailsAbout(body, "duration")).hasSize(1);
            assertThat(detailsAbout(body, "caloriesBurned")).hasSize(1);
        }

        @Test
        @DisplayName("each entry says what is wrong, not just which field")
        void eachEntrySaysWhatIsWrong() throws Exception {
            ActivityRequest invalid = validRequest();
            invalid.setDuration(0);

            ErrorResponse body = handle(invalid).getBody();

            assertThat(body).isNotNull();
            assertThat(body.details()).singleElement(as(InstanceOfAssertFactories.STRING))
                    .startsWith("duration")
                    .hasSizeGreaterThan("duration".length() + 2);
        }
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
                "connection to mongodb://admin:s3cr3t-pw@10.0.0.12:27017 failed in ActivityRepository";

        private ResponseEntity<ErrorResponse> handle() {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/activities");
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
            assertThat(body.path()).isEqualTo("/api/activities");
            assertThat(body.timestamp()).isNotNull();
            assertThat(body.details()).isEmpty();
        }

        @Test
        @DisplayName("exposes nothing about the cause to the client")
        void exposesNoInternals() {
            String everythingTheClientSees = String.valueOf(handle().getBody());

            assertThat(everythingTheClientSees)
                    .doesNotContain("s3cr3t-pw")
                    .doesNotContain("mongodb://")
                    .doesNotContain("10.0.0.12")
                    .doesNotContain("ActivityRepository")
                    .doesNotContain("IllegalStateException");
        }

        @Test
        @DisplayName("is logged with its stack trace, so it can still be investigated")
        void isLoggedWithItsStackTrace(CapturedOutput output) {
            handle();

            assertThat(output.getOut() + output.getErr())
                    .contains("/api/activities")
                    .contains("IllegalStateException")
                    .contains(INTERNALS)
                    .contains("at com.repwise.activityservice.exception.GlobalExceptionHandlerTest");
        }
    }
}
