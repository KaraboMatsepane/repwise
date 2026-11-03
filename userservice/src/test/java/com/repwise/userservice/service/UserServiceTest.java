package com.repwise.userservice.service;

import com.repwise.userservice.dto.UserResponse;
import com.repwise.userservice.exception.MissingEmailException;
import com.repwise.userservice.exception.UserNotFoundException;
import com.repwise.userservice.model.User;
import com.repwise.userservice.model.UserRole;
import com.repwise.userservice.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Specifies how {@link UserService} must behave, as documented on the class: who may read which
 * profile, what {@code existsById} may reveal, and how a profile is created from a token.
 *
 * <p>The database is replaced by a Mockito mock of {@link UserRepository}, so these are plain unit
 * tests with no Spring context, no PostgreSQL and no network.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String ALICE = "alice-id";
    private static final String BOB = "bob-id";
    private static final String ADMIN = "admin-id";

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 1, 15, 9, 30);
    private static final LocalDateTime UPDATED = LocalDateTime.of(2026, 2, 20, 18, 45);

    @Mock
    private UserRepository repository;

    private UserService service() {
        return new UserService(repository);
    }

    /** A stored profile, as the database would return it. */
    private User storedUser(String id) {
        User user = new User();
        user.setId(id);
        user.setEmail(id + "@example.com");
        user.setFirstName("First-" + id);
        user.setLastName("Last-" + id);
        user.setCreatedAt(CREATED);
        user.setUpdatedAt(UPDATED);
        return user;
    }

    /** A token as Keycloak would issue it for the given user. */
    private Jwt tokenFor(String sub, String email, String givenName, String familyName) {
        Jwt.Builder builder = Jwt.withTokenValue("fake-token").header("alg", "none").subject(sub);
        if (email != null) builder.claim("email", email);
        if (givenName != null) builder.claim("given_name", givenName);
        if (familyName != null) builder.claim("family_name", familyName);
        return builder.build();
    }

    @Nested
    @DisplayName("getProfile")
    class GetProfile {

        @Test
        @DisplayName("a user can read their own profile, with all its fields")
        void userReadsOwnProfile() {
            when(repository.findById(ALICE)).thenReturn(Optional.of(storedUser(ALICE)));

            UserResponse profile = service().getProfile(ALICE, ALICE, false);

            assertThat(profile.getId()).isEqualTo(ALICE);
            assertThat(profile.getEmail()).isEqualTo("alice-id@example.com");
            assertThat(profile.getFirstName()).isEqualTo("First-alice-id");
            assertThat(profile.getLastName()).isEqualTo("Last-alice-id");
            assertThat(profile.getCreatedAt()).isEqualTo(CREATED);
            assertThat(profile.getUpdatedAt()).isEqualTo(UPDATED);
        }

        @Test
        @DisplayName("a user cannot read someone else's profile, even though it exists")
        void userCannotReadAnotherUsersProfile() {
            // Bob's profile really is in the database; a correct service must still not return it.
            lenient().when(repository.findById(BOB)).thenReturn(Optional.of(storedUser(BOB)));

            assertThatThrownBy(() -> service().getProfile(BOB, ALICE, false))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("asking for someone else's profile looks exactly like asking for a missing user")
        void otherUsersProfileIsReportedLikeAMissingUser() {
            when(repository.findById("ghost-id")).thenReturn(Optional.empty());

            Throwable forExistingOtherUser = org.assertj.core.api.Assertions.catchThrowable(
                    () -> service().getProfile(BOB, ALICE, false));
            Throwable forMissingUser = org.assertj.core.api.Assertions.catchThrowable(
                    () -> service().getProfile("ghost-id", "ghost-id", false));

            assertThat(forExistingOtherUser).isInstanceOf(UserNotFoundException.class);
            assertThat(forMissingUser).isInstanceOf(UserNotFoundException.class);
            assertThat(forExistingOtherUser).hasMessage("User not found: " + BOB);
            assertThat(forMissingUser).hasMessage("User not found: ghost-id");
        }

        @Test
        @DisplayName("a refused request does not even touch the database")
        void refusedRequestDoesNotQueryTheDatabase() {
            assertThatThrownBy(() -> service().getProfile(BOB, ALICE, false))
                    .isInstanceOf(UserNotFoundException.class);

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("a user whose own profile does not exist yet gets 'not found'")
        void userWithoutAProfileGetsNotFound() {
            when(repository.findById(ALICE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service().getProfile(ALICE, ALICE, false))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("User not found: " + ALICE);
        }

        @Test
        @DisplayName("an administrator can read any user's profile")
        void adminReadsAnyProfile() {
            when(repository.findById(BOB)).thenReturn(Optional.of(storedUser(BOB)));

            UserResponse profile = service().getProfile(BOB, ADMIN, true);

            assertThat(profile.getId()).isEqualTo(BOB);
            assertThat(profile.getEmail()).isEqualTo("bob-id@example.com");
        }

        @Test
        @DisplayName("an administrator can also read their own profile")
        void adminReadsOwnProfile() {
            when(repository.findById(ADMIN)).thenReturn(Optional.of(storedUser(ADMIN)));

            assertThat(service().getProfile(ADMIN, ADMIN, true).getId()).isEqualTo(ADMIN);
        }

        @Test
        @DisplayName("an administrator asking for a user that does not exist gets 'not found'")
        void adminAskingForMissingUserGetsNotFound() {
            when(repository.findById("ghost-id")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service().getProfile("ghost-id", ADMIN, true))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("User not found: ghost-id");
        }
    }

    @Nested
    @DisplayName("existsById")
    class ExistsById {

        @Test
        @DisplayName("is true for the caller's own id when a profile exists")
        void trueForOwnExistingProfile() {
            when(repository.existsById(ALICE)).thenReturn(true);

            assertThat(service().existsById(ALICE, ALICE)).isTrue();
        }

        @Test
        @DisplayName("is false for the caller's own id when no profile exists yet")
        void falseForOwnMissingProfile() {
            when(repository.existsById(ALICE)).thenReturn(false);

            assertThat(service().existsById(ALICE, ALICE)).isFalse();
        }

        @Test
        @DisplayName("is false for someone else's id, so it cannot be used to discover accounts")
        void falseForAnotherUsersId() {
            // Bob's profile really exists; a correct service must still answer false for Alice.
            lenient().when(repository.existsById(BOB)).thenReturn(true);

            assertThat(service().existsById(BOB, ALICE)).isFalse();
        }

        @Test
        @DisplayName("does not ask the database about ids that are not the caller's own")
        void doesNotQueryTheDatabaseForAnotherUsersId() {
            service().existsById(BOB, ALICE);

            verifyNoInteractions(repository);
        }
    }

    @Nested
    @DisplayName("getOrCreateFromToken")
    class GetOrCreateFromToken {

        @Test
        @DisplayName("returns the stored profile for a returning user")
        void returnsExistingProfile() {
            when(repository.findById(ALICE)).thenReturn(Optional.of(storedUser(ALICE)));

            UserResponse profile = service().getOrCreateFromToken(
                    tokenFor(ALICE, "alice-id@example.com", "First-alice-id", "Last-alice-id"));

            assertThat(profile.getId()).isEqualTo(ALICE);
            assertThat(profile.getCreatedAt()).isEqualTo(CREATED);
        }

        @Test
        @DisplayName("does not create or overwrite anything for a returning user")
        void doesNotSaveForAReturningUser() {
            when(repository.findById(ALICE)).thenReturn(Optional.of(storedUser(ALICE)));

            service().getOrCreateFromToken(
                    tokenFor(ALICE, "changed@example.com", "Changed", "Name"));

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("looks the profile up by the token's subject")
        void looksUpByTheTokenSubject() {
            when(repository.findById(ALICE)).thenReturn(Optional.of(storedUser(ALICE)));

            service().getOrCreateFromToken(tokenFor(ALICE, "a@example.com", "A", "B"));

            verify(repository).findById(ALICE);
        }

        @Test
        @DisplayName("creates a profile from the token's claims on a user's first request")
        void createsProfileFromTokenClaims() {
            when(repository.findById(ALICE)).thenReturn(Optional.empty());
            when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            service().getOrCreateFromToken(tokenFor(ALICE, "alice@example.com", "Alice", "Smith"));

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(repository).save(saved.capture());
            assertThat(saved.getValue().getId()).isEqualTo(ALICE);
            assertThat(saved.getValue().getEmail()).isEqualTo("alice@example.com");
            assertThat(saved.getValue().getFirstName()).isEqualTo("Alice");
            assertThat(saved.getValue().getLastName()).isEqualTo("Smith");
        }

        @Test
        @DisplayName("a newly created profile has the USER role")
        void newProfileHasTheUserRole() {
            when(repository.findById(ALICE)).thenReturn(Optional.empty());
            when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            service().getOrCreateFromToken(tokenFor(ALICE, "alice@example.com", "Alice", "Smith"));

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(repository).save(saved.capture());
            assertThat(saved.getValue().getRole()).isEqualTo(UserRole.USER);
        }

        @Test
        @DisplayName("returns the saved profile, including the timestamps the database generated")
        void returnsTheSavedProfile() {
            when(repository.findById(ALICE)).thenReturn(Optional.empty());
            when(repository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setCreatedAt(CREATED);
                user.setUpdatedAt(CREATED);
                return user;
            });

            UserResponse profile = service().getOrCreateFromToken(
                    tokenFor(ALICE, "alice@example.com", "Alice", "Smith"));

            assertThat(profile.getId()).isEqualTo(ALICE);
            assertThat(profile.getEmail()).isEqualTo("alice@example.com");
            assertThat(profile.getFirstName()).isEqualTo("Alice");
            assertThat(profile.getLastName()).isEqualTo("Smith");
            assertThat(profile.getCreatedAt()).isEqualTo(CREATED);
        }

        @Test
        @DisplayName("a first-time user whose token has no email is refused with a clear error")
        void refusesToCreateAProfileWithoutAnEmail() {
            when(repository.findById(ALICE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service().getOrCreateFromToken(tokenFor(ALICE, null, "Alice", "Smith")))
                    .isInstanceOf(MissingEmailException.class);
        }

        @Test
        @DisplayName("a first-time user whose token has a blank email is refused too")
        void refusesToCreateAProfileWithABlankEmail() {
            when(repository.findById(ALICE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service().getOrCreateFromToken(tokenFor(ALICE, "   ", "Alice", "Smith")))
                    .isInstanceOf(MissingEmailException.class);
        }

        @Test
        @DisplayName("nothing is saved when the email is missing")
        void savesNothingWhenTheEmailIsMissing() {
            when(repository.findById(ALICE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service().getOrCreateFromToken(tokenFor(ALICE, null, "Alice", "Smith")))
                    .isInstanceOf(MissingEmailException.class);

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("a returning user is not affected by a token without an email")
        void returningUserWithoutAnEmailInTheTokenStillWorks() {
            when(repository.findById(ALICE)).thenReturn(Optional.of(storedUser(ALICE)));

            UserResponse profile = service().getOrCreateFromToken(tokenFor(ALICE, null, null, null));

            assertThat(profile.getId()).isEqualTo(ALICE);
            assertThat(profile.getEmail()).isEqualTo("alice-id@example.com");
        }

        @Test
        @DisplayName("names are optional: a token with only an email still creates a profile")
        void createsProfileWhenNamesAreMissing() {
            when(repository.findById(ALICE)).thenReturn(Optional.empty());
            when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UserResponse profile = service().getOrCreateFromToken(
                    tokenFor(ALICE, "alice@example.com", null, null));

            assertThat(profile.getEmail()).isEqualTo("alice@example.com");
            assertThat(profile.getFirstName()).isNull();
            assertThat(profile.getLastName()).isNull();
        }
    }
}
