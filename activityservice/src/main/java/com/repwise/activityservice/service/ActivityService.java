package com.repwise.activityservice.service;

import com.repwise.activityservice.dto.ActivityRequest;
import com.repwise.activityservice.dto.ActivityResponse;
import com.repwise.activityservice.model.Activity;
import com.repwise.activityservice.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Business logic for recording and retrieving activities.
 *
 * <p>An activity may only be recorded for a user that exists. Existence is checked through
 * {@link UserValidationService}, which asks the user service. Persistence is delegated to
 * {@link ActivityRepository}, and entities are converted to {@link ActivityResponse} objects
 * before they leave this class.
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository repository;
    private final UserValidationService userValidationService;

    /**
     * Validates the user and then saves a new activity.
     *
     * @param request the activity details, including the owning user's identifier
     * @return the saved activity, including its generated identifier and audit timestamps
     * @throws RuntimeException if the user is invalid or the user service cannot be reached
     */
    public ActivityResponse trackActivity(ActivityRequest request) {

        boolean validUser = userValidationService.validateUser(request.getUserId());

        if (!validUser) throw new RuntimeException("Invalid user: " + request.getUserId());

        Activity activity = Activity.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .duration(request.getDuration())
                .caloriesBurned(request.getCaloriesBurned())
                .startTime(request.getStartTime())
                .additionalMetrics(request.getAdditionalMetrics())
                .build();

        Activity savedActivity = repository.save(activity);

        return mapToResponse(savedActivity);
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
     * Retrieves a single activity.
     *
     * @param activityId the identifier of the activity
     * @return the activity
     * @throws RuntimeException if no activity exists with the given identifier
     */
    public ActivityResponse getActivityById(String activityId) {
        return repository.findById(activityId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RuntimeException("activity with id " + activityId + " not found"));
    }
}
