package com.repwise.aiservice.service;

import com.repwise.aiservice.exception.RecommendationNotFoundException;
import com.repwise.aiservice.model.Recommendation;
import com.repwise.aiservice.repository.RecommendationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Specifies how {@link RecommendationService} must behave, as documented on the class: a user can
 * list their own recommendations, and can read the recommendation for an activity only if it is
 * theirs.
 *
 * <p>The database is replaced by a Mockito mock of {@link RecommendationRepository}, so these are
 * plain unit tests with no Spring context, no MongoDB and no network.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final String ALICE = "alice-id";
    private static final String BOB = "bob-id";

    @Mock
    private RecommendationRepository repository;

    private RecommendationService service() {
        return new RecommendationService(repository);
    }

    /** A stored recommendation, as the database would return it. */
    private Recommendation recommendation(String id, String activityId, String ownerId) {
        return Recommendation.builder()
                .id(id)
                .activityId(activityId)
                .userId(ownerId)
                .activityType("RUNNING")
                .analysis(new Recommendation.Analysis(
                        "Solid session", "Steady pace", "No heart rate data", "420 kcal is reasonable"))
                .improvements(List.of("Add intervals"))
                .suggestions(List.of("Try a recovery walk tomorrow"))
                .safety(List.of("Hydrate well"))
                .createdAt(LocalDateTime.of(2026, 11, 2, 7, 45))
                .build();
    }

    @Nested
    @DisplayName("getUserRecommendations")
    class GetUserRecommendations {

        @Test
        @DisplayName("returns the user's recommendations with their full content")
        void returnsTheUsersRecommendations() {
            Recommendation stored = recommendation("r1", "act-1", ALICE);
            when(repository.findByUserId(ALICE)).thenReturn(List.of(stored));

            List<Recommendation> result = service().getUserRecommendations(ALICE);

            assertThat(result).hasSize(1);
            Recommendation recommendation = result.get(0);
            assertThat(recommendation.getId()).isEqualTo("r1");
            assertThat(recommendation.getActivityId()).isEqualTo("act-1");
            assertThat(recommendation.getUserId()).isEqualTo(ALICE);
            assertThat(recommendation.getActivityType()).isEqualTo("RUNNING");
            assertThat(recommendation.getAnalysis().overall()).isEqualTo("Solid session");
            assertThat(recommendation.getImprovements()).containsExactly("Add intervals");
            assertThat(recommendation.getSuggestions()).containsExactly("Try a recovery walk tomorrow");
            assertThat(recommendation.getSafety()).containsExactly("Hydrate well");
        }

        @Test
        @DisplayName("returns every recommendation of the user, none dropped")
        void returnsEveryRecommendation() {
            when(repository.findByUserId(ALICE)).thenReturn(List.of(
                    recommendation("r1", "act-1", ALICE),
                    recommendation("r2", "act-2", ALICE),
                    recommendation("r3", "act-3", ALICE)));

            assertThat(service().getUserRecommendations(ALICE))
                    .extracting(Recommendation::getId)
                    .containsExactly("r1", "r2", "r3");
        }

        @Test
        @DisplayName("is empty, not an error, when the user has no recommendations yet")
        void isEmptyWhenThereAreNone() {
            when(repository.findByUserId(ALICE)).thenReturn(List.of());

            assertThat(service().getUserRecommendations(ALICE)).isEmpty();
        }

        @Test
        @DisplayName("only the requested user's recommendations are looked up")
        void looksUpOnlyTheRequestedUser() {
            when(repository.findByUserId(ALICE)).thenReturn(List.of(recommendation("r1", "act-1", ALICE)));

            service().getUserRecommendations(ALICE);

            verify(repository).findByUserId(ALICE);
            verify(repository, never()).findByUserId(BOB);
            verify(repository, never()).findAll();
        }
    }

    @Nested
    @DisplayName("getActivityRecommendation")
    class GetActivityRecommendation {

        @Test
        @DisplayName("the owner can read the recommendation for their activity, with its full content")
        void ownerReadsTheirRecommendation() {
            when(repository.findByActivityId("act-1")).thenReturn(Optional.of(recommendation("r1", "act-1", ALICE)));

            Recommendation result = service().getActivityRecommendation("act-1", ALICE);

            assertThat(result.getId()).isEqualTo("r1");
            assertThat(result.getActivityId()).isEqualTo("act-1");
            assertThat(result.getUserId()).isEqualTo(ALICE);
            assertThat(result.getAnalysis().pace()).isEqualTo("Steady pace");
            assertThat(result.getImprovements()).containsExactly("Add intervals");
        }

        @Test
        @DisplayName("the recommendation is looked up by activity id")
        void isLookedUpByActivityId() {
            when(repository.findByActivityId("act-1")).thenReturn(Optional.of(recommendation("r1", "act-1", ALICE)));

            service().getActivityRecommendation("act-1", ALICE);

            verify(repository).findByActivityId("act-1");
        }

        @Test
        @DisplayName("another user cannot read it, even though it exists")
        void anotherUserCannotReadIt() {
            when(repository.findByActivityId("act-1")).thenReturn(Optional.of(recommendation("r1", "act-1", ALICE)));

            assertThatThrownBy(() -> service().getActivityRecommendation("act-1", BOB))
                    .isInstanceOf(RecommendationNotFoundException.class);
        }

        @Test
        @DisplayName("an activity with no recommendation is reported as not found")
        void missingRecommendationIsNotFound() {
            when(repository.findByActivityId("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service().getActivityRecommendation("ghost", ALICE))
                    .isInstanceOf(RecommendationNotFoundException.class)
                    .hasMessage("No recommendation found for activity: ghost");
        }

        @Test
        @DisplayName("someone else's recommendation looks exactly like a missing one, so its existence is not revealed")
        void otherUsersRecommendationLooksLikeAMissingOne() {
            lenient().when(repository.findByActivityId("act-1"))
                    .thenReturn(Optional.of(recommendation("r1", "act-1", ALICE)));
            lenient().when(repository.findByActivityId("ghost")).thenReturn(Optional.empty());

            Throwable forOthersRecommendation = catchThrowable(() -> service().getActivityRecommendation("act-1", BOB));
            Throwable forMissingRecommendation = catchThrowable(() -> service().getActivityRecommendation("ghost", BOB));

            assertThat(forOthersRecommendation).isInstanceOf(RecommendationNotFoundException.class);
            assertThat(forMissingRecommendation).isInstanceOf(RecommendationNotFoundException.class);
            assertThat(forOthersRecommendation).hasMessage("No recommendation found for activity: act-1");
            assertThat(forMissingRecommendation).hasMessage("No recommendation found for activity: ghost");
        }
    }
}
