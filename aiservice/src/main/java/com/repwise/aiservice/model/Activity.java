package com.repwise.aiservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * An activity as received from the activity service over RabbitMQ.
 *
 * <p>This is a plain message payload, not a stored entity: the AI service does not persist
 * activities, it only reads them to generate recommendations. The activity type is kept as a
 * string so this service does not need a copy of the activity service's enum, and fields it
 * does not use, such as the audit timestamps, are ignored.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Activity {
    private String id;
    private String userId;
    private String type;
    private Integer duration;
    private Integer caloriesBurned;
    private LocalDateTime startTime;
    private Map<String, Object> additionalMetrics;
}
