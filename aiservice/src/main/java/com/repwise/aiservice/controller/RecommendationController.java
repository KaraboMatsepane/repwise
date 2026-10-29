package com.repwise.aiservice.controller;

import com.repwise.aiservice.model.Recommendation;
import com.repwise.aiservice.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing stored recommendations under {@code /api/recommendations}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/recommendations/user/{userId}} lists a user's recommendations.</li>
 *   <li>{@code GET /api/recommendations/activity/{activityId}} returns the recommendation
 *       for one activity.</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * Lists every recommendation generated for a user.
     *
     * @param userId the identifier of the user
     * @return {@code 200 OK} with the user's recommendations, empty if there are none
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Recommendation>> getUserRecommendations(@PathVariable String userId){
        return ResponseEntity.ok(recommendationService.getUserRecommendations(userId));
    }

    /**
     * Retrieves the recommendation generated for a single activity.
     *
     * @param activityId the identifier of the activity
     * @return {@code 200 OK} with the recommendation
     */
    @GetMapping("/activity/{activityId}")
    public ResponseEntity<Recommendation> getActivityRecommendation(@PathVariable String activityId){
        return ResponseEntity.ok(recommendationService.getActivityRecommendation(activityId));
    }
}
