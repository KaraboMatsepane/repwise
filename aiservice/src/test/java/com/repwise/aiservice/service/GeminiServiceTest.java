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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 *       with an error status.</li>
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

    private final List<ReceivedRequest> received = new ArrayList<>();

    private HttpServer fakeGemini;

    /** What the fake Gemini answers with; set by each test. */
    private int responseStatus = 200;
    private String responseBody = "{}";

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
            exchange.sendResponseHeaders(responseStatus, answer.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(answer);
            }
        });
        fakeGemini.start();

        service = new GeminiService(fakeGeminiUrl(), API_KEY);
    }

    @AfterEach
    void stopFakeGemini() {
        fakeGemini.stop(0);
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
            GeminiService unreachable =
                    new GeminiService("http://127.0.0.1:" + closedPort + MODEL_PATH, API_KEY);

            assertThatThrownBy(() -> unreachable.getAnswer("hello")).isInstanceOf(RuntimeException.class);
        }
    }
}
