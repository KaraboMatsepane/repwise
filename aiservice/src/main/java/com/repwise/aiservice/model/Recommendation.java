package com.repwise.aiservice.model;

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
 * <p>It carries a structured {@link Analysis} of the session plus lists of suggested
 * improvements, further suggestions and safety notes. The creation timestamp is maintained by
 * Mongo auditing.
 */
@Document(collection = "recommendations")
@Data
@Builder
public class Recommendation {
    private String id;
    private String activityId;
    private String userId;
    private String activityType;
    private Analysis analysis;
    private List<String> improvements;
    private List<String> suggestions;
    private List<String> safety;

    @CreatedDate
    private LocalDateTime createdAt;

    /**
     * The coach's assessment of one session, split by topic. A part is empty when the model had
     * no data to comment on, for example no heart rate.
     *
     * @param overall short overall assessment of the session
     * @param pace pace analysis
     * @param heartRate heart rate analysis
     * @param caloriesBurned calories analysis
     */
    public record Analysis(String overall, String pace, String heartRate, String caloriesBurned) {
    }
}
