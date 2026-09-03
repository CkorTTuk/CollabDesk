package collabdesk.auth.github;

import collabdesk.auth.entity.AuthProvider;
import collabdesk.auth.external.ExternalAccountResult;
import collabdesk.auth.external.ExternalAccountService;
import collabdesk.auth.external.ExternalIdentity;
import collabdesk.auth.security.GitHubOAuth2Principal;
import collabdesk.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubOAuth2UserServiceTest {
    @Mock
    private OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    @Mock
    private GitHubEmailClient emailClient;
    @Mock
    private ExternalAccountService externalAccountService;

    private GitHubOAuth2UserService service;

    @BeforeEach
    void setUp() {
        service = new GitHubOAuth2UserService(
                delegate,
                emailClient,
                externalAccountService
        );
    }

    @Test
    void mapsStableIdAndVerifiedEmailToInternalPrincipal() {
        OAuth2UserRequest request = request("github", "secret-access-token");
        OAuth2User providerUser = providerUser(Map.of(
                "id", 987654321L,
                "login", "octocat",
                "name", "Mona Lisa",
                "avatar_url", "https://avatars.example.test/octocat"
        ));
        User user = User.pendingExternal("mona@example.com", "mona");
        ReflectionTestUtils.setField(user, "id", 42L);

        when(delegate.loadUser(request)).thenReturn(providerUser);
        when(emailClient.findPrimaryVerifiedEmail("secret-access-token"))
                .thenReturn("mona@example.com");
        when(externalAccountService.findOrCreate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ExternalAccountResult(user, true, true));

        GitHubOAuth2Principal principal = assertInstanceOf(
                GitHubOAuth2Principal.class,
                service.loadUser(request)
        );
        ArgumentCaptor<ExternalIdentity> identityCaptor =
                ArgumentCaptor.forClass(ExternalIdentity.class);
        verify(externalAccountService).findOrCreate(identityCaptor.capture());
        ExternalIdentity identity = identityCaptor.getValue();

        assertEquals(AuthProvider.GITHUB, identity.provider());
        assertEquals("987654321", identity.providerSubject());
        assertEquals("mona@example.com", identity.verifiedEmail());
        assertEquals("Mona", identity.suggestedFirstName());
        assertEquals("Lisa", identity.suggestedLastName());
        assertEquals(42L, principal.getUserId());
        assertFalse(principal.isOnboardingCompleted());
    }

    @Test
    void rejectsMissingGitHubIdBeforeCallingEmailApi() {
        OAuth2UserRequest request = request("github", "secret-access-token");
        when(delegate.loadUser(request)).thenReturn(providerUser(Map.of(
                "id", 0,
                "login", "octocat"
        )));

        OAuth2AuthenticationException exception = assertThrows(
                OAuth2AuthenticationException.class,
                () -> service.loadUser(request)
        );

        assertEquals("github_subject_missing", exception.getError().getErrorCode());
        verify(emailClient, never()).findPrimaryVerifiedEmail(
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void rejectsOtherRegistrationBeforeCallingProvider() {
        OAuth2UserRequest request = request("other", "secret-access-token");

        OAuth2AuthenticationException exception = assertThrows(
                OAuth2AuthenticationException.class,
                () -> service.loadUser(request)
        );

        assertEquals(
                "unsupported_oauth2_provider",
                exception.getError().getErrorCode()
        );
        verify(delegate, never()).loadUser(request);
    }

    private static OAuth2User providerUser(Map<String, Object> attributes) {
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                attributes,
                "id"
        );
    }

    private static OAuth2UserRequest request(String registrationId, String token) {
        ClientRegistration registration = ClientRegistration
                .withRegistrationId(registrationId)
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://provider.test/authorize")
                .tokenUri("https://provider.test/token")
                .userInfoUri("https://provider.test/user")
                .userNameAttributeName("id")
                .clientName("GitHub")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                token,
                Instant.now(),
                Instant.now().plusSeconds(60)
        );
        return new OAuth2UserRequest(registration, accessToken);
    }
}
