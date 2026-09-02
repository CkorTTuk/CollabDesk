package collabdesk.auth.security;

import collabdesk.user.entity.User;
import collabdesk.user.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleOidcPrincipalTest {

    @Test
    void combinesLocalCollabDeskIdentityWithVerifiedOidcClaims() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(user.getEmail()).thenReturn("local@example.com");
        when(user.getDisplayName()).thenReturn("Local Display Name");
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.isEmailVerified()).thenReturn(true);
        when(user.isOnboardingCompleted()).thenReturn(true);

        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "signed-id-token",
                now,
                now.plusSeconds(300),
                Map.of(
                        "sub", "stable-google-subject",
                        "email", "google@example.com",
                        "email_verified", true
                )
        );
        OidcUser oidcUser = new DefaultOidcUser(List.of(), idToken, "sub");

        GoogleOidcPrincipal principal = new GoogleOidcPrincipal(user, oidcUser);

        assertAll(
                () -> assertEquals(42L, principal.getUserId()),
                () -> assertEquals("local@example.com", principal.getEmail()),
                () -> assertEquals("Local Display Name", principal.getDisplayName()),
                () -> assertEquals(UserStatus.ACTIVE, principal.getStatus()),
                () -> assertTrue(principal.isEmailVerified()),
                () -> assertTrue(principal.isOnboardingCompleted()),
                () -> assertTrue(principal.getAuthorities().contains(
                        new SimpleGrantedAuthority(
                                CollabDeskAuthorities.PROFILE_COMPLETE
                        )
                )),
                () -> assertEquals("stable-google-subject", principal.getSubject())
        );
    }

    @Test
    void incompleteProfilePreservesOidcAuthoritiesWithoutProfileComplete() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(43L);
        when(user.getEmail()).thenReturn("new@example.com");
        when(user.getDisplayName()).thenReturn("new");
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.isEmailVerified()).thenReturn(true);
        when(user.isOnboardingCompleted()).thenReturn(false);

        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "signed-id-token",
                now,
                now.plusSeconds(300),
                Map.of("sub", "new-google-subject")
        );
        var oidcAuthority = new SimpleGrantedAuthority("OIDC_USER");
        OidcUser oidcUser = new DefaultOidcUser(
                List.of(oidcAuthority),
                idToken,
                "sub"
        );

        GoogleOidcPrincipal principal = new GoogleOidcPrincipal(user, oidcUser);

        assertAll(
                () -> assertTrue(principal.isEmailVerified()),
                () -> assertFalse(principal.isOnboardingCompleted()),
                () -> assertEquals(Set.of(oidcAuthority), principal.getAuthorities())
        );
    }
}
