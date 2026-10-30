package com.repwise.activityservice.dto;

import com.repwise.activityservice.model.ActivityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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
    @NotBlank(message = "User id is required")
    private String userId;

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
