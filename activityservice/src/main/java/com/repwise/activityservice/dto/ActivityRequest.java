package com.repwise.activityservice.dto;

import com.repwise.activityservice.model.ActivityType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Request body for {@code POST /api/activities}.
 *
 * <p>The request carries no user id: the owner is taken from the caller's access token.
 * {@code additionalMetrics} holds free-form values specific to the activity type, for example
 * distance for a run.
 */
@Data
public class ActivityRequest {

    @NotNull(message = "Activity type is required")
    private ActivityType type;

    @NotNull(message = "Duration is required")
    @Positive(message = "Duration must be greater than 0 minutes")
    private Integer duration;

    @NotNull(message = "Calories burned is required")
    @PositiveOrZero(message = "Calories burned cannot be negative")
    private Integer caloriesBurned;

    @NotNull(message = "Start time is required")
    private LocalDateTime startTime;

    private Map<String, Object> additionalMetrics;
}
