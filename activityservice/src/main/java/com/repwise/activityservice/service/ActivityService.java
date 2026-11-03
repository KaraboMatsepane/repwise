package com.repwise.activityservice.service;

import com.repwise.activityservice.dto.ActivityRequest;
import com.repwise.activityservice.dto.ActivityResponse;
import com.repwise.activityservice.exception.ActivityNotFoundException;
import com.repwise.activityservice.exception.InvalidUserException;
import com.repwise.activityservice.model.Activity;
import com.repwise.activityservice.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Business logic for recording and retrieving activities.
 *
 * <p>An activity may only be recorded for a user that exists. Existence is checked through
 * {@link UserValidationService}, which asks the user service. Persistence is delegated to
 * {@link ActivityRepository}, and entities are converted to {@link ActivityResponse} objects
 * before they leave this class.
 *
 * <p>After an activity is saved it is also published to RabbitMQ so other services, such as the
 * AI service, can react to it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository repository;
    private final UserValidationService userValidationService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.name}")
    private String exchange;

    @Value("${rabbitmq.routing.key}")
    private String routingKey;

    /**
     * Checks that the user has a profile, saves a new activity and publishes it to RabbitMQ.
     *
     * <p>A publishing failure is logged but does not fail the request, because the activity is
     * already saved. In that case other services will not hear about the activity.
     *
     * @param request the activity details
     * @param jwt the caller's verified access token; its subject owns the activity and the token
     *        itself is forwarded to the user service for the profile check
     * @return the saved activity, including its generated identifier and audit timestamps
     * @throws InvalidUserException if the user does not exist
     * @throws UserServiceUnavailableException if the user service cannot be reached
     */
    public ActivityResponse trackActivity(ActivityRequest request, Jwt jwt) {

        String userId = jwt.getSubject();
        boolean validUser = userValidationService.validateUser(userId, jwt.getTokenValue());

        if (!validUser) throw new InvalidUserException(userId);

        Activity activity = Activity.builder()
                .userId(userId)
                .type(request.getType())
                .duration(request.getDuration())
                .caloriesBurned(request.getCaloriesBurned())
                .startTime(request.getStartTime())
                .additionalMetrics(request.getAdditionalMetrics())
                .build();

        Activity savedActivity = repository.save(activity);

        ActivityResponse response = mapToResponse(savedActivity);

        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, response);
        } catch (Exception e) {
            log.error("Failed to publish activity {} to RabbitMQ", response.getId(), e);
        }

        return response;
    }

    /**
     * Converts an {@link Activity} entity into its response representation.
     *
     * @param activity the entity to convert
     * @return the corresponding response object
     */
    private ActivityResponse mapToResponse(Activity activity){
        ActivityResponse activityResponse = new ActivityResponse();

        activityResponse.setId(activity.getId());
        activityResponse.setUserId(activity.getUserId());
        activityResponse.setType(activity.getType());
        activityResponse.setDuration(activity.getDuration());
        activityResponse.setCaloriesBurned(activity.getCaloriesBurned());
        activityResponse.setStartTime(activity.getStartTime());
        activityResponse.setAdditionalMetrics(activity.getAdditionalMetrics());
        activityResponse.setCreatedAt(activity.getCreatedAt());
        activityResponse.setUpdatedAt(activity.getUpdatedAt());

        return activityResponse;
    }

    /**
     * Retrieves every activity recorded for a user.
     *
     * @param userId the identifier of the user
     * @return the user's activities, empty if there are none
     */
    public List<ActivityResponse> getUserActivities(String userId) {
        List<Activity> activities = repository.findByUserId(userId);

        return activities.stream().map(this::mapToResponse).toList();
    }

    /**
     * Retrieves a single activity, only if it belongs to the given user.
     *
     * @param activityId the identifier of the activity
     * @param userId the caller's identifier, taken from the verified token
     * @return the activity
     * @throws ActivityNotFoundException if no activity exists with the given identifier or it
     *         belongs to a different user
     */
    public ActivityResponse getActivityById(String activityId, String userId) {

        return repository.findById(activityId)
                .map(this::mapToResponse)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> new ActivityNotFoundException(activityId));

    }
}
