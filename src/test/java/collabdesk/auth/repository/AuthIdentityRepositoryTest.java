package collabdesk.auth.repository;

import collabdesk.TestcontainersConfiguration;
import collabdesk.auth.entity.AuthIdentity;
import collabdesk.auth.entity.AuthProvider;
import collabdesk.user.entity.User;
import collabdesk.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class AuthIdentityRepositoryTest {

    private static final String PASSWORD_HASH = "{bcrypt}test-hash";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthIdentityRepository authIdentityRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesLocalIdentityWithUser() {
        User savedUser = saveUser("email@gmail.com");
        AuthIdentity savedIdentity = saveLocalIdentity(
                savedUser,
                savedUser.getEmail()
        );

        Long userId = savedUser.getId();
        Long identityId = savedIdentity.getId();

        entityManager.clear();

        AuthIdentity loadedIdentity = authIdentityRepository
                .findById(identityId)
                .orElseThrow();

        assertAll(
                () -> assertEquals(AuthProvider.LOCAL, loadedIdentity.getProvider()),
                () -> assertEquals("email@gmail.com", loadedIdentity.getProviderSubject()),
                () -> assertEquals(PASSWORD_HASH, loadedIdentity.getPasswordHash()),
                () -> assertNotNull(loadedIdentity.getCreatedAt()),
                () -> assertEquals(userId, loadedIdentity.getUser().getId()),
                () -> assertEquals("email@gmail.com", loadedIdentity.getUser().getEmail())
        );
    }

    @Test
    void findsIdentityByProviderSubjectAndProvider() {
        User savedUser = saveUser("email@gmail.com");
        AuthIdentity savedIdentity = saveLocalIdentity(
                savedUser,
                "email@gmail.com"
        );
        Long identityId = savedIdentity.getId();

        entityManager.clear();

        AuthIdentity foundIdentity = authIdentityRepository
                .findByProviderAndProviderSubject(
                        AuthProvider.LOCAL,
                        "email@gmail.com"

                )
                .orElseThrow();

        assertEquals(identityId, foundIdentity.getId());
    }

    @Test
    void returnsEmptyWhenProviderAndSubjectDoNotExist() {
        assertTrue(
                authIdentityRepository
                        .findByProviderAndProviderSubject(
                                AuthProvider.LOCAL,
                                "missing@gmail.com"
                        )
                        .isEmpty()
        );
    }

    @Test
    void returnsTrueWhenProviderAndSubjectExist() {
        User savedUser = saveUser("email@gmail.com");
        saveLocalIdentity(savedUser, "email@gmail.com");

        entityManager.clear();

        assertTrue(
                authIdentityRepository
                        .existsByProviderAndProviderSubject(
                                AuthProvider.LOCAL,
                                "email@gmail.com"
                        )
        );
    }

    @Test
    void returnsFalseWhenProviderAndSubjectDoNotExist() {
        assertFalse(
                authIdentityRepository
                        .existsByProviderAndProviderSubject(
                                AuthProvider.LOCAL,
                                "missing@gmail.com"

                        )
        );
    }

    @Test
    void rejectsDuplicateProviderAndSubject() {
        User savedUser = saveUser("email@gmail.com");
        saveLocalIdentity(savedUser, "email@gmail.com");

        AuthIdentity duplicate = AuthIdentity.local(
                savedUser,
                "email@gmail.com",
                PASSWORD_HASH
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> authIdentityRepository.saveAndFlush(duplicate)
        );
    }

    @Test
    void rejectsLocalIdentityWithoutPasswordHash() {
        User savedUser = saveUser("email@gmail.com");

        assertThrows(
                IllegalArgumentException.class,
                () -> AuthIdentity.local(
                        savedUser,
                        "email@gmail.com",
                        null
                )
        );
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(
                new User(email, "User")
        );
    }

    private AuthIdentity saveLocalIdentity(User user, String providerSubject) {
        return authIdentityRepository.saveAndFlush(
                AuthIdentity.local(
                        user,
                        providerSubject,
                        PASSWORD_HASH
                )
        );
    }
}
