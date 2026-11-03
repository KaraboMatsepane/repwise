package com.repwise.aiservice.service;

import com.repwise.aiservice.model.Activity;
import com.repwise.aiservice.model.Recommendation;
import com.repwise.aiservice.repository.RecommendationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Specifies what {@link ActivityMessageListener} does with one message: generate a recommendation
 * and save it. Any failure must surface as an exception, never be swallowed, because the message
 * container reacts to the exception by retrying and finally dead-lettering the message. A
 * swallowed failure would acknowledge the message and lose it silently.
 *
 * <p>Retrying and dead-lettering themselves are specified in
 * {@code ActivityMessageListenerDeadLetterTest}, against a real RabbitMQ.
 */
@ExtendWith(MockitoExtension.class)
class ActivityMessageListenerTest {

    @Mock
    private ActivityAiService activityAiService;
    @Mock
    private RecommendationRepository recommendationRepository;

    private Activity activity() {
        Activity activity = new Activity();
        activity.setId("act-1");
        activity.setUserId("alice-id");
        activity.setType("RUNNING");
        return activity;
    }

    private Recommendation generated() {
        return Recommendation.builder().activityId("act-1").userId("alice-id").build();
    }

    private ActivityMessageListener listener() {
        return new ActivityMessageListener(activityAiService, recommendationRepository);
    }

    @Test
    @DisplayName("the recommendation generated for the activity is saved")
    void savesTheGeneratedRecommendation() {
        Activity activity = activity();
        Recommendation generated = generated();
        when(activityAiService.generateRecommendation(activity)).thenReturn(generated);
        when(recommendationRepository.save(generated)).thenReturn(generated);

        listener().processActivity(activity);

        verify(recommendationRepository).save(generated);
    }

    @Test
    @DisplayName("a failure while generating the recommendation is passed on, not swallowed, and nothing is saved")
    void generationFailureIsPassedOn() {
        IllegalStateException failure = new IllegalStateException("Gemini reply was not JSON");
        when(activityAiService.generateRecommendation(any(Activity.class))).thenThrow(failure);

        assertThatThrownBy(() -> listener().processActivity(activity())).isSameAs(failure);

        verifyNoInteractions(recommendationRepository);
    }

    @Test
    @DisplayName("a failure while saving is passed on, not swallowed")
    void saveFailureIsPassedOn() {
        RuntimeException failure = new RuntimeException("database is down");
        when(activityAiService.generateRecommendation(any(Activity.class))).thenReturn(generated());
        when(recommendationRepository.save(any(Recommendation.class))).thenThrow(failure);

        assertThatThrownBy(() -> listener().processActivity(activity())).isSameAs(failure);
    }
}
