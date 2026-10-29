package com.repwise.userservice.controller;

import com.repwise.userservice.dto.RegisterRequest;
import com.repwise.userservice.dto.UserResponse;
import com.repwise.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing user operations under {@code /api/users}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/users/{userId}} returns a user's profile.</li>
 *   <li>{@code POST /api/users/register} creates a user and responds with {@code 201 Created}.</li>
 *   <li>{@code GET /api/users/{userId}/validate} reports whether a user exists. The activity
 *       service calls this before it records an activity.</li>
 * </ul>
 *
 * <p>The controller only maps HTTP to service calls and holds no business logic.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Retrieves a user's profile.
     *
     * @param userId the identifier of the user
     * @return {@code 200 OK} with the user's profile
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserProfile(@PathVariable String userId){
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    /**
     * Registers a new user. The request body is validated before the service is invoked.
     *
     * @param request the registration details
     * @return {@code 201 Created} with the newly created user
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    /**
     * Checks whether a user exists.
     *
     * @param userId the identifier of the user
     * @return {@code 200 OK} with {@code true} if the user exists, otherwise {@code false}
     */
    @GetMapping("/{userId}/validate")
    public ResponseEntity<Boolean> validateUser(@PathVariable String userId){
        return ResponseEntity.ok(userService.existsById(userId));
    }
}
