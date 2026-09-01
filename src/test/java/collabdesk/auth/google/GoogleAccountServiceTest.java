package collabdesk.auth.google;

import collabdesk.auth.entity.AuthIdentity;
import collabdesk.auth.entity.AuthProvider;
import collabdesk.auth.repository.AuthIdentityRepository;
import collabdesk.user.entity.User;
import collabdesk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleAccountServiceTest {

    @Mock
    private AuthIdentityRepository authIdentityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OidcUser oidcUser;

    private GoogleAccountService service;

    @BeforeEach
    void setUp() {
        service = new GoogleAccountService(
                authIdentityRepository,
                userRepository
        );
    }

    @Test
    void createsUserAndGoogleIdentityForNewVerifiedAccount() {
        googleClaims("google-subject", "  Student@Example.COM  ", "Google Student");
        when(authIdentityRepository.findWithUserByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                "google-subject"
        )).thenReturn(Optional.empty());
        when(userRepository.findByEmail("student@example.com"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User result = service.findOrCreate(oidcUser);

        ArgumentCaptor<AuthIdentity> identityCaptor =
                ArgumentCaptor.forClass(AuthIdentity.class);
        verify(authIdentityRepository).save(identityCaptor.capture());

        AuthIdentity identity = identityCaptor.getValue();
        assertAll(
                () -> assertEquals("student@example.com", result.getEmail()),
                () -> assertEquals("Google Student", result.getDisplayName()),
                () -> assertSame(result, identity.getUser()),
                () -> assertEquals(AuthProvider.GOOGLE, identity.getProvider()),
                () -> assertEquals("google-subject", identity.getProviderSubject()),
                () -> assertNull(identity.getPasswordHash())
        );
    }

    @Test
    void returnsExistingUserWhenGoogleSubjectIsAlreadyLinked() {
        User existingUser = new User("student@example.com", "Saved Name");
        AuthIdentity identity = AuthIdentity.google(existingUser, "google-subject");
        googleClaims("google-subject", "new-address@example.com", "Changed Name");
        when(authIdentityRepository.findWithUserByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                "google-subject"
        )).thenReturn(Optional.of(identity));

        User result = service.findOrCreate(oidcUser);

        assertSame(existingUser, result);
        assertEquals("Saved Name", result.getDisplayName());
        verifyNoInteractions(userRepository);
        verify(authIdentityRepository, never()).save(any());
    }

    @Test
    void linksVerifiedGoogleIdentityToExistingLocalUser() {
        User localUser = new User("student@example.com", "Local Name");
        googleClaims("google-subject", "student@example.com", "Google Name");
        when(authIdentityRepository.findWithUserByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                "google-subject"
        )).thenReturn(Optional.empty());
        when(userRepository.findByEmail("student@example.com"))
                .thenReturn(Optional.of(localUser));

        User result = service.findOrCreate(oidcUser);

        assertSame(localUser, result);
        assertEquals("Local Name", result.getDisplayName());
        verify(authIdentityRepository).save(any(AuthIdentity.class));
        verify(userRepository, never()).save(any());
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
        verifyNoInteractions(userRepository, authIdentityRepository);
    }

    @Test
    void rejectsDifferentGoogleSubjectForAlreadyLinkedUser() {
        User existingUser = new User("student@example.com", "Student");
        googleClaims("different-subject", "student@example.com", "Student");
        when(authIdentityRepository.findWithUserByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                "different-subject"
        )).thenReturn(Optional.empty());
        when(userRepository.findByEmail("student@example.com"))
                .thenReturn(Optional.of(existingUser));
        when(authIdentityRepository.existsByUser_IdAndProvider(
                existingUser.getId(),
                AuthProvider.GOOGLE
        )).thenReturn(true);

        OAuth2AuthenticationException exception = assertThrows(
                OAuth2AuthenticationException.class,
                () -> service.findOrCreate(oidcUser)
        );

        assertEquals("google_identity_conflict", exception.getError().getErrorCode());
        verify(authIdentityRepository, never()).save(any());
    }

    @Test
    void rejectsMissingGoogleSubjectBeforeUsingRepositories() {
        when(oidcUser.getSubject()).thenReturn("  ");

        OAuth2AuthenticationException exception = assertThrows(
                OAuth2AuthenticationException.class,
                () -> service.findOrCreate(oidcUser)
        );

        assertEquals("google_subject_missing", exception.getError().getErrorCode());
        verifyNoInteractions(userRepository, authIdentityRepository);
    }

    @Test
    void rejectsMissingGoogleEmailBeforeUsingRepositories() {
        when(oidcUser.getSubject()).thenReturn("google-subject");
        when(oidcUser.getEmail()).thenReturn(null);

        OAuth2AuthenticationException exception = assertThrows(
                OAuth2AuthenticationException.class,
                () -> service.findOrCreate(oidcUser)
        );

        assertEquals("google_email_missing", exception.getError().getErrorCode());
        verifyNoInteractions(userRepository, authIdentityRepository);
    }

    @Test
    void rejectsDisabledUserWhoseGoogleSubjectIsAlreadyLinked() {
        User disabledUser = new User("student@example.com", "Student");
        disabledUser.disable();
        AuthIdentity identity = AuthIdentity.google(disabledUser, "google-subject");
        googleClaims("google-subject", "student@example.com", "Student");
        when(authIdentityRepository.findWithUserByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                "google-subject"
        )).thenReturn(Optional.of(identity));

        OAuth2AuthenticationException exception = assertThrows(
                OAuth2AuthenticationException.class,
                () -> service.findOrCreate(oidcUser)
        );

        assertEquals("account_disabled", exception.getError().getErrorCode());
        verifyNoInteractions(userRepository);
        verify(authIdentityRepository, never()).save(any());
    }

    private void googleClaims(String subject, String email, String displayName) {
        when(oidcUser.getSubject()).thenReturn(subject);
        when(oidcUser.getEmail()).thenReturn(email);
        when(oidcUser.getEmailVerified()).thenReturn(true);
        lenient().when(oidcUser.getFullName()).thenReturn(displayName);
    }
}
