package com.repwise.userservice.service;

import com.repwise.userservice.dto.RegisterRequest;
import com.repwise.userservice.dto.UserResponse;
import com.repwise.userservice.exception.EmailAlreadyExistsException;
import com.repwise.userservice.exception.UserNotFoundException;
import com.repwise.userservice.model.User;
import com.repwise.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

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
     * Registers a new user.
     *
     * <p>The request is assumed to have been validated by the web layer.
     *
     * @param request the registration details
     * @return the persisted user, including generated identifier and timestamps
     * @throws EmailAlreadyExistsException if a user with the same email already exists
     */
    public UserResponse register(RegisterRequest request) {

        if (repository.existsByEmail(request.getEmail())){
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
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
