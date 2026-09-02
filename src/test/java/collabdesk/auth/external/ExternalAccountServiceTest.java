package collabdesk.auth.external;

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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalAccountServiceTest {

    @Mock
    private AuthIdentityRepository authIdentityRepository;

    @Mock
    private UserRepository userRepository;

    private ExternalAccountService service;

    @BeforeEach
    void setUp() {
        service = new ExternalAccountService(
                authIdentityRepository,
                userRepository
        );
    }

    @Test
    void createsPendingUserAndIdentityForNewExternalAccount() {
        when(authIdentityRepository.findWithUserByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                "google-subject"
        )).thenReturn(Optional.empty());
        when(userRepository.findByEmail("student@example.com"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExternalAccountResult result = service.findOrCreate(googleIdentity(
                "google-subject",
                "  Student@Example.COM  "
        ));

        ArgumentCaptor<AuthIdentity> identityCaptor =
                ArgumentCaptor.forClass(AuthIdentity.class);
        verify(authIdentityRepository).save(identityCaptor.capture());
        AuthIdentity savedIdentity = identityCaptor.getValue();
        assertAll(
                () -> assertEquals("student@example.com", result.user().getEmail()),
                () -> assertEquals("student", result.user().getDisplayName()),
                () -> assertTrue(result.user().isEmailVerified()),
                () -> assertFalse(result.user().isOnboardingCompleted()),
                () -> assertTrue(result.newlyCreated()),
                () -> assertTrue(result.onboardingRequired()),
                () -> assertSame(result.user(), savedIdentity.getUser()),
                () -> assertEquals(AuthProvider.GOOGLE, savedIdentity.getProvider()),
                () -> assertEquals("google-subject", savedIdentity.getProviderSubject()),
                () -> assertNull(savedIdentity.getPasswordHash())
        );
    }

    @Test
    void returnsExistingUserForRepeatedProviderSubject() {
        User existingUser = new User("saved@example.com", "Saved Name");
        AuthIdentity identity = AuthIdentity.google(
                existingUser,
                "google-subject"
        );
        when(authIdentityRepository.findWithUserByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                "google-subject"
        )).thenReturn(Optional.of(identity));

        ExternalAccountResult result = service.findOrCreate(googleIdentity(
                "google-subject",
                "changed@example.com"
        ));

        assertAll(
                () -> assertSame(existingUser, result.user()),
                () -> assertEquals("Saved Name", result.user().getDisplayName()),
                () -> assertFalse(result.newlyCreated()),
                () -> assertFalse(result.onboardingRequired())
        );
        verifyNoInteractions(userRepository);
        verify(authIdentityRepository, never()).save(any());
    }

    @Test
    void linksProviderToExistingUserWithoutChangingChosenName() {
        User existingUser = new User("student@example.com", "Chosen Name");
        ReflectionTestUtils.setField(existingUser, "id", 17L);
        when(authIdentityRepository.findWithUserByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                "google-subject"
        )).thenReturn(Optional.empty());
        when(userRepository.findByEmail("student@example.com"))
                .thenReturn(Optional.of(existingUser));

        ExternalAccountResult result = service.findOrCreate(googleIdentity(
                "google-subject",
                "student@example.com"
        ));

        assertAll(
                () -> assertSame(existingUser, result.user()),
                () -> assertEquals("Chosen Name", result.user().getDisplayName()),
                () -> assertFalse(result.newlyCreated()),
                () -> assertFalse(result.onboardingRequired())
        );
        verify(userRepository, never()).save(any());
        verify(authIdentityRepository).save(any(AuthIdentity.class));
    }

    @Test
    void rejectsDisabledUserForExistingIdentity() {
        User disabledUser = new User("student@example.com", "Student");
        disabledUser.disable();
        when(authIdentityRepository.findWithUserByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                "google-subject"
        )).thenReturn(Optional.of(AuthIdentity.google(
                disabledUser,
                "google-subject"
        )));

        OAuth2AuthenticationException exception = assertThrows(
                OAuth2AuthenticationException.class,
                () -> service.findOrCreate(googleIdentity(
                        "google-subject",
                        "student@example.com"
                ))
        );

        assertEquals("account_disabled", exception.getError().getErrorCode());
        verifyNoInteractions(userRepository);
        verify(authIdentityRepository, never()).save(any());
    }

    @Test
    void rejectsSecondSubjectFromSameProviderForExistingUser() {
        User existingUser = new User("student@example.com", "Student");
        ReflectionTestUtils.setField(existingUser, "id", 19L);
        when(authIdentityRepository.findWithUserByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                "second-subject"
        )).thenReturn(Optional.empty());
        when(userRepository.findByEmail("student@example.com"))
                .thenReturn(Optional.of(existingUser));
        when(authIdentityRepository.existsByUser_IdAndProvider(
                19L,
                AuthProvider.GOOGLE
        )).thenReturn(true);

        OAuth2AuthenticationException exception = assertThrows(
                OAuth2AuthenticationException.class,
                () -> service.findOrCreate(googleIdentity(
                        "second-subject",
                        "student@example.com"
                ))
        );

        assertEquals(
                "external_identity_conflict",
                exception.getError().getErrorCode()
        );
        verify(authIdentityRepository, never()).save(any());
    }

    @Test
    void rejectsLocalProviderAndInvalidEmailBeforeReadingDatabase() {
        ExternalIdentity localIdentity = new ExternalIdentity(
                AuthProvider.LOCAL,
                "subject",
                "invalid-email",
                null,
                null,
                null
        );

        OAuth2AuthenticationException providerException = assertThrows(
                OAuth2AuthenticationException.class,
                () -> service.findOrCreate(localIdentity)
        );
        assertEquals(
                "unsupported_external_provider",
                providerException.getError().getErrorCode()
        );

        OAuth2AuthenticationException emailException = assertThrows(
                OAuth2AuthenticationException.class,
                () -> service.findOrCreate(googleIdentity("subject", "invalid-email"))
        );
        assertEquals("external_email_invalid", emailException.getError().getErrorCode());
        verifyNoInteractions(userRepository, authIdentityRepository);
    }

    private ExternalIdentity googleIdentity(String subject, String email) {
        return new ExternalIdentity(
                AuthProvider.GOOGLE,
                subject,
                email,
                "Suggested",
                "Name",
                "https://example.com/avatar.png"
        );
    }
}
