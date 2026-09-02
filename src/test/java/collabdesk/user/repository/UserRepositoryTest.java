package collabdesk.user.repository;

import collabdesk.TestcontainersConfiguration;
import collabdesk.user.entity.User;
import collabdesk.user.entity.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
public class UserRepositoryTest {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;
    @Test
    void findsUserByEmail() {
        User savedUser = userRepository.saveAndFlush(
                new User("email@test.com", "User")
        );

        Long savedId = savedUser.getId();

        entityManager.clear();

        User foundUser = userRepository
                .findByEmail("email@test.com")
                .orElseThrow();

        assertAll(
                () -> assertEquals(savedId, foundUser.getId()),
                () -> assertEquals("email@test.com", foundUser.getEmail()),
                () -> assertEquals("User", foundUser.getDisplayName()),
                () -> assertEquals("User", foundUser.getFirstName()),
                () -> assertNull(foundUser.getLastName()),
                () -> assertNull(foundUser.getBirthDate()),
                () -> assertNull(foundUser.getAvatarKey()),
                () -> assertNotNull(foundUser.getEmailVerifiedAt()),
                () -> assertNotNull(foundUser.getOnboardingCompletedAt()),
                () -> assertEquals(UserStatus.ACTIVE, foundUser.getStatus()),
                () -> assertNotNull(foundUser.getCreatedAt()),
                () -> assertNotNull(foundUser.getUpdatedAt()),
                () -> assertNotNull(foundUser.getVersion())
        );
    }

    @Test
    void returnsEmptyWhenEmailDoesNotExist() {
        assertTrue(
                userRepository.findByEmail("missing@test.com").isEmpty()
        );
    }

    @Test
    void returnsTrueWhenEmailExists() {
        userRepository.saveAndFlush(
                new User("email@test.com", "User")
        );

        entityManager.clear();

        assertTrue(
                userRepository.existsByEmail("email@test.com")
        );
    }

    @Test
    void returnsFalseWhenEmailDoesNotExist() {
        assertFalse(
                userRepository.existsByEmail("missing@test.com")
        );
    }

    @Test
    void rejectsDuplicateEmail() {
        userRepository.saveAndFlush(
                new User("duplicate@test.com", "First")
        );

        User duplicate = new User(
                "duplicate@test.com",
                "Second"
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(duplicate)
        );
    }

    @Test
    void persistsDisabledStatus() {
        User savedUser = userRepository.saveAndFlush(
                new User("email@test.com", "User")
        );

        Long userId = savedUser.getId();

        savedUser.disable();
        userRepository.flush();

        entityManager.clear();

        User loadedUser = userRepository.findById(userId)
                .orElseThrow();

        assertEquals(UserStatus.DISABLED, loadedUser.getStatus());
    }

    @Test
    void incrementsVersionWhenUserIsUpdated() {
        User savedUser = userRepository.saveAndFlush(
                new User("email@test.com", "User")
        );

        Long initialVersion = savedUser.getVersion();

        savedUser.disable();
        userRepository.flush();

        assertEquals(
                initialVersion + 1,
                savedUser.getVersion()
        );
    }

    @Test
    void persistsCompletedProfileAndPendingExternalState() {
        User pendingUser = User.pendingExternal(
                "oauth@test.com",
                "temporary-name"
        );
        pendingUser.completeOnboarding(
                "Alex",
                "Morgan",
                LocalDate.of(2000, 2, 3)
        );
        pendingUser.changeAvatar("avatars/user-1.webp");

        User savedUser = userRepository.saveAndFlush(pendingUser);
        Long userId = savedUser.getId();
        entityManager.clear();

        User loadedUser = userRepository.findById(userId).orElseThrow();

        assertAll(
                () -> assertEquals("Alex", loadedUser.getFirstName()),
                () -> assertEquals("Morgan", loadedUser.getLastName()),
                () -> assertEquals("Alex Morgan", loadedUser.getDisplayName()),
                () -> assertEquals(
                        LocalDate.of(2000, 2, 3),
                        loadedUser.getBirthDate()
                ),
                () -> assertEquals(
                        "avatars/user-1.webp",
                        loadedUser.getAvatarKey()
                ),
                () -> assertTrue(loadedUser.isEmailVerified()),
                () -> assertTrue(loadedUser.isOnboardingCompleted())
        );
    }
}
