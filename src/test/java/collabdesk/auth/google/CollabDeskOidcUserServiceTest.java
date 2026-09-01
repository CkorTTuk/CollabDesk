package collabdesk.auth.google;

import collabdesk.auth.security.GoogleOidcPrincipal;
import collabdesk.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollabDeskOidcUserServiceTest {

    @Mock
    private OidcUserService delegate;

    @Mock
    private GoogleAccountService googleAccountService;

    @Mock
    private OidcUserRequest request;

    @Mock
    private ClientRegistration clientRegistration;

    @Mock
    private OidcUser oidcUser;

    private CollabDeskOidcUserService service;

    @BeforeEach
    void setUp() {
        service = new CollabDeskOidcUserService(delegate, googleAccountService);
        when(request.getClientRegistration()).thenReturn(clientRegistration);
    }

    @Test
    void provisionsGoogleAccountAndReturnsCollabDeskPrincipal() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(73L);
        when(user.getEmail()).thenReturn("student@example.com");
        when(user.getDisplayName()).thenReturn("Student");
        when(user.getStatus()).thenReturn(collabdesk.user.entity.UserStatus.ACTIVE);
        when(clientRegistration.getRegistrationId()).thenReturn("google");
        when(delegate.loadUser(request)).thenReturn(oidcUser);
        when(googleAccountService.findOrCreate(oidcUser)).thenReturn(user);
        when(oidcUser.getAuthorities()).thenReturn(java.util.List.of());
        when(oidcUser.getIdToken()).thenReturn(TestOidcTokens.idToken());
        when(oidcUser.getUserInfo()).thenReturn(null);

        OidcUser result = service.loadUser(request);

        GoogleOidcPrincipal principal = assertInstanceOf(
                GoogleOidcPrincipal.class,
                result
        );
        assertEquals(73L, principal.getUserId());
        assertEquals("student@example.com", principal.getEmail());
        verify(delegate).loadUser(request);
        verify(googleAccountService).findOrCreate(oidcUser);
    }

    @Test
    void rejectsUnsupportedRegistrationBeforeCallingGoogle() {
        when(clientRegistration.getRegistrationId()).thenReturn("another-provider");

        OAuth2AuthenticationException exception = assertThrows(
                OAuth2AuthenticationException.class,
                () -> service.loadUser(request)
        );

        assertEquals("unsupported_oidc_provider", exception.getError().getErrorCode());
        verifyNoInteractions(delegate, googleAccountService);
    }

    @Test
    void propagatesDelegateAuthenticationFailureWithoutCreatingAccount() {
        when(clientRegistration.getRegistrationId()).thenReturn("google");
        OAuth2AuthenticationException failure = new OAuth2AuthenticationException(
                new OAuth2Error("invalid_id_token")
        );
        when(delegate.loadUser(request)).thenThrow(failure);

        OAuth2AuthenticationException result = assertThrows(
                OAuth2AuthenticationException.class,
                () -> service.loadUser(request)
        );

        assertSame(failure, result);
        verifyNoInteractions(googleAccountService);
    }
}
