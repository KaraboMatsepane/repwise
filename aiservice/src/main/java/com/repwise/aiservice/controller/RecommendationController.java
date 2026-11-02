package com.repwise.aiservice.controller;

import com.repwise.aiservice.model.Recommendation;
import com.repwise.aiservice.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
 *   <li>{@code GET /api/recommendations} lists the caller's recommendations.</li>
 *   <li>{@code GET /api/recommendations/activity/{activityId}} returns the recommendation
 *       for one of the caller's activities.</li>
 * </ul>
 *
 * <p>Every endpoint requires a valid access token. The caller is the token's {@code sub} claim;
 * no user id is accepted from the path, body or headers.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * Lists every recommendation generated for the caller.
     *
     * @param jwt the verified access token; its subject selects whose recommendations are returned
     * @return {@code 200 OK} with the caller's recommendations, empty if there are none
     */
    @GetMapping
    public ResponseEntity<List<Recommendation>> getMyRecommendations(@AuthenticationPrincipal Jwt jwt){
        return ResponseEntity.ok(recommendationService.getUserRecommendations(jwt.getSubject()));
    }

    /**
     * Retrieves the recommendation generated for one of the caller's activities.
     *
     * <p>A recommendation that exists but belongs to someone else is reported as not found, so its
     * existence is not revealed.
     *
     * @param activityId the identifier of the activity
     * @param jwt the verified access token of the caller
     * @return {@code 200 OK} with the recommendation
     */
    @GetMapping("/activity/{activityId}")
    public ResponseEntity<Recommendation> getActivityRecommendation(
            @PathVariable String activityId, @AuthenticationPrincipal Jwt jwt){
        return ResponseEntity.ok(
                recommendationService.getActivityRecommendation(activityId, jwt.getSubject()));
    }
}
