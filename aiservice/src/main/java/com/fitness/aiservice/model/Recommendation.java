package com.fitness.aiservice.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MongoDB document holding the guidance generated for one activity, stored in the
 * {@code recommendations} collection.
 *
 * <p>Besides a free-text summary it carries lists of suggested improvements, further
 * suggestions and safety notes. The creation timestamp is maintained by Mongo auditing.
 */
@Document(collection = "recommendations")
@Data
@Builder
public class Recommendation {
    private String id;
    private String activityId;
    private String userId;
    private String activityType;
    private String recommendations;
    private List<String> improvements;
    private List<String> suggestions;
    private List<String> safety;

    @CreatedDate
    private LocalDateTime createdAt;

}
