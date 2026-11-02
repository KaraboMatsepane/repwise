package com.repwise.aiservice.service;

import com.repwise.aiservice.exception.RecommendationNotFoundException;
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
     * @param userId the identifier of the user (the Keycloak {@code sub})
     * @return the user's recommendations, empty if there are none
     */
    public List<Recommendation> getUserRecommendations(String userId) {
        return repository.findByUserId(userId);
    }

    /**
     * Retrieves the recommendation generated for a single activity, only if it belongs to the
     * given user.
     *
     * @param activityId the identifier of the activity
     * @param userId the caller's identifier, taken from the verified token
     * @return the recommendation
     * @throws RecommendationNotFoundException if no recommendation exists for the activity or it
     *         belongs to a different user
     */
    public Recommendation getActivityRecommendation(String activityId, String userId) {
        return repository.findByActivityId(activityId)
                .filter(recommendation -> recommendation.getUserId().equals(userId))
                .orElseThrow(() -> new RecommendationNotFoundException(activityId));
    }
}
