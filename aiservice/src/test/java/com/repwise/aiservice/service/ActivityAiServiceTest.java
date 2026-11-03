package com.repwise.aiservice.service;

import com.repwise.aiservice.model.Activity;
import com.repwise.aiservice.model.Recommendation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Specifies how {@link ActivityAiService} must behave, as documented on the class: it describes an
 * activity to Gemini in a prompt that asks for a fixed JSON shape, and turns the reply into an
 * unsaved {@link Recommendation} linked to the activity and its user, or throws
 * {@link IllegalStateException} if the reply is missing or is not the expected JSON.
 *
 * <p>No real Gemini call is ever made: {@link GeminiService}, the only class that talks to the
 * network, is a Mockito mock that returns canned replies. The JSON mapper is a real one, so the
 * parsing itself is exercised for real.
 */
@ExtendWith(MockitoExtension.class)
class ActivityAiServiceTest {

    private static final String ALICE = "alice-id";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private GeminiService geminiService;

    private ActivityAiService service;

    @BeforeEach
    void setUp() {
        service = new ActivityAiService(geminiService, jsonMapper);
    }

    private Activity runningActivity() {
        Activity activity = new Activity();
        activity.setId("act-1");
        activity.setUserId(ALICE);
        activity.setType("RUNNING");
        // Deliberately unusual numbers: the prompt's own instructions mention "45 minutes" and
        // "420 calories" as examples, so those values could not prove the activity's data was used.
        activity.setDuration(37);
        activity.setCaloriesBurned(413);
        activity.setStartTime(LocalDateTime.of(2026, 11, 2, 6, 30));
        activity.setAdditionalMetrics(Map.of("distanceKm", 7.2));
        return activity;
    }

    /** The text the model is asked to produce: a complete, well-formed recommendation. */
    private static final String FULL_REPLY = """
            {
              "analysis": {
                "overall": "A solid morning run",
                "pace": "About 6.25 minutes per km",
                "heartRate": "No heart rate was provided",
                "caloriesBurned": "420 calories is about 9.3 per minute"
              },
              "improvements": ["Add strides", "Warm up longer", "Track heart rate"],
              "suggestions": ["Easy 30 minute run on Thursday", "Intervals on Saturday", "Rest tomorrow"],
              "safety": ["Hydrate after an early run", "Stretch calves"]
            }
            """;

