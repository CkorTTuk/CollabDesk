package collabdesk.auth.security;

import collabdesk.TestcontainersConfiguration;
import collabdesk.auth.entity.AuthIdentity;
import collabdesk.auth.repository.AuthIdentityRepository;
import collabdesk.user.entity.User;
import collabdesk.user.entity.UserStatus;
import collabdesk.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({
        TestcontainersConfiguration.class,
        LocalUserDetailsService.class
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class LocalUserDetailsServiceTest {

    private static final String PASSWORD_HASH = "{bcrypt}stored-hash";

    @Autowired
    private LocalUserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthIdentityRepository authIdentityRepository;

    @Test
    void normalizesEmailAndBuildsPrincipalFromLocalIdentity() {
        User user = saveUser("student@example.com", "Student");
        saveLocalIdentity(user, user.getEmail());

        AuthenticatedUserPrincipal principal =
                (AuthenticatedUserPrincipal) userDetailsService
                        .loadUserByUsername("  StUdEnT@Example.COM  ");

        assertAll(
                () -> assertEquals(user.getId(), principal.getUserId()),
                () -> assertEquals("student@example.com", principal.getUsername()),
                () -> assertEquals("student@example.com", principal.getEmail()),
                () -> assertEquals("Student", principal.getDisplayName()),
                () -> assertEquals(UserStatus.ACTIVE, principal.getStatus()),
                () -> assertEquals(PASSWORD_HASH, principal.getPassword()),
                () -> assertTrue(principal.isEnabled())
        );
    }

    @Test
    void unknownEmailThrowsUsernameNotFoundException() {
        assertThrowsExactly(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("missing@example.com")
        );
    }

    @Test
    void blankUsernameThrowsUsernameNotFoundException() {
        assertThrowsExactly(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("   ")
        );
    }

    @Test
    void nullUsernameThrowsUsernameNotFoundException() {
        assertThrowsExactly(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername(null)
        );
    }

    @Test
    void googleOnlyIdentityCannotBeUsedForLocalPasswordLogin() {
        User user = saveUser("google@example.com", "Google User");
        authIdentityRepository.saveAndFlush(
                AuthIdentity.google(user, "google-provider-subject")
        );

        assertThrowsExactly(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("google@example.com")
        );
    }

    @Test
    void disabledUserIsReturnedAsNotEnabled() {
        User user = saveUser("disabled@example.com", "Disabled User");
        user.disable();
        userRepository.saveAndFlush(user);
        saveLocalIdentity(user, user.getEmail());

        AuthenticatedUserPrincipal principal =
                (AuthenticatedUserPrincipal) userDetailsService
                        .loadUserByUsername("disabled@example.com");

        assertAll(
                () -> assertEquals(UserStatus.DISABLED, principal.getStatus()),
                () -> assertFalse(principal.isEnabled())
        );
    }

    private User saveUser(String email, String displayName) {
        return userRepository.saveAndFlush(
                new User(email, displayName)
        );
    }

    private void saveLocalIdentity(User user, String providerSubject) {
        authIdentityRepository.saveAndFlush(
                AuthIdentity.local(
                        user,
                        providerSubject,
                        PASSWORD_HASH
                )
        );
    }
}
