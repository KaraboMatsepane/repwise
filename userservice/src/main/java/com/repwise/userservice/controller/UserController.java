package com.repwise.userservice.controller;

import com.repwise.userservice.dto.UserResponse;
import com.repwise.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing user operations under {@code /api/users}.
 *
 * <p>Endpoints (all require a valid access token except {@code validate}):
 * <ul>
 *   <li>{@code GET /api/users/me} returns the caller's own profile, creating it on first use.</li>
 *   <li>{@code GET /api/users/{userId}} returns a user's profile.</li>
 *   <li>{@code GET /api/users/{userId}/validate} reports whether a user has a profile. It is
 *       open (no token) because the activity service calls it before it records an activity.</li>
 * </ul>
 *
 * <p>There is no registration endpoint: users sign up and log in through Keycloak.
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
     * Checks whether a user exists.
     *
     * @param userId the identifier of the user
     * @return {@code 200 OK} with {@code true} if the user exists, otherwise {@code false}
     */
    @GetMapping("/{userId}/validate")
    public ResponseEntity<Boolean> validateUser(@PathVariable String userId){
        return ResponseEntity.ok(userService.existsById(userId));
    }

    /**
     * Returns the profile of the caller, identified by the verified access token.
     *
     * <p>The first call for a Keycloak user creates the profile from the token's {@code sub},
     * {@code email}, {@code given_name} and {@code family_name} claims. Later calls return the
     * stored profile.
     *
     * @param jwt the verified access token of the caller
     * @return {@code 200 OK} with the caller's profile
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.getOrCreateFromToken(jwt));
    }
}
