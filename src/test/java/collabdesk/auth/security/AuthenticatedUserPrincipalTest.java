package collabdesk.auth.security;

import collabdesk.user.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticatedUserPrincipalTest {

    private static final String PASSWORD_HASH = "{bcrypt}stored-hash";

    @Test
    void exposesUserDataRequiredBySecurityAndCurrentUserResponse() {
        AuthenticatedUserPrincipal principal = principal(
                UserStatus.ACTIVE,
                true,
                true
        );

        assertAll(
                () -> assertEquals(15L, principal.getUserId()),
                () -> assertEquals("student@example.com", principal.getEmail()),
                () -> assertEquals("student@example.com", principal.getUsername()),
                () -> assertEquals("Student", principal.getDisplayName()),
                () -> assertEquals(UserStatus.ACTIVE, principal.getStatus()),
                () -> assertTrue(principal.isEmailVerified()),
                () -> assertTrue(principal.isOnboardingCompleted()),
                () -> assertEquals(PASSWORD_HASH, principal.getPassword()),
                () -> assertEquals(
                        java.util.List.of(new SimpleGrantedAuthority(
                                CollabDeskAuthorities.PROFILE_COMPLETE
                        )),
                        principal.getAuthorities()
                )
        );
    }

    @Test
    void incompleteProfileDoesNotReceiveProfileCompleteAuthority() {
        AuthenticatedUserPrincipal principal = principal(
                UserStatus.ACTIVE,
                true,
                false
        );

        assertAll(
                () -> assertTrue(principal.isEmailVerified()),
                () -> assertFalse(principal.isOnboardingCompleted()),
                () -> assertTrue(principal.getAuthorities().isEmpty())
        );
    }

    @Test
    void activeUserIsEnabled() {
        assertTrue(principal(UserStatus.ACTIVE, true, true).isEnabled());
    }

    @Test
    void disabledUserIsNotEnabled() {
        assertFalse(principal(UserStatus.DISABLED, true, true).isEnabled());
    }

    @Test
    void eraseCredentialsRemovesOnlyPasswordHash() {
        AuthenticatedUserPrincipal principal = principal(
                UserStatus.ACTIVE,
                true,
                true
        );

        principal.eraseCredentials();

        assertAll(
                () -> assertNull(principal.getPassword()),
                () -> assertEquals(15L, principal.getUserId()),
                () -> assertEquals("student@example.com", principal.getUsername()),
                () -> assertEquals("Student", principal.getDisplayName()),
                () -> assertEquals(UserStatus.ACTIVE, principal.getStatus())
        );
    }

    private AuthenticatedUserPrincipal principal(
            UserStatus status,
            boolean emailVerified,
            boolean onboardingCompleted
    ) {
        return new AuthenticatedUserPrincipal(
                15L,
                "student@example.com",
                "Student",
                status,
                emailVerified,
                onboardingCompleted,
                PASSWORD_HASH
        );
    }
}
