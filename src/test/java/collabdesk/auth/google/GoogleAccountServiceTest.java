package collabdesk.auth.google;

import collabdesk.auth.entity.AuthProvider;
import collabdesk.auth.external.ExternalAccountResult;
import collabdesk.auth.external.ExternalAccountService;
import collabdesk.auth.external.ExternalIdentity;
import collabdesk.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleAccountServiceTest {

    @Mock
    private ExternalAccountService externalAccountService;

    @Mock
    private OidcUser oidcUser;

    private GoogleAccountService service;

    @BeforeEach
    void setUp() {
        service = new GoogleAccountService(externalAccountService);
    }

    @Test
    void mapsVerifiedGoogleClaimsToExternalIdentity() {
        User user = new User("student@example.com", "Student");
        ExternalAccountResult expected = new ExternalAccountResult(
                user,
                false,
                false
        );
        when(oidcUser.getSubject()).thenReturn(" google-subject ");
        when(oidcUser.getEmail()).thenReturn(" Student@Example.COM ");
        when(oidcUser.getEmailVerified()).thenReturn(true);
        when(oidcUser.getGivenName()).thenReturn("Google");
        when(oidcUser.getFamilyName()).thenReturn("Student");
        when(oidcUser.getPicture()).thenReturn("https://example.com/avatar.png");
        when(externalAccountService.findOrCreate(any())).thenReturn(expected);

        ExternalAccountResult result = service.findOrCreate(oidcUser);

        ArgumentCaptor<ExternalIdentity> identityCaptor =
                ArgumentCaptor.forClass(ExternalIdentity.class);
        verify(externalAccountService).findOrCreate(identityCaptor.capture());
        ExternalIdentity identity = identityCaptor.getValue();
        assertAll(
                () -> assertSame(expected, result),
                () -> assertEquals(AuthProvider.GOOGLE, identity.provider()),
                () -> assertEquals("google-subject", identity.providerSubject()),
                () -> assertEquals("Student@Example.COM", identity.verifiedEmail()),
                () -> assertEquals("Google", identity.suggestedFirstName()),
                () -> assertEquals("Student", identity.suggestedLastName()),
                () -> assertEquals(
                        "https://example.com/avatar.png",
                        identity.suggestedAvatarUrl()
                )
        );
    }

    @Test
    void rejectsUnverifiedGoogleEmail() {
        when(oidcUser.getSubject()).thenReturn("google-subject");
        when(oidcUser.getEmail()).thenReturn("student@example.com");
        when(oidcUser.getEmailVerified()).thenReturn(false);

        OAuth2AuthenticationException exception = assertThrows(
                OAuth2AuthenticationException.class,
                () -> service.findOrCreate(oidcUser)
        );

        assertEquals("google_email_not_verified", exception.getError().getErrorCode());
        verifyNoInteractions(externalAccountService);
    }

    @Test
    void rejectsMissingGoogleSubject() {
        when(oidcUser.getSubject()).thenReturn("  ");

        OAuth2AuthenticationException exception = assertThrows(
                OAuth2AuthenticationException.class,
                () -> service.findOrCreate(oidcUser)
        );

        assertEquals("google_subject_missing", exception.getError().getErrorCode());
        verifyNoInteractions(externalAccountService);
    }

    @Test
    void rejectsMissingGoogleEmail() {
        when(oidcUser.getSubject()).thenReturn("google-subject");
        when(oidcUser.getEmail()).thenReturn(null);

        OAuth2AuthenticationException exception = assertThrows(
                OAuth2AuthenticationException.class,
                () -> service.findOrCreate(oidcUser)
        );

        assertEquals("google_email_missing", exception.getError().getErrorCode());
        verifyNoInteractions(externalAccountService);
    }
}
