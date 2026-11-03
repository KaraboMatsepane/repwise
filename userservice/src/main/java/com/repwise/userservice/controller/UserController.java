package com.repwise.userservice.controller;

import com.repwise.userservice.dto.UserResponse;
import com.repwise.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * REST controller exposing user operations under {@code /api/users}.
 *
 * <p>Endpoints (all require a valid access token):
 * <ul>
 *   <li>{@code GET /api/users/me} returns the caller's own profile, creating it on first use.</li>
 *   <li>{@code GET /api/users/{userId}} returns the caller's own profile, or any profile if the
 *       caller is an administrator; for other callers any other id is reported as not
 *       found.</li>
 *   <li>{@code GET /api/users/{userId}/validate} reports whether the caller has a profile
 *       ({@code false} for any other id). The activity service calls it, with the caller's token
 *       forwarded, before it records an activity.</li>
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
     * Retrieves a profile by id: the caller's own, or any profile for an administrator.
     *
     * <p>A caller without the {@code ROLE_ADMIN} authority who asks for another user's id gets
     * {@code 404}, the same as for a user that does not exist.
     *
     * @param userId the identifier of the profile being requested
     * @param jwt the verified access token of the caller
     * @param authentication the caller's identity, used to check for the {@code ROLE_ADMIN}
     *        authority
     * @return {@code 200 OK} with the user's profile
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserProfile(
            @PathVariable String userId, @AuthenticationPrincipal Jwt jwt,
            Authentication authentication){
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(authority -> Objects.equals(authority.getAuthority(), "ROLE_ADMIN"));

        return ResponseEntity.ok(userService.getProfile(userId, jwt.getSubject(), isAdmin));
    }


    /**
     * Checks whether the caller has a profile.
     *
     * @param userId the identifier being asked about
     * @param jwt the verified access token of the caller
     * @return {@code 200 OK} with {@code true} if {@code userId} is the caller's own id and has a
     *         profile, otherwise {@code false}
     */
    @GetMapping("/{userId}/validate")
    public ResponseEntity<Boolean> validateUser(
            @PathVariable String userId, @AuthenticationPrincipal Jwt jwt){
        return ResponseEntity.ok(userService.existsById(userId, jwt.getSubject()));
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
