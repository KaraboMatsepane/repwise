package com.fitness.userservice.service;

import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.model.User;
import com.fitness.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Business logic for user registration and lookup.
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
     * @throws RuntimeException if no user exists with the given identifier
     */
    public UserResponse getProfile(String userId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return createUserResponse(user);
    }

    /**
     * Registers a new user.
     *
     * <p>The request is assumed to have been validated by the web layer.
     *
     * @param request the registration details
     * @return the persisted user, including generated identifier and timestamps
     * @throws RuntimeException if a user with the same email already exists
     */
    public UserResponse register(RegisterRequest request) {

        if (repository.existsByEmail(request.getEmail())){
            throw new RuntimeException("Email already exists.");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());

        User savedUser = repository.save(user);

        return createUserResponse(savedUser);

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
        userResponse.setPassword(user.getPassword());
        userResponse.setFirstName(user.getFirstName());
        userResponse.setLastName(user.getLastName());
        userResponse.setCreatedAt(user.getCreatedAt());
        userResponse.setUpdatedAt(user.getUpdatedAt());

        return userResponse;
    }

    /**
     * Checks whether a user exists.
     *
     * @param userId the identifier of the user
     * @return {@code true} if a user with the given identifier exists
     */
    public boolean existsById(String userId) {
        return repository.existsById(userId);
    }
}
