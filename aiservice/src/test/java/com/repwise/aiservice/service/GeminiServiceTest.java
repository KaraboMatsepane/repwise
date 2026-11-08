package com.repwise.aiservice.service;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Specifies how {@link GeminiService} is supposed to behave. Every test is derived from the
 * documented contract, not from the implementation:
 *
 * <ul>
 *   <li><b>Class Javadoc:</b> a thin client for Gemini's {@code generateContent} API; the endpoint
 *       and key come from {@code gemini.api.url} and {@code gemini.api.key}; the key is sent in the
 *       {@code x-goog-api-key} header "so it does not end up in logged URLs"; it "only sends a
 *       prompt and returns the raw response body; interpreting it is left to the caller".</li>
 *   <li><b>Javadoc of {@code getAnswer}:</b> the request asks for a JSON response
 *       ({@code responseMimeType}); it throws {@link WebClientResponseException} if Gemini answers
 *       with an error status, and fails if Gemini has not given a complete answer within the time
 *       limit ({@code gemini.api.timeout}, 30 seconds by default).</li>
 *   <li><b>README, "Failed messages and the dead-letter queue", and the Javadoc of
 *       {@link ActivityMessageListener}:</b> a Gemini error must reach the listener as an exception,
 *       so the message is retried and then dead-lettered instead of being lost.</li>
 * </ul>
 *
 * <p>No real Gemini call is made. Each test starts a fake Gemini on a random local port using the
 * JDK's built-in HTTP server, and points the service at it through its configured URL, exactly as
 * {@code gemini.api.url} would. The service's real {@code WebClient} does the real HTTP and JSON
 * work against it.
 */
class GeminiServiceTest {

    private static final String API_KEY = "test-api-key-123";
    private static final String MODEL_PATH = "/v1beta/models/gemini-2.5-flash:generateContent";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /** A request as the fake Gemini received it. */
    private record ReceivedRequest(String method, URI uri, Headers headers, String body) {
    }

    /** Time limit for the tests that are not about timing: far longer than any local reply. */
    private static final Duration GENEROUS_TIMEOUT = Duration.ofSeconds(10);

    private final List<ReceivedRequest> received = new CopyOnWriteArrayList<>();

    private HttpServer fakeGemini;
    private ExecutorService fakeGeminiThreads;

    /** How the fake Gemini behaves; set by each test. */
    private enum Behaviour {
        /** Answers straight away. */
        ANSWERS,
        /** Answers after {@link #answerDelay}. */
        ANSWERS_LATE,
        /** Accepts the request and never answers. */
        NEVER_ANSWERS,
        /** Sends the status and headers, then never sends the rest of the body. */
        STALLS_MID_ANSWER
    }

    private Behaviour behaviour = Behaviour.ANSWERS;
    private Duration answerDelay = Duration.ZERO;
    private int responseStatus = 200;
    private String responseBody = "{}";

    /** Released after each test, so a fake Gemini that is "hanging" lets go of its thread. */
    private final CountDownLatch endOfTest = new CountDownLatch(1);

    private GeminiService service;

    @BeforeEach
    void startFakeGemini() throws IOException {
        fakeGemini = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        fakeGemini.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new ReceivedRequest(
                    exchange.getRequestMethod(), exchange.getRequestURI(), exchange.getRequestHeaders(), body));

            byte[] answer = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");

            switch (behaviour) {
                case NEVER_ANSWERS -> {
                    hangUntilTheTestEnds();
                    return;
                }
                case STALLS_MID_ANSWER -> {
                    exchange.sendResponseHeaders(responseStatus, answer.length);
                    OutputStream out = exchange.getResponseBody();
                    out.write(answer, 0, answer.length / 2);
                    out.flush();
                    hangUntilTheTestEnds();
                    return;
                }
                case ANSWERS_LATE -> pause(answerDelay);
                case ANSWERS -> { }
            }

