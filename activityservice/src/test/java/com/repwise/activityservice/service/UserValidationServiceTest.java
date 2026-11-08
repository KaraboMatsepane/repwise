package com.repwise.activityservice.service;

import com.repwise.activityservice.exception.ForwardedTokenRejectedException;
import com.repwise.activityservice.exception.UserServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Specifies how {@link UserValidationService} is supposed to behave. Every test is derived from
 * the documented contract, not from the implementation:
 *
 * <ul>
 *   <li><b>README, "How it works" step 3 and "Activity Service":</b> before saving an activity
 *       the service asks the user service whether the user has a profile, through
 *       {@code http://USER-SERVICE} (resolved via Eureka), forwarding the caller's token, and the
 *       activity is rejected if not.</li>
 *   <li><b>README, "API Reference":</b> the question is {@code GET /api/users/{userId}/validate},
 *       which answers {@code true} only for a user that has a profile.</li>
 *   <li><b>README, "Error Handling":</b> {@code 401} when the forwarded token is rejected by the
 *       User Service (for example it expired between the two calls); {@code 503} when "the
 *       Activity Service cannot reach the User Service, or it responds with any other
 *       error".</li>
 *   <li><b>Javadoc of {@code validateUser}:</b> returns {@code true} only "if the user service
 *       confirms the user exists"; throws {@link ForwardedTokenRejectedException} when the user
 *       service answers {@code 401}, and {@link UserServiceUnavailableException} for any other
 *       error status or when the user service cannot be reached.</li>
 * </ul>
 *
 * <p>Exception messages are deliberately not asserted: no spec defines them, and the global
 * exception handler replaces them with a generic message before they reach a client.
 *
 * <p>The network is replaced by a stub {@link ExchangeFunction} plugged into a real
 * {@link WebClient}, so no server, Eureka registry or load balancer is needed.
 */
class UserValidationServiceTest {

    private static final String ALICE = "alice-id";
    private static final String ALICE_TOKEN = "alice-raw-access-token";
    private static final String USER_SERVICE = "http://USER-SERVICE";

    /** Every request the service sent, in order. */
    private final List<ClientRequest> requests = new ArrayList<>();

    /** How the stub user service answers; set by each test. */
    private ExchangeFunction userService;

    private UserValidationService service;

    @BeforeEach
    void setUp() {
        // Mirrors the userServiceWebClient bean: a client whose base URL is the user service's
        // registry name.
        WebClient client = WebClient.builder()
                .baseUrl(USER_SERVICE)
                .exchangeFunction(request -> {
                    requests.add(request);
                    return userService.exchange(request);
                })
                .build();
        service = new UserValidationService(client);
    }

    private void userServiceAnswers(MediaType contentType, String body) {
        userService = request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, contentType.toString())
                .body(body)
                .build());
    }

    private void userServiceAnswers(String jsonBody) {
        userServiceAnswers(MediaType.APPLICATION_JSON, jsonBody);
    }

    private void userServiceFailsWith(HttpStatus status) {
        userService = request -> Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"status\":" + status.value() + "}")
                .build());
    }

    /**
     * Asserts the documented guarantee for an answer that is not a confirmation: the user is not
     * accepted. Either {@code false} or an exception satisfies it, because either way the activity
     * service rejects the activity; only {@code true} would be wrong.
     */
    private void assertUserIsNotAccepted() {
        Boolean[] result = new Boolean[1];
        Throwable thrown = catchThrowable(() -> result[0] = service.validateUser(ALICE, ALICE_TOKEN));

        if (thrown == null) {
            assertThat(result[0]).as("an answer that is not a confirmation must not accept the user")
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("asks the user service")
    class AsksTheUserService {

        @Test
        @DisplayName("with GET /api/users/{userId}/validate for the given user")
        void usesTheValidateEndpoint() {
            userServiceAnswers("true");

            service.validateUser(ALICE, ALICE_TOKEN);

            assertThat(requests).hasSize(1);
            assertThat(requests.getFirst().method()).isEqualTo(HttpMethod.GET);
            assertThat(requests.getFirst().url().getPath())
                    .isEqualTo("/api/users/" + ALICE + "/validate");
        }

        @Test
        @DisplayName("by its registry name, so Eureka resolves the instance")
        void addressesTheUserServiceByName() {
            userServiceAnswers("true");

            service.validateUser(ALICE, ALICE_TOKEN);

            assertThat(requests.getFirst().url().getScheme()).isEqualTo("http");
            assertThat(requests.getFirst().url().getHost()).isEqualTo("USER-SERVICE");
        }

        @Test
        @DisplayName("with the caller's own token forwarded as a Bearer token")
        void forwardsTheCallersToken() {
            userServiceAnswers("true");

            service.validateUser(ALICE, ALICE_TOKEN);

            assertThat(requests.getFirst().headers().get(HttpHeaders.AUTHORIZATION))
                    .containsExactly("Bearer " + ALICE_TOKEN);
        }

        @Test
        @DisplayName("about exactly the given user, even if the id contains path characters")
        void asksAboutExactlyTheGivenUser() {
            userServiceAnswers("true");

            service.validateUser("../admin", ALICE_TOKEN);

            // The id must stay one path segment of the validate endpoint, never reach another path.
            assertThat(requests.getFirst().url().getRawPath())
                    .startsWith("/api/users/")
                    .endsWith("/validate")
                    .doesNotContain("/../");
        }
    }

    @Nested
    @DisplayName("accepts the user")
    class Accepts {

        @Test
        @DisplayName("only when the user service confirms with true")
        void whenConfirmed() {
            userServiceAnswers("true");

            assertThat(service.validateUser(ALICE, ALICE_TOKEN)).isTrue();
        }
    }

    @Nested
    @DisplayName("does not accept the user")
    class DoesNotAccept {

        @Test
        @DisplayName("when the user service answers false")
        void whenDenied() {
            userServiceAnswers("false");

            assertThat(service.validateUser(ALICE, ALICE_TOKEN)).isFalse();
        }

        @Test
        @DisplayName("when the answer is null")
        void whenTheAnswerIsNull() {
            userServiceAnswers("null");

            assertUserIsNotAccepted();
        }

        @Test
        @DisplayName("when the answer is empty")
        void whenTheAnswerIsEmpty() {
            userService = request -> Mono.just(ClientResponse.create(HttpStatus.OK).build());

            assertUserIsNotAccepted();
        }

        @Test
        @DisplayName("when the answer is not a boolean")
        void whenTheAnswerIsNotABoolean() {
            userServiceAnswers("{\"exists\":true}");

            assertUserIsNotAccepted();
        }

        @Test
        @DisplayName("when the answer is not JSON at all")
        void whenTheAnswerIsNotJson() {
            userServiceAnswers(MediaType.TEXT_HTML, "<html>true</html>");

            assertUserIsNotAccepted();
        }
    }

    @Nested
    @DisplayName("reports the caller's token as rejected")
    class TokenRejected {

        @Test
        @DisplayName("when the user service answers 401, for example because the token expired")
        void whenTheUserServiceRejectsTheToken() {
            userServiceFailsWith(HttpStatus.UNAUTHORIZED);

            assertThatThrownBy(() -> service.validateUser(ALICE, ALICE_TOKEN))
                    .isInstanceOf(ForwardedTokenRejectedException.class);
        }

        @Test
        @DisplayName("and not as the user service being unavailable")
        void notAsUnavailable() {
            userServiceFailsWith(HttpStatus.UNAUTHORIZED);

            assertThatThrownBy(() -> service.validateUser(ALICE, ALICE_TOKEN))
                    .isNotInstanceOf(UserServiceUnavailableException.class);
        }
    }

    @Nested
    @DisplayName("reports the user service as unavailable")
    class Unavailable {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = HttpStatus.class, names = {
                "BAD_REQUEST", "FORBIDDEN", "NOT_FOUND",
                "INTERNAL_SERVER_ERROR", "BAD_GATEWAY", "SERVICE_UNAVAILABLE"})
        @DisplayName("when it responds with any other error status")
        void whenItRespondsWithAnError(HttpStatus status) {
            userServiceFailsWith(status);

            assertThatThrownBy(() -> service.validateUser(ALICE, ALICE_TOKEN))
                    .isInstanceOf(UserServiceUnavailableException.class);
        }

        @Test
        @DisplayName("when it cannot be reached")
        void whenItCannotBeReached() {
            userService = request -> Mono.error(new WebClientRequestException(
                    new ConnectException("Connection refused"),
                    request.method(), request.url(), request.headers()));

            assertThatThrownBy(() -> service.validateUser(ALICE, ALICE_TOKEN))
                    .isInstanceOf(UserServiceUnavailableException.class);
        }
    }
}
