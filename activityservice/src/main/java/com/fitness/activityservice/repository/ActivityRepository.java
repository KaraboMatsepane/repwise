package com.fitness.activityservice.repository;

import com.fitness.activityservice.model.Activity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Spring Data MongoDB repository for {@link Activity} documents in the {@code activities}
 * collection.
 *
 * <p>Standard CRUD operations are inherited from {@link MongoRepository}, keyed by the
 * activity's string identifier.
 */
public interface ActivityRepository extends MongoRepository<Activity, String> {
    /**
     * Finds every activity that belongs to a user.
     *
     * @param userId the identifier of the owning user
     * @return the user's activities, empty if there are none
     */
    List<Activity> findByUserId(String userId);
}
