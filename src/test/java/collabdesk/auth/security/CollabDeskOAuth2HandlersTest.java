package collabdesk.auth.security;

import collabdesk.user.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CollabDeskOAuth2HandlersTest {
    private static final String FRONTEND_URL = "https://app.example.test";

    @Test
    void successRedirectsIncompleteProfileToOnboarding() throws Exception {
        var handler = new CollabDeskOAuth2SuccessHandler(FRONTEND_URL + "/");
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                authentication(UserStatus.ACTIVE, false)
        );

        assertEquals(FRONTEND_URL + "/onboarding", response.getRedirectedUrl());
    }

    @Test
    void successRedirectsCompletedProfileToApplicationRoot() throws Exception {
        var handler = new CollabDeskOAuth2SuccessHandler(FRONTEND_URL);
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                authentication(UserStatus.ACTIVE, true)
        );

        assertEquals(FRONTEND_URL + "/", response.getRedirectedUrl());
    }

    @Test
    void successRedirectsPendingSecondFactorToMfaScreen() throws Exception {
        var handler = new CollabDeskOAuth2SuccessHandler(FRONTEND_URL);
        var response = new MockHttpServletResponse();
        var principal = principal(UserStatus.ACTIVE, true);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                java.util.List.of(new SimpleGrantedAuthority(
                        CollabDeskAuthorities.MFA_PENDING
                ))
        );

        handler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                authentication
        );

        assertEquals(FRONTEND_URL + "/auth/mfa", response.getRedirectedUrl());
    }

    @Test
    void disabledAccountInvalidatesSessionAndUsesSafeRedirect() throws Exception {
        var handler = new CollabDeskOAuth2SuccessHandler(FRONTEND_URL);
        var request = new MockHttpServletRequest();
        var session = request.getSession();
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request,
                response,
                authentication(UserStatus.DISABLED, true)
        );

        assertEquals(FRONTEND_URL + "/?oauth=disabled", response.getRedirectedUrl());
        assertThrows(IllegalStateException.class, session::isNew);
    }

    @Test
    void failureExposesOnlyAllowlistedErrorCodes() throws Exception {
        var handler = new CollabDeskOAuth2FailureHandler(FRONTEND_URL);

        assertEquals(
                FRONTEND_URL + "/?oauth=email_missing",
                redirectFor(handler, "github_email_missing")
        );
        assertEquals(
                FRONTEND_URL + "/?oauth=identity_conflict",
                redirectFor(handler, "external_identity_conflict")
        );
        assertEquals(
                FRONTEND_URL + "/?oauth=failed",
                redirectFor(handler, "provider_secret-token-details")
        );
    }

    private static String redirectFor(
            CollabDeskOAuth2FailureHandler handler,
            String providerErrorCode
    ) throws Exception {
        var response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(
                new MockHttpServletRequest(),
                response,
                new OAuth2AuthenticationException(new OAuth2Error(providerErrorCode))
        );
        return response.getRedirectedUrl();
    }

    private static Authentication authentication(
            UserStatus status,
            boolean onboardingCompleted
    ) {
        var principal = principal(status, onboardingCompleted);
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        );
    }

    private static AuthenticatedUserPrincipal principal(
            UserStatus status,
            boolean onboardingCompleted
    ) {
        return new AuthenticatedUserPrincipal(
                17L,
                "user@example.com",
                "User",
                status,
                true,
                onboardingCompleted,
                null
        );
    }
}
