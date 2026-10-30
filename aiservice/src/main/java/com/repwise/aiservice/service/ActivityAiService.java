package com.repwise.aiservice.service;

import com.repwise.aiservice.model.Activity;
import com.repwise.aiservice.model.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Turns an {@link Activity} into a {@link Recommendation} by asking Gemini.
 *
 * <p>The activity is described in a prompt that asks for a fixed JSON shape
 * ({@code analysis}, {@code improvements}, {@code suggestions}, {@code safety}). The reply is
 * parsed into a {@link Recommendation}. Saving it is the caller's job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityAiService {

    private final GeminiService geminiService;
    private final JsonMapper jsonMapper;

    /**
     * Generates a recommendation for an activity.
     *
     * @param activity the activity to analyse
     * @return an unsaved recommendation linked to the activity and its user
     * @throws IllegalStateException if Gemini's reply is missing or is not the expected JSON
     */
    public Recommendation generateRecommendation(Activity activity) {
        String response = geminiService.getAnswer(createPrompt(activity));
        log.debug("Gemini response for activity {}: {}", activity.getId(), response);

        return parseResponse(activity, response);
    }

    /**
     * Builds the prompt for an activity, including the exact JSON shape expected back.
     */
    private String createPrompt(Activity activity) {
        return """
                You are a personal coach reviewing one specific workout that your client just
                logged. Write to them directly ("you", "your"), like a coach who watched the
                session, not like a generic fitness article.

                The session:
                Activity type: %s
                Duration: %s minutes
                Calories burned: %s
                Start time: %s
                Additional metrics: %s

                Rules:
                - Ground every point in the numbers above. Quote them (for example "45 minutes",
                  "420 calories") and work out useful figures from them, such as calories per
                  minute or pace, instead of speaking in generalities.
                - Use the activity type: advice for weight lifting must differ from advice for
                  running or swimming, and should name concrete exercises, drills or techniques
                  that fit that activity.
                - Use the start time: consider the time of day (early morning, evening and so on)
                  and what it means for warm-up, recovery, sleep or fuelling.
                - Use every additional metric that is present, and say what it tells you.
                - If a metric you would need (such as heart rate or pace) was not provided, say
                  so in one short sentence and give the best advice possible without inventing
                  a value.
                - Never give advice that would apply equally to any workout. If a point could be
                  copied onto someone else's session unchanged, rewrite it using this session.
                - Be specific and actionable: say what to do, how much, and why it matters for
                  this session. Keep each item to one or two sentences.
                - Do not use Markdown, emojis or headings inside the strings.

                Respond with only a JSON object in exactly this shape:
                {
                  "analysis": {
                    "overall": "a short overall assessment of the session",
                    "pace": "pace analysis",
                    "heartRate": "heart rate analysis",
                    "caloriesBurned": "calories analysis"
                  },
                  "improvements": [
                    "exactly 3 items. Each names one weakness or missed opportunity visible in this
                     session's numbers, then the specific change to make next time and the
                     expected benefit"
                  ],
                  "suggestions": [
                    "exactly 3 items. Each is a concrete next workout or progression that follows
                     from this session, with a rough duration, intensity or target that builds
                     on today's figures and says when to do it"
                  ],
                  "safety": [
                    "2 to 3 items. Each is a precaution tied to the risks of this activity type,
                     this duration and effort, and this time of day, and says what to watch for
                     and what to do about it. No generic reminders"
                  ]
                }
                """.formatted(
                activity.getType(),
                activity.getDuration(),
                activity.getCaloriesBurned(),
                activity.getStartTime(),
                activity.getAdditionalMetrics());
    }

    /**
     * Extracts the model's text from Gemini's envelope
     * ({@code candidates[0].content.parts[0].text}) and maps it onto a {@link Recommendation}.
     */
    private Recommendation parseResponse(Activity activity, String response) {
        try {
            JsonNode text = jsonMapper.readTree(response)
                    .path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text");

            if (text.isMissingNode() || text.isNull()) {
                throw new IllegalStateException("Gemini returned no text for activity " + activity.getId());
            }

            JsonNode analysis = jsonMapper.readTree(text.asString());

            return Recommendation.builder()
                    .activityId(activity.getId())
                    .userId(activity.getUserId())
                    .activityType(activity.getType())
                    .recommendations(joinAnalysis(analysis.path("analysis")))
                    .improvements(toList(analysis.path("improvements")))
                    .suggestions(toList(analysis.path("suggestions")))
                    .safety(toList(analysis.path("safety")))
                    .build();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not parse Gemini response for activity " + activity.getId(), e);
        }
    }

    /**
     * Joins the non-empty parts of the analysis (overall, pace, heart rate, calories) into one
     * paragraph, one part per line, because a {@link Recommendation} stores a single string.
     */
    private String joinAnalysis(JsonNode analysis) {
        return Stream.of("overall", "pace", "heartRate", "caloriesBurned")
                .map(key -> analysis.path(key).asString(""))
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private List<String> toList(JsonNode array) {
        List<String> items = new ArrayList<>();
        array.forEach(item -> items.add(item.asString()));
        return items;
    }
}
