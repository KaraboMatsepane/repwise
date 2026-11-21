package com.repwise.userservice.service;

import com.repwise.userservice.dto.UserResponse;
import com.repwise.userservice.exception.EmailAlreadyInUseException;
import com.repwise.userservice.exception.MissingEmailException;
import com.repwise.userservice.exception.UserNotFoundException;
import com.repwise.userservice.model.User;
import com.repwise.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
     * <p>A user may only read their own profile, but an administrator may read any. For a caller
     * who is neither, asking for someone else's profile is reported exactly like a missing user,
     * so the existence of other accounts is not revealed.
     *
     * @param userId the identifier of the profile being requested
     * @param callerId the identifier of the caller, taken from their verified token
     * @param callerIsAdmin whether the caller holds the {@code ROLE_ADMIN} authority
     * @return the user's profile
     * @throws UserNotFoundException if no user exists with the given identifier, or it is not the
     *         caller's own and the caller is not an administrator
     */
    public UserResponse getProfile(String userId, String callerId, boolean callerIsAdmin) {
        if (!callerIsAdmin && !userId.equals(callerId)){
            throw new UserNotFoundException(userId);
        }

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
     * Checks whether the caller has a profile.
     *
     * <p>Only the caller's own id can be confirmed. For any other id the answer is {@code false},
     * so this cannot be used to find out which accounts exist.
     *
     * @param userId the identifier being asked about (the Keycloak {@code sub})
     * @param callerId the identifier of the caller, taken from their verified token
     * @return {@code true} if {@code userId} is the caller's own id and a profile exists for it
     */
    public boolean existsById(String userId, String callerId) {
        return userId.equals(callerId) && repository.existsById(userId);
    }

    /**
     * Returns the caller's profile, creating it if this is their first request.
     *
     * <p>The profile id is the token's {@code sub} claim, so a user can only ever reach their own
     * row. Two simultaneous first requests for the same user, as a browser app easily sends, both
     * get the same profile: only one is created, and the other request returns it instead of
     * failing.
     *
     * <p>An email address is required to create a profile, but not to return an existing one.
     *
     * @param jwt the verified access token of the caller
     * @return the caller's profile
     * @throws MissingEmailException if the profile has to be created and the token has no email
     * @throws EmailAlreadyInUseException if the profile has to be created and another profile
     *         already has the token's email
     */
    public UserResponse getOrCreateFromToken(Jwt jwt) {
        User user = repository.findById(jwt.getSubject())
                .orElseGet(() -> createProfileFrom(jwt));

        return createUserResponse(user);

    }

    /**
     * Builds and saves a new profile from the claims of a verified token.
     *
     * <p>The token must contain a non-blank {@code email} claim, because the column is not
     * nullable. This is checked up front so the caller gets a clear error and nothing is saved.
     *
     * <p>Another request for the same user can save the profile between the caller's lookup and
     * this save. The database then refuses this row (the id is the primary key), and the profile
     * the other request saved is returned instead. If no profile exists for this id after all, the
     * row was refused for another reason, in practice because the email is unique and another
     * profile already has it.
     *
     * <p>This relies on {@code save} committing on its own, so the refusal happens inside this
     * method and the follow-up read starts a fresh transaction. Do not make this class or its
     * callers {@code @Transactional}.
     *
     * @param jwt the verified access token
     * @return the saved profile, or the one a simultaneous request saved for the same user
     * @throws MissingEmailException if the token has no email, or a blank one
     * @throws EmailAlreadyInUseException if another profile already has the token's email
     */
    private User createProfileFrom(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new MissingEmailException();
        }

        User user = new User();

        user.setId(jwt.getSubject());
        user.setEmail(email);
        user.setFirstName(jwt.getClaimAsString("given_name"));
        user.setLastName(jwt.getClaimAsString("family_name"));

        try {
            return repository.save(user);
        } catch (DataIntegrityViolationException e) {
            // A parallel first request saved this user's profile between our check and this save:
            // return that profile. If there is none, the row was refused for its email instead.
            return repository.findById(user.getId())
                    .orElseThrow(() -> new EmailAlreadyInUseException(e));
        }
    }
}
