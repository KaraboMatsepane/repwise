package com.fitness.activityservice.controller;

import com.fitness.activityservice.dto.ActivityRequest;
import com.fitness.activityservice.dto.ActivityResponse;
import com.fitness.activityservice.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing activity tracking under {@code /api/activities}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/activities} records a new activity.</li>
 *   <li>{@code GET /api/activities} lists the activities of the user named in the
 *       {@code X-User-ID} header.</li>
 *   <li>{@code GET /api/activities/{activityId}} returns a single activity.</li>
 * </ul>
 *
 * <p>The controller only maps HTTP to service calls and holds no business logic.
 */
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /**
     * Records a new activity after the owning user has been verified.
     *
     * @param request the activity details
     * @return {@code 200 OK} with the saved activity
     */
    @PostMapping
    public ResponseEntity<ActivityResponse> trackActivity(@RequestBody ActivityRequest request){
        return ResponseEntity.ok(activityService.trackActivity(request));
    }

    /**
     * Lists every activity belonging to a user.
     *
     * @param userId the user's identifier, taken from the {@code X-User-ID} header
     * @return {@code 200 OK} with the user's activities, empty if there are none
     */
    @GetMapping
    public ResponseEntity<List<ActivityResponse>> getUserActivities(@RequestHeader("X-User-ID") String userId){
        return ResponseEntity.ok(activityService.getUserActivities(userId));
    }

    /**
     * Retrieves a single activity.
     *
     * @param activityId the identifier of the activity
     * @return {@code 200 OK} with the activity
     */
    @GetMapping("/{activityId}")
    public ResponseEntity<ActivityResponse> getActivityById(@PathVariable String activityId){
        return ResponseEntity.ok(activityService.getActivityById(activityId));
    }
}
