package com.fitness.activityservice.dto;

import com.fitness.activityservice.model.ActivityType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Request body for {@code POST /api/activities}.
 *
 * <p>{@code userId} identifies the owner and is verified against the user service.
 * {@code additionalMetrics} holds free-form values specific to the activity type, for example
 * distance for a run.
 */
@Data
public class ActivityRequest {
    private String id;
    private String userId;
    private ActivityType type;
    private Integer duration;
    private Integer caloriesBurned;
    private LocalDateTime startTime;
    private Map<String, Object> additionalMetrics;
}
