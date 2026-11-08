package com.repwise.activityservice.service;

import com.repwise.activityservice.dto.ActivityRequest;
import com.repwise.activityservice.dto.ActivityResponse;
import com.repwise.activityservice.exception.ActivityNotFoundException;
import com.repwise.activityservice.exception.ForwardedTokenRejectedException;
import com.repwise.activityservice.exception.InvalidUserException;
import com.repwise.activityservice.exception.UserServiceUnavailableException;
import com.repwise.activityservice.model.Activity;
import com.repwise.activityservice.model.ActivityType;
import com.repwise.activityservice.repository.ActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Specifies how {@link ActivityService} must behave, as documented on the class: an activity is
 * owned by the user in the caller's token, is only recorded for a user that has a profile, is
 * published to RabbitMQ after it is saved, and can only be read by its owner.
 *
 * <p>The database, the user service and RabbitMQ are replaced by Mockito mocks, so these are plain
 * unit tests with no Spring context, no MongoDB and no network.
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    private static final String ALICE = "alice-id";
    private static final String BOB = "bob-id";
    private static final String ALICE_TOKEN = "alice-raw-access-token";

    private static final String EXCHANGE = "activity.exchange";
    private static final String ROUTING_KEY = "activity.tracking";

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 2, 6, 30);
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 2, 7, 45);

    @Mock
    private ActivityRepository repository;
    @Mock
    private UserValidationService userValidationService;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private ActivityService service;

    @BeforeEach
    void setUp() {
        service = new ActivityService(repository, userValidationService, rabbitTemplate);
        // These two come from configuration in the running application.
        ReflectionTestUtils.setField(service, "exchange", EXCHANGE);
        ReflectionTestUtils.setField(service, "routingKey", ROUTING_KEY);
    }

    private Jwt tokenFor(String sub, String tokenValue) {
        return Jwt.withTokenValue(tokenValue).header("alg", "none").subject(sub).build();
    }

    private ActivityRequest runRequest() {
        ActivityRequest request = new ActivityRequest();
        request.setType(ActivityType.RUNNING);
        request.setDuration(45);
        request.setCaloriesBurned(420);
        request.setStartTime(START);
        request.setAdditionalMetrics(Map.of("distanceKm", 7.2));
        return request;
    }

    /** An activity as the database holds it after saving: id and timestamps are filled in. */
    private Activity storedActivity(String id, String ownerId) {
        return Activity.builder()
                .id(id)
                .userId(ownerId)
                .type(ActivityType.RUNNING)
                .duration(45)
                .caloriesBurned(420)
                .startTime(START)
                .additionalMetrics(Map.of("distanceKm", 7.2))
                .createdAt(CREATED)
                .updatedAt(CREATED)
                .build();
    }

    /** Makes the repository behave like a database: it assigns an id and timestamps on save. */
    private void saveAssignsIdAndTimestamps(String generatedId) {
        when(repository.save(any(Activity.class))).thenAnswer(invocation -> {
            Activity activity = invocation.getArgument(0);
            activity.setId(generatedId);
            activity.setCreatedAt(CREATED);
            activity.setUpdatedAt(CREATED);
            return activity;
        });
    }

    @Nested
    @DisplayName("trackActivity")
    class TrackActivity {

        @Test
        @DisplayName("the activity is owned by the user in the caller's token")
        void ownerIsTheTokenSubject() {
            when(userValidationService.validateUser(ALICE, ALICE_TOKEN)).thenReturn(true);
            saveAssignsIdAndTimestamps("act-1");

            ActivityResponse response = service.trackActivity(runRequest(), tokenFor(ALICE, ALICE_TOKEN));

            ArgumentCaptor<Activity> saved = ArgumentCaptor.forClass(Activity.class);
            verify(repository).save(saved.capture());
            assertThat(saved.getValue().getUserId()).isEqualTo(ALICE);
            assertThat(response.getUserId()).isEqualTo(ALICE);
        }

        @Test
        @DisplayName("the caller's own token is forwarded to the user service for the profile check")
        void forwardsTheCallersTokenToTheUserService() {
            when(userValidationService.validateUser(ALICE, ALICE_TOKEN)).thenReturn(true);
            saveAssignsIdAndTimestamps("act-1");

            service.trackActivity(runRequest(), tokenFor(ALICE, ALICE_TOKEN));

            verify(userValidationService).validateUser(ALICE, ALICE_TOKEN);
        }

        @Test
        @DisplayName("every field of the request is stored on the activity")
        void storesEveryFieldOfTheRequest() {
            when(userValidationService.validateUser(ALICE, ALICE_TOKEN)).thenReturn(true);
            saveAssignsIdAndTimestamps("act-1");

            service.trackActivity(runRequest(), tokenFor(ALICE, ALICE_TOKEN));

            ArgumentCaptor<Activity> saved = ArgumentCaptor.forClass(Activity.class);
            verify(repository).save(saved.capture());
            Activity activity = saved.getValue();
            assertThat(activity.getType()).isEqualTo(ActivityType.RUNNING);
            assertThat(activity.getDuration()).isEqualTo(45);
            assertThat(activity.getCaloriesBurned()).isEqualTo(420);
            assertThat(activity.getStartTime()).isEqualTo(START);
            assertThat(activity.getAdditionalMetrics()).containsEntry("distanceKm", 7.2);
        }

        @Test
        @DisplayName("additional metrics are optional")
        void additionalMetricsAreOptional() {
            when(userValidationService.validateUser(ALICE, ALICE_TOKEN)).thenReturn(true);
            saveAssignsIdAndTimestamps("act-1");
            ActivityRequest request = runRequest();
            request.setAdditionalMetrics(null);

            ActivityResponse response = service.trackActivity(request, tokenFor(ALICE, ALICE_TOKEN));

            assertThat(response.getAdditionalMetrics()).isNull();
            assertThat(response.getId()).isEqualTo("act-1");
        }

        @Test
        @DisplayName("returns the saved activity with its generated id and timestamps")
        void returnsTheSavedActivity() {
            when(userValidationService.validateUser(ALICE, ALICE_TOKEN)).thenReturn(true);
            saveAssignsIdAndTimestamps("act-1");

            ActivityResponse response = service.trackActivity(runRequest(), tokenFor(ALICE, ALICE_TOKEN));

            assertThat(response.getId()).isEqualTo("act-1");
            assertThat(response.getType()).isEqualTo(ActivityType.RUNNING);
            assertThat(response.getDuration()).isEqualTo(45);
            assertThat(response.getCaloriesBurned()).isEqualTo(420);
            assertThat(response.getStartTime()).isEqualTo(START);
            assertThat(response.getAdditionalMetrics()).containsEntry("distanceKm", 7.2);
            assertThat(response.getCreatedAt()).isEqualTo(CREATED);
            assertThat(response.getUpdatedAt()).isEqualTo(CREATED);
        }

        @Test
        @DisplayName("the saved activity is published to RabbitMQ on the configured exchange and routing key")
        void publishesTheSavedActivity() {
            when(userValidationService.validateUser(ALICE, ALICE_TOKEN)).thenReturn(true);
            saveAssignsIdAndTimestamps("act-1");

            ActivityResponse response = service.trackActivity(runRequest(), tokenFor(ALICE, ALICE_TOKEN));

            ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
            verify(rabbitTemplate).convertAndSend(
                    org.mockito.ArgumentMatchers.eq(EXCHANGE),
                    org.mockito.ArgumentMatchers.eq(ROUTING_KEY),
                    published.capture());
            assertThat(published.getValue()).isEqualTo(response);
            assertThat(((ActivityResponse) published.getValue()).getId()).isEqualTo("act-1");
        }

        @Test
        @DisplayName("a RabbitMQ failure does not fail the request, because the activity is already saved")
        void aPublishingFailureDoesNotFailTheRequest() {
            when(userValidationService.validateUser(ALICE, ALICE_TOKEN)).thenReturn(true);
            saveAssignsIdAndTimestamps("act-1");
            doThrow(new AmqpConnectException(new IOException("broker is down")))
                    .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

            ActivityResponse response = service.trackActivity(runRequest(), tokenFor(ALICE, ALICE_TOKEN));

            assertThat(response.getId()).isEqualTo("act-1");
            verify(repository).save(any(Activity.class));
        }

        @Test
        @DisplayName("an unknown user is rejected and nothing is saved or published")
        void unknownUserIsRejected() {
            when(userValidationService.validateUser(ALICE, ALICE_TOKEN)).thenReturn(false);

            assertThatThrownBy(() -> service.trackActivity(runRequest(), tokenFor(ALICE, ALICE_TOKEN)))
                    .isInstanceOf(InvalidUserException.class)
                    .hasMessage("User not found: " + ALICE);

            verifyNoInteractions(repository, rabbitTemplate);
        }

        @Test
        @DisplayName("when the user service is unavailable the error is passed on and nothing is saved or published")
        void userServiceOutageIsPassedOn() {
            when(userValidationService.validateUser(ALICE, ALICE_TOKEN))
                    .thenThrow(new UserServiceUnavailableException("down", new RuntimeException()));

            assertThatThrownBy(() -> service.trackActivity(runRequest(), tokenFor(ALICE, ALICE_TOKEN)))
                    .isInstanceOf(UserServiceUnavailableException.class);

            verifyNoInteractions(repository, rabbitTemplate);
        }

        @Test
        @DisplayName("when the user service rejects the token the error is passed on and nothing is saved or published")
        void rejectedTokenIsPassedOn() {
            when(userValidationService.validateUser(ALICE, ALICE_TOKEN))
                    .thenThrow(new ForwardedTokenRejectedException(new RuntimeException()));

            assertThatThrownBy(() -> service.trackActivity(runRequest(), tokenFor(ALICE, ALICE_TOKEN)))
                    .isInstanceOf(ForwardedTokenRejectedException.class);

            verifyNoInteractions(repository, rabbitTemplate);
        }

        @Test
        @DisplayName("nothing is published when saving fails")
        void nothingIsPublishedWhenSavingFails() {
            when(userValidationService.validateUser(ALICE, ALICE_TOKEN)).thenReturn(true);
            when(repository.save(any(Activity.class))).thenThrow(new IllegalStateException("database is down"));

            assertThatThrownBy(() -> service.trackActivity(runRequest(), tokenFor(ALICE, ALICE_TOKEN)))
                    .isInstanceOf(IllegalStateException.class);

            verifyNoInteractions(rabbitTemplate);
        }
    }

    @Nested
    @DisplayName("getUserActivities")
    class GetUserActivities {

        @Test
        @DisplayName("returns the user's activities with all their fields")
        void returnsTheUsersActivities() {
            when(repository.findByUserId(ALICE)).thenReturn(List.of(storedActivity("a1", ALICE)));

            List<ActivityResponse> activities = service.getUserActivities(ALICE);

            assertThat(activities).hasSize(1);
            ActivityResponse activity = activities.get(0);
            assertThat(activity.getId()).isEqualTo("a1");
            assertThat(activity.getUserId()).isEqualTo(ALICE);
            assertThat(activity.getType()).isEqualTo(ActivityType.RUNNING);
            assertThat(activity.getDuration()).isEqualTo(45);
            assertThat(activity.getCaloriesBurned()).isEqualTo(420);
            assertThat(activity.getStartTime()).isEqualTo(START);
            assertThat(activity.getAdditionalMetrics()).containsEntry("distanceKm", 7.2);
            assertThat(activity.getCreatedAt()).isEqualTo(CREATED);
        }

        @Test
        @DisplayName("returns every activity of the user, none dropped")
        void returnsEveryActivity() {
            when(repository.findByUserId(ALICE)).thenReturn(
                    List.of(storedActivity("a1", ALICE), storedActivity("a2", ALICE), storedActivity("a3", ALICE)));

            assertThat(service.getUserActivities(ALICE))
                    .extracting(ActivityResponse::getId)
                    .containsExactly("a1", "a2", "a3");
        }

        @Test
        @DisplayName("is empty, not an error, when the user has no activities")
        void isEmptyWhenThereAreNoActivities() {
            when(repository.findByUserId(ALICE)).thenReturn(List.of());

            assertThat(service.getUserActivities(ALICE)).isEmpty();
        }

        @Test
        @DisplayName("only the requested user's activities are looked up")
        void looksUpOnlyTheRequestedUser() {
            when(repository.findByUserId(ALICE)).thenReturn(List.of(storedActivity("a1", ALICE)));

            service.getUserActivities(ALICE);

            verify(repository).findByUserId(ALICE);
            verify(repository, never()).findByUserId(BOB);
            verify(repository, never()).findAll();
        }
    }

    @Nested
    @DisplayName("getActivityById")
    class GetActivityById {

        @Test
        @DisplayName("the owner can read their own activity, with all its fields")
        void ownerReadsTheirActivity() {
            when(repository.findById("a1")).thenReturn(Optional.of(storedActivity("a1", ALICE)));

            ActivityResponse activity = service.getActivityById("a1", ALICE);

            assertThat(activity.getId()).isEqualTo("a1");
            assertThat(activity.getUserId()).isEqualTo(ALICE);
            assertThat(activity.getType()).isEqualTo(ActivityType.RUNNING);
            assertThat(activity.getDuration()).isEqualTo(45);
            assertThat(activity.getStartTime()).isEqualTo(START);
            assertThat(activity.getCreatedAt()).isEqualTo(CREATED);
        }

        @Test
        @DisplayName("another user cannot read the activity, even though it exists")
        void anotherUserCannotReadIt() {
            when(repository.findById("a1")).thenReturn(Optional.of(storedActivity("a1", ALICE)));

            assertThatThrownBy(() -> service.getActivityById("a1", BOB))
                    .isInstanceOf(ActivityNotFoundException.class);
        }

        @Test
        @DisplayName("an activity that does not exist is reported as not found")
        void missingActivityIsNotFound() {
            when(repository.findById("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getActivityById("ghost", ALICE))
                    .isInstanceOf(ActivityNotFoundException.class)
                    .hasMessage("Activity not found: ghost");
        }

        @Test
        @DisplayName("someone else's activity looks exactly like a missing one, so its existence is not revealed")
        void otherUsersActivityLooksLikeAMissingOne() {
            lenient().when(repository.findById("a1")).thenReturn(Optional.of(storedActivity("a1", ALICE)));
            lenient().when(repository.findById("ghost")).thenReturn(Optional.empty());

            Throwable forOthersActivity = catchThrowable(() -> service.getActivityById("a1", BOB));
            Throwable forMissingActivity = catchThrowable(() -> service.getActivityById("ghost", BOB));

            assertThat(forOthersActivity).isInstanceOf(ActivityNotFoundException.class);
            assertThat(forMissingActivity).isInstanceOf(ActivityNotFoundException.class);
            assertThat(forOthersActivity).hasMessage("Activity not found: a1");
            assertThat(forMissingActivity).hasMessage("Activity not found: ghost");
        }
    }
}
