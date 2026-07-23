package collabdesk.auth.security;

import collabdesk.user.entity.UserStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticatedUserPrincipalTest {

    private static final String PASSWORD_HASH = "{bcrypt}stored-hash";

    @Test
    void exposesUserDataRequiredBySecurityAndCurrentUserResponse() {
        AuthenticatedUserPrincipal principal = principal(UserStatus.ACTIVE);

        assertAll(
                () -> assertEquals(15L, principal.getUserId()),
                () -> assertEquals("student@example.com", principal.getEmail()),
                () -> assertEquals("student@example.com", principal.getUsername()),
                () -> assertEquals("Student", principal.getDisplayName()),
                () -> assertEquals(UserStatus.ACTIVE, principal.getStatus()),
                () -> assertEquals(PASSWORD_HASH, principal.getPassword()),
                () -> assertTrue(principal.getAuthorities().isEmpty())
        );
    }

    @Test
    void activeUserIsEnabled() {
        assertTrue(principal(UserStatus.ACTIVE).isEnabled());
    }

    @Test
    void disabledUserIsNotEnabled() {
        assertFalse(principal(UserStatus.DISABLED).isEnabled());
    }

    @Test
    void eraseCredentialsRemovesOnlyPasswordHash() {
        AuthenticatedUserPrincipal principal = principal(UserStatus.ACTIVE);

        principal.eraseCredentials();

        assertAll(
                () -> assertNull(principal.getPassword()),
                () -> assertEquals(15L, principal.getUserId()),
                () -> assertEquals("student@example.com", principal.getUsername()),
                () -> assertEquals("Student", principal.getDisplayName()),
                () -> assertEquals(UserStatus.ACTIVE, principal.getStatus())
        );
    }

    private AuthenticatedUserPrincipal principal(UserStatus status) {
        return new AuthenticatedUserPrincipal(
                15L,
                "student@example.com",
                "Student",
                status,
                PASSWORD_HASH
        );
    }
}
