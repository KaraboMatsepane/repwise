package com.repwise.userservice.service;

import com.repwise.userservice.dto.UserResponse;
import com.repwise.userservice.exception.UserNotFoundException;
import com.repwise.userservice.model.User;
import com.repwise.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Business logic for user profiles: lookup, existence checks and creating a profile from a
 * verified Keycloak token.
 *
 * <p>Persistence is delegated to {@link UserRepository}, and entities are converted to
 * {@link UserResponse} objects before they leave this class so the web layer never handles
 * {@link User} entities directly.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;

    /**
     * Looks up a user's profile.
     *
     * @param userId the identifier of the user
     * @return the user's profile
     * @throws UserNotFoundException if no user exists with the given identifier
     */
    public UserResponse getProfile(String userId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        return createUserResponse(user);
    }

    /**
     * Converts a {@link User} entity into its response representation.
     *
     * @param user the entity to convert
     * @return the corresponding response object
     */
    private UserResponse createUserResponse(User user){
        UserResponse userResponse = new UserResponse();

        userResponse.setId(user.getId());
        userResponse.setEmail(user.getEmail());
        userResponse.setFirstName(user.getFirstName());
        userResponse.setLastName(user.getLastName());
        userResponse.setCreatedAt(user.getCreatedAt());
        userResponse.setUpdatedAt(user.getUpdatedAt());

        return userResponse;
    }

    /**
     * Checks whether a user has a profile.
     *
     * @param userId the identifier of the user (the Keycloak {@code sub})
     * @return {@code true} if a profile with the given identifier exists
     */
    public boolean existsById(String userId) {
        return repository.existsById(userId);
    }

    /**
     * Returns the caller's profile, creating it if this is their first request.
     *
     * <p>The profile id is the token's {@code sub} claim, so a user can only ever reach their own
     * row. Two simultaneous first requests for the same user could both try to insert; the loser
     * fails on the primary key.
     *
     * @param jwt the verified access token of the caller
     * @return the caller's profile
     */
    public UserResponse getOrCreateFromToken(Jwt jwt) {
        User user = repository.findById(jwt.getSubject())
                .orElseGet(() -> createProfileFrom(jwt));

        return createUserResponse(user);

    }

    /**
     * Builds and saves a new profile from the claims of a verified token.
     *
     * <p>The token must contain an {@code email} claim, because the column is not nullable.
     *
     * @param jwt the verified access token
     * @return the saved profile
     */
    private User createProfileFrom(Jwt jwt) {
        User user = new User();

        user.setId(jwt.getSubject());
        user.setEmail(jwt.getClaimAsString("email"));
        user.setFirstName(jwt.getClaimAsString("given_name"));
        user.setLastName(jwt.getClaimAsString("family_name"));

        return repository.save(user);
    }
}
