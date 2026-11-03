package com.repwise.activityservice.controller;

import com.repwise.activityservice.dto.ActivityRequest;
import com.repwise.activityservice.dto.ActivityResponse;
import com.repwise.activityservice.service.ActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing activity tracking under {@code /api/activities}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/activities} records a new activity for the caller.</li>
 *   <li>{@code GET /api/activities} lists the caller's activities.</li>
 *   <li>{@code GET /api/activities/{activityId}} returns one of the caller's activities.</li>
 * </ul>
 *
 * <p>Every endpoint requires a valid access token. The caller is the token's {@code sub} claim;
 * no user id is accepted from the request body, path or headers.
 *
 * <p>The controller only maps HTTP to service calls and holds no business logic.
 */
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /**
     * Records a new activity for the caller after checking that they have a profile.
     *
     * @param request the activity details
     * @param jwt the verified access token; its subject becomes the owner of the activity
     * @return {@code 200 OK} with the saved activity
     */
    @PostMapping
    public ResponseEntity<ActivityResponse> trackActivity(@Valid @RequestBody ActivityRequest request, @AuthenticationPrincipal Jwt jwt){
        return ResponseEntity.ok(activityService.trackActivity(request, jwt));
    }

    /**
     * Lists every activity belonging to the caller.
     *
     * @param jwt the verified access token; its subject selects whose activities are returned
     * @return {@code 200 OK} with the user's activities, empty if there are none
     */
    @GetMapping
    public ResponseEntity<List<ActivityResponse>> getUserActivities(@AuthenticationPrincipal Jwt jwt){
        return ResponseEntity.ok(activityService.getUserActivities(jwt.getSubject()));
    }

    /**
     * Retrieves a single activity owned by the caller.
     *
     * <p>An activity that exists but belongs to someone else is reported as not found, so its
     * existence is not revealed.
     *
     * @param activityId the identifier of the activity
     * @param jwt the verified access token of the caller
     * @return {@code 200 OK} with the activity
     */
    @GetMapping("/{activityId}")
    public ResponseEntity<ActivityResponse> getActivityById(@PathVariable String activityId, @AuthenticationPrincipal Jwt jwt){
        return ResponseEntity.ok(activityService.getActivityById(activityId, jwt.getSubject()));
    }
}
