package com.fitness.aiservice.repository;

import com.fitness.aiservice.model.Recommendation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for {@link Recommendation} documents in the
 * {@code recommendations} collection.
 *
 * <p>Standard CRUD operations are inherited from {@link MongoRepository}, keyed by the
 * recommendation's string identifier.
 */
public interface RecommendationRepository extends MongoRepository<Recommendation, String> {

    /**
     * Finds every recommendation generated for a user.
     *
     * @param userId the identifier of the user
     * @return the user's recommendations, empty if there are none
     */
    List<Recommendation> findByUserId(String userId);

    /**
     * Finds the recommendation generated for an activity.
     *
     * @param activityId the identifier of the activity
     * @return the recommendation, or empty if none exists
     */
    Optional<Recommendation> findByActivityId(String activityId);
}
