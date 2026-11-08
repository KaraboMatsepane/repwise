package com.repwise.aiservice.service;

import com.repwise.aiservice.model.Activity;
import com.repwise.aiservice.model.Recommendation;
import com.repwise.aiservice.repository.RecommendationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Specifies what {@link ActivityMessageListener} does with one message: generate a recommendation
 * and save it, unless the activity already has one. Any failure must surface as an exception,
 * never be swallowed, because the message container reacts to the exception by retrying and
 * finally dead-lettering the message. A swallowed failure would acknowledge the message and lose
 * it silently. An activity that already has a recommendation is not a failure: the work is done.
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

        verify(recommendationRepository, never()).save(any(Recommendation.class));
    }

    /**
     * An activity has at most one recommendation. RabbitMQ can deliver the same message more than
     * once (for example after a connection drop before the acknowledgement), and a second
     * recommendation would cost a second Gemini call and leave the activity with two answers.
     */
    @Nested
    @DisplayName("an activity that already has a recommendation")
    class AlreadyRecommended {

        @Test
        @DisplayName("is not sent to Gemini again")
        void isNotSentToGeminiAgain() {
            when(recommendationRepository.existsByActivityId("act-1")).thenReturn(true);

            listener().processActivity(activity());

            verifyNoInteractions(activityAiService);
        }

        @Test
        @DisplayName("does not get a second recommendation")
        void doesNotGetASecondRecommendation() {
            when(recommendationRepository.existsByActivityId("act-1")).thenReturn(true);

            listener().processActivity(activity());

            verify(recommendationRepository, never()).save(any(Recommendation.class));
        }

        @Test
        @DisplayName("is acknowledged, not treated as a failure to retry or dead-letter")
        void isAcknowledged() {
            when(recommendationRepository.existsByActivityId("act-1")).thenReturn(true);

            assertThatCode(() -> listener().processActivity(activity())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("that was saved by a parallel delivery a moment earlier is acknowledged, not retried or dead-lettered")
        void lostRaceIsAcknowledged() {
            when(recommendationRepository.existsByActivityId("act-1")).thenReturn(false);
            when(activityAiService.generateRecommendation(any(Activity.class))).thenReturn(generated());
            when(recommendationRepository.save(any(Recommendation.class)))
                    .thenThrow(new DuplicateKeyException("E11000 duplicate key error, index: activityId"));

            assertThatCode(() -> listener().processActivity(activity())).doesNotThrowAnyException();
        }
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