            exchange.sendResponseHeaders(responseStatus, answer.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(answer);
            }
        });
        fakeGeminiThreads = Executors.newCachedThreadPool();
        fakeGemini.setExecutor(fakeGeminiThreads);
        fakeGemini.start();

        service = new GeminiService(fakeGeminiUrl(), API_KEY, GENEROUS_TIMEOUT);
    }

    @AfterEach
    void stopFakeGemini() {
        endOfTest.countDown();
        fakeGemini.stop(0);
        fakeGeminiThreads.shutdownNow();
    }

    private void hangUntilTheTestEnds() {
        try {
            endOfTest.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String fakeGeminiUrl() {
        return "http://127.0.0.1:" + fakeGemini.getAddress().getPort() + MODEL_PATH;
    }

    private void geminiAnswers(int status, String body) {
        responseStatus = status;
        responseBody = body;
    }

    private ReceivedRequest theOnlyRequest() {
        assertThat(received).as("exactly one request is sent to Gemini").hasSize(1);
        return received.getFirst();
    }

    private JsonNode sentJson() {
        return jsonMapper.readTree(theOnlyRequest().body());
    }

    @Nested
    @DisplayName("the request it sends")
    class Request {

        @Test
        @DisplayName("is one POST to the configured generateContent URL")
        void postsToTheConfiguredUrl() {
            service.getAnswer("hello");

            ReceivedRequest request = theOnlyRequest();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.uri().getPath()).isEqualTo(MODEL_PATH);
        }

        @Test
        @DisplayName("is sent as JSON")
        void isJson() {
            service.getAnswer("hello");

            assertThat(theOnlyRequest().headers().getFirst("Content-Type")).startsWith("application/json");
        }

        @Test
        @DisplayName("carries the prompt as the text of the first part of the first content")
        void carriesThePrompt() {
            service.getAnswer("Review my 37 minute run");

            assertThat(sentJson().path("contents").path(0).path("parts").path(0).path("text").asString())
                    .isEqualTo("Review my 37 minute run");
        }

        @Test
        @DisplayName("carries the prompt unchanged, including quotes, newlines, backslashes and non-ASCII text")
        void carriesThePromptUnchanged() {
            String prompt = """
                    Line one with "quotes" and a backslash \\ here.
                    Line two: {"looks": "like json"} <tags> & ampersands
                    Unicode: café, 5 km/h → 12°C, 跑步 🏃
                    """;

            service.getAnswer(prompt);

            assertThat(sentJson().path("contents").path(0).path("parts").path(0).path("text").asString())
                    .isEqualTo(prompt);
        }

        @Test
        @DisplayName("asks Gemini for a JSON response")
        void asksForJson() {
            service.getAnswer("hello");

            assertThat(sentJson().path("generationConfig").path("responseMimeType").asString())
                    .isEqualTo("application/json");
        }
    }

    @Nested
    @DisplayName("the API key")
    class ApiKey {

        @Test
        @DisplayName("is sent in the x-goog-api-key header")
        void isSentInTheHeader() {
            service.getAnswer("hello");

            assertThat(theOnlyRequest().headers().get("x-goog-api-key")).containsExactly(API_KEY);
        }

        @Test
        @DisplayName("never appears in the URL, so it cannot end up in logged URLs")
        void neverAppearsInTheUrl() {
            service.getAnswer("hello");

            assertThat(theOnlyRequest().uri().toString()).doesNotContain(API_KEY);
        }

        @Test
        @DisplayName("is not sent in the request body")
        void isNotInTheBody() {
            service.getAnswer("hello");

            assertThat(theOnlyRequest().body()).doesNotContain(API_KEY);
        }
    }

    @Nested
    @DisplayName("the answer it returns")
    class Answer {

        @Test
        @DisplayName("is Gemini's response body, unchanged")
        void isTheRawBody() {
            String geminiResponse = """
                    {
                      "candidates": [ { "content": { "parts": [ { "text": "{\\"analysis\\": {}}" } ] } } ],
                      "usageMetadata": { "totalTokenCount": 42 }
                    }""";
            geminiAnswers(200, geminiResponse);

            assertThat(service.getAnswer("hello")).isEqualTo(geminiResponse);
        }

        @Test
        @DisplayName("is returned as-is even when it is not in Gemini's usual shape, because interpreting it is the caller's job")
        void isNotInterpreted() {
            geminiAnswers(200, "{\"unexpected\": \"shape\"}");

            assertThat(service.getAnswer("hello")).isEqualTo("{\"unexpected\": \"shape\"}");
        }
    }

    @Nested
    @DisplayName("when Gemini fails")
    class Failures {

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {400, 401, 403, 404, 429, 500, 503})
        @DisplayName("an error status is thrown as WebClientResponseException, never returned as an answer")
        void errorStatusIsThrown(int status) {
            geminiAnswers(status, "{\"error\": {\"code\": " + status + ", \"message\": \"failed\"}}");

            assertThatThrownBy(() -> service.getAnswer("hello"))
                    .isInstanceOf(WebClientResponseException.class)
                    .satisfies(e -> assertThat(((WebClientResponseException) e).getStatusCode().value())
                            .isEqualTo(status));
        }

        @Test
        @DisplayName("an unreachable Gemini is thrown, never returned as an answer")
        void unreachableIsThrown() throws IOException {
            int closedPort;
            try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
                closedPort = socket.getLocalPort();
            }
            GeminiService unreachable = new GeminiService(
                    "http://127.0.0.1:" + closedPort + MODEL_PATH, API_KEY, GENEROUS_TIMEOUT);

            assertThatThrownBy(() -> unreachable.getAnswer("hello")).isInstanceOf(RuntimeException.class);
        }
    }

    /**
     * Gemini must answer within the configured time limit ({@code gemini.api.timeout}, 30 seconds by
     * default). A call that takes longer fails like any other Gemini error, so the listener retries
     * it and then dead-letters it, instead of the listener waiting forever and holding up every
     * message behind it.
     *
     * <p>These tests use a time limit of a fraction of a second so they run quickly.
     */
    @Nested
    @DisplayName("the time limit")
    class TimeLimit {

        private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);

        /** Far below what a hanging call would take, but far above the time limit. */
        private static final Duration GIVES_UP_WITHIN = Duration.ofSeconds(5);

        private GeminiService withShortTimeout() {
            return new GeminiService(fakeGeminiUrl(), API_KEY, SHORT_TIMEOUT);
        }

        @Test
        @DisplayName("a Gemini that never answers fails the call instead of waiting forever")
        void neverAnsweringFails() {
            behaviour = Behaviour.NEVER_ANSWERS;
            GeminiService service = withShortTimeout();

            assertTimeoutPreemptively(GIVES_UP_WITHIN, () ->
                    assertThatThrownBy(() -> service.getAnswer("hello")).isInstanceOf(RuntimeException.class));
        }

        @Test
        @DisplayName("a Gemini that stops in the middle of its answer fails the call instead of waiting forever")
        void stallingMidAnswerFails() {
            behaviour = Behaviour.STALLS_MID_ANSWER;
            geminiAnswers(200, "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"a long answer\"}]}}]}");
            GeminiService service = withShortTimeout();

            assertTimeoutPreemptively(GIVES_UP_WITHIN, () ->
                    assertThatThrownBy(() -> service.getAnswer("hello")).isInstanceOf(RuntimeException.class));
        }

        @Test
        @DisplayName("a Gemini that answers too late fails the call; the late answer is not returned")
        void lateAnswerFails() {
            behaviour = Behaviour.ANSWERS_LATE;
            answerDelay = Duration.ofSeconds(2);
            GeminiService service = withShortTimeout();

            assertTimeoutPreemptively(GIVES_UP_WITHIN, () ->
                    assertThatThrownBy(() -> service.getAnswer("hello")).isInstanceOf(RuntimeException.class));
        }

        @Test
        @DisplayName("an answer that arrives within the time limit is returned normally")
        void answerWithinTheLimitIsReturned() {
            behaviour = Behaviour.ANSWERS_LATE;
            answerDelay = Duration.ofMillis(200);
            geminiAnswers(200, "{\"ok\": true}");
            GeminiService service = new GeminiService(fakeGeminiUrl(), API_KEY, Duration.ofSeconds(3));

            assertThat(service.getAnswer("hello")).isEqualTo("{\"ok\": true}");
        }
    }
}