    /** Wraps the model's text in Gemini's response envelope: candidates[0].content.parts[0].text. */
    private String geminiEnvelope(String modelText) {
        return jsonMapper.writeValueAsString(Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of("parts", List.of(Map.of("text", modelText)))))));
    }

    private void geminiReturns(String modelText) {
        when(geminiService.getAnswer(anyString())).thenReturn(geminiEnvelope(modelText));
    }

    @Nested
    @DisplayName("the prompt sent to Gemini")
    class Prompt {

        private String capturePrompt(Activity activity) {
            geminiReturns(FULL_REPLY);
            service.generateRecommendation(activity);
            ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
            verify(geminiService).getAnswer(prompt.capture());
            return prompt.getValue();
        }

        @Test
        @DisplayName("describes the session with the activity's own numbers, so advice can be specific")
        void containsTheActivitysNumbers() {
            String prompt = capturePrompt(runningActivity());

            assertThat(prompt).contains("RUNNING");
            assertThat(prompt).contains("37");
            assertThat(prompt).contains("413");
            assertThat(prompt).contains("2026-11-02T06:30");
        }

        @Test
        @DisplayName("includes the additional metrics")
        void containsTheAdditionalMetrics() {
            String prompt = capturePrompt(runningActivity());

            assertThat(prompt).contains("distanceKm").contains("7.2");
        }

        @Test
        @DisplayName("reflects a different activity, not a fixed example")
        void reflectsTheGivenActivity() {
            Activity swim = runningActivity();
            swim.setType("SWIMMING");
            swim.setDuration(53);
            swim.setCaloriesBurned(289);

            String prompt = capturePrompt(swim);

            assertThat(prompt).contains("SWIMMING").contains("53").contains("289");
            assertThat(prompt).doesNotContain("RUNNING").doesNotContain("37").doesNotContain("413");
        }

        @Test
        @DisplayName("asks for the fixed JSON shape the reply is parsed from")
        void asksForTheExpectedJsonShape() {
            String prompt = capturePrompt(runningActivity());

            assertThat(prompt).contains("\"analysis\"", "\"overall\"", "\"pace\"", "\"heartRate\"",
                    "\"caloriesBurned\"", "\"improvements\"", "\"suggestions\"", "\"safety\"");
        }

        @Test
        @DisplayName("Gemini is asked exactly once per activity")
        void geminiIsAskedOnce() {
            geminiReturns(FULL_REPLY);

            service.generateRecommendation(runningActivity());

            verify(geminiService, times(1)).getAnswer(anyString());
        }
    }

    @Nested
    @DisplayName("turning Gemini's reply into a recommendation")
    class Parsing {

        @Test
        @DisplayName("the recommendation is linked to the activity and its user")
        void isLinkedToTheActivityAndItsUser() {
            geminiReturns(FULL_REPLY);

            Recommendation recommendation = service.generateRecommendation(runningActivity());

            assertThat(recommendation.getActivityId()).isEqualTo("act-1");
            assertThat(recommendation.getUserId()).isEqualTo(ALICE);
            assertThat(recommendation.getActivityType()).isEqualTo("RUNNING");
        }

        @Test
        @DisplayName("the four parts of the analysis are read from the reply")
        void readsTheAnalysis() {
            geminiReturns(FULL_REPLY);

            Recommendation.Analysis analysis = service.generateRecommendation(runningActivity()).getAnalysis();

            assertThat(analysis.overall()).isEqualTo("A solid morning run");
            assertThat(analysis.pace()).isEqualTo("About 6.25 minutes per km");
            assertThat(analysis.heartRate()).isEqualTo("No heart rate was provided");
            assertThat(analysis.caloriesBurned()).isEqualTo("420 calories is about 9.3 per minute");
        }

        @Test
        @DisplayName("improvements, suggestions and safety notes are read in order")
        void readsTheLists() {
            geminiReturns(FULL_REPLY);

            Recommendation recommendation = service.generateRecommendation(runningActivity());

            assertThat(recommendation.getImprovements())
                    .containsExactly("Add strides", "Warm up longer", "Track heart rate");
            assertThat(recommendation.getSuggestions())
                    .containsExactly("Easy 30 minute run on Thursday", "Intervals on Saturday", "Rest tomorrow");
            assertThat(recommendation.getSafety())
                    .containsExactly("Hydrate after an early run", "Stretch calves");
        }

        @Test
        @DisplayName("the recommendation is not saved: it has no id and no creation time yet")
        void isNotSaved() {
            geminiReturns(FULL_REPLY);

            Recommendation recommendation = service.generateRecommendation(runningActivity());

            assertThat(recommendation.getId()).isNull();
            assertThat(recommendation.getCreatedAt()).isNull();
        }

        @Test
        @DisplayName("sections the model left out become empty lists, never null")
        void missingListsBecomeEmpty() {
            geminiReturns("""
                    { "analysis": { "overall": "Short session", "pace": "", "heartRate": "", "caloriesBurned": "" } }
                    """);

            Recommendation recommendation = service.generateRecommendation(runningActivity());

            assertThat(recommendation.getImprovements()).isNotNull().isEmpty();
            assertThat(recommendation.getSuggestions()).isNotNull().isEmpty();
            assertThat(recommendation.getSafety()).isNotNull().isEmpty();
            assertThat(recommendation.getAnalysis().overall()).isEqualTo("Short session");
        }

        @Test
        @DisplayName("fields the model adds that we do not know about are ignored")
        void unknownFieldsAreIgnored() {
            geminiReturns("""
                    {
                      "analysis": { "overall": "Fine", "pace": "p", "heartRate": "h", "caloriesBurned": "c" },
                      "improvements": ["one"],
                      "suggestions": ["two"],
                      "safety": ["three"],
                      "motivationalQuote": "No pain, no gain"
                    }
                    """);

            Recommendation recommendation = service.generateRecommendation(runningActivity());

            assertThat(recommendation.getImprovements()).containsExactly("one");
        }

        @Test
        @DisplayName("only the first candidate of Gemini's reply is used")
        void usesTheFirstCandidate() {
            String second = jsonMapper.writeValueAsString(Map.of("analysis",
                    Map.of("overall", "SECOND", "pace", "", "heartRate", "", "caloriesBurned", "")));
            String envelope = jsonMapper.writeValueAsString(Map.of("candidates", List.of(
                    Map.of("content", Map.of("parts", List.of(Map.of("text", FULL_REPLY)))),
                    Map.of("content", Map.of("parts", List.of(Map.of("text", second)))))));
            when(geminiService.getAnswer(anyString())).thenReturn(envelope);

            Recommendation recommendation = service.generateRecommendation(runningActivity());

            assertThat(recommendation.getAnalysis().overall()).isEqualTo("A solid morning run");
        }
    }

    @Nested
    @DisplayName("replies wrapped in Markdown code fences")
    class MarkdownFences {

        /** What the same reply produces with no fence at all: the reference for every case below. */
        private Recommendation plainResult() {
            geminiReturns(FULL_REPLY);
            return service.generateRecommendation(runningActivity());
        }

        private Recommendation resultFor(String modelText) {
            geminiReturns(modelText);
            return service.generateRecommendation(runningActivity());
        }

        @Test
        @DisplayName("a reply fenced as ```json is unwrapped and gives the same recommendation")
        void fenceWithJsonTag() {
            Recommendation expected = plainResult();

            Recommendation actual = resultFor("```json\n" + FULL_REPLY + "```");

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        @DisplayName("a fence with no language tag is unwrapped")
        void fenceWithoutLanguageTag() {
            Recommendation expected = plainResult();

            Recommendation actual = resultFor("```\n" + FULL_REPLY + "\n```");

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        @DisplayName("the language tag may be in any case, such as ```JSON")
        void fenceWithUpperCaseTag() {
            Recommendation expected = plainResult();

            Recommendation actual = resultFor("```JSON\n" + FULL_REPLY + "\n```");

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        @DisplayName("Windows line endings inside the fence are tolerated")
        void fenceWithWindowsLineEndings() {
            Recommendation expected = plainResult();

            Recommendation actual = resultFor("```json\r\n" + FULL_REPLY.replace("\n", "\r\n") + "```");

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        @DisplayName("a sentence before and after the fenced block is ignored")
        void fenceWithSurroundingProse() {
            Recommendation expected = plainResult();

            Recommendation actual = resultFor(
                    "Here is your coaching plan:\n\n```json\n" + FULL_REPLY + "```\n\nLet me know if you want more.");

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        @DisplayName("when there are several fenced blocks, only the first one is used")
        void onlyTheFirstFenceIsUsed() {
            Recommendation expected = plainResult();

            Recommendation actual = resultFor(
                    "```json\n" + FULL_REPLY + "```\n\nTo log your next run:\n```bash\necho done\n```");

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        @DisplayName("plain JSON is never altered: backticks inside its text are kept")
        void backticksInsideUnfencedJsonAreKept() {
            Recommendation recommendation = resultFor("""
                    {
                      "analysis": { "overall": "Use `tempo` runs", "pace": "p", "heartRate": "h", "caloriesBurned": "c" },
                      "improvements": ["Log it with ```notes``` after each run"],
                      "suggestions": [],
                      "safety": []
                    }
                    """);

            assertThat(recommendation.getAnalysis().overall()).isEqualTo("Use `tempo` runs");
            assertThat(recommendation.getImprovements()).containsExactly("Log it with ```notes``` after each run");
        }

        @Test
        @DisplayName("a fence that is never closed, as in a cut-off reply, is rejected")
        void unclosedFenceIsRejected() {
            geminiReturns("```json\n{ \"analysis\": { \"overall\": \"Solid ses");

            assertThatThrownBy(() -> service.generateRecommendation(runningActivity()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("act-1");
        }

        @Test
        @DisplayName("a fenced block that does not contain the expected JSON is still rejected")
        void fencedProseIsRejected() {
            geminiReturns("```\nSorry, I cannot help with that.\n```");

            assertThatThrownBy(() -> service.generateRecommendation(runningActivity()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("act-1");
        }
    }

    @Nested
    @DisplayName("when Gemini's reply cannot be used")
    class Failures {

        @Test
        @DisplayName("a reply that is not JSON at all is rejected with IllegalStateException naming the activity")
        void notJson() {
            when(geminiService.getAnswer(anyString())).thenReturn("Sorry, I cannot help with that.");

            assertThatThrownBy(() -> service.generateRecommendation(runningActivity()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("act-1");
        }

        @Test
        @DisplayName("a missing reply is rejected with IllegalStateException")
        void nullReply() {
            when(geminiService.getAnswer(anyString())).thenReturn(null);

            assertThatThrownBy(() -> service.generateRecommendation(runningActivity()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("an empty reply is rejected with IllegalStateException")
        void emptyReply() {
            when(geminiService.getAnswer(anyString())).thenReturn("");

            assertThatThrownBy(() -> service.generateRecommendation(runningActivity()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("a reply with no candidates (for example a blocked prompt) is rejected, naming the activity")
        void noCandidates() {
            when(geminiService.getAnswer(anyString()))
                    .thenReturn("{\"promptFeedback\": {\"blockReason\": \"SAFETY\"}}");

            assertThatThrownBy(() -> service.generateRecommendation(runningActivity()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("act-1");
        }

        @Test
        @DisplayName("a candidate without text is rejected")
        void candidateWithoutText() {
            when(geminiService.getAnswer(anyString())).thenReturn(
                    jsonMapper.writeValueAsString(Map.of("candidates",
                            List.of(Map.of("content", Map.of("parts", List.of(Map.of())))))));

            assertThatThrownBy(() -> service.generateRecommendation(runningActivity()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("act-1");
        }

        @Test
        @DisplayName("text that is prose instead of the requested JSON is rejected, naming the activity")
        void textIsNotJson() {
            geminiReturns("Great run! You should keep it up and drink more water.");

            assertThatThrownBy(() -> service.generateRecommendation(runningActivity()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("act-1");
        }

        @Test
        @DisplayName("an error from the Gemini call itself is passed on unchanged, so the message can be retried")
        void geminiFailureIsPassedOn() {
            RuntimeException failure = new RuntimeException("503 from Gemini");
            when(geminiService.getAnswer(anyString())).thenThrow(failure);

            assertThatThrownBy(() -> service.generateRecommendation(runningActivity()))
                    .isSameAs(failure);
        }
    }
}
