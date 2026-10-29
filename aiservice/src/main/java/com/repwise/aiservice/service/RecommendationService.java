package com.repwise.aiservice.service;

import com.repwise.aiservice.model.Recommendation;
import com.repwise.aiservice.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Business logic for reading stored recommendations.
 *
 * <p>Persistence is delegated to {@link RecommendationRepository}. This class currently only
 * retrieves recommendations and does not generate them.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationRepository repository;

    /**
     * Retrieves every recommendation generated for a user.
     *
     * @param userId the identifier of the user
     * @return the user's recommendations, empty if there are none
     */
    public List<Recommendation> getUserRecommendations(String userId) {
        return repository.findByUserId(userId);
    }

    /**
     * Retrieves the recommendation generated for a single activity.
     *
     * @param activityId the identifier of the activity
     * @return the recommendation
     * @throws RuntimeException if no recommendation exists for the activity
     */
    public Recommendation getActivityRecommendation(String activityId) {
        return repository.findByActivityId(activityId).orElseThrow(
                () -> new RuntimeException(
                        "No recommendation found for this activity " + activityId)
        );
    }
}
