package collabdesk.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/** Maps OAuth failures to safe frontend error codes without exposing details. */
@Component
public class CollabDeskOAuth2FailureHandler
        implements AuthenticationFailureHandler {
    private static final Set<String> EMAIL_ERROR_CODES = Set.of(
            "external_email_missing",
            "external_email_invalid",
            "google_email_missing",
            "google_email_not_verified",
            "github_email_missing"
    );

    private final String frontendUrl;

    public CollabDeskOAuth2FailureHandler(
            @Value("${app.frontend-url}") String configuredFrontendUrl
    ) {
        this.frontendUrl = CollabDeskOAuth2SuccessHandler.normalizeFrontendUrl(
                configuredFrontendUrl
        );
    }

    @Override
    public void onAuthenticationFailure(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AuthenticationException exception
    ) throws IOException {
        CollabDeskOAuth2SuccessHandler.clearSession(request);
        response.sendRedirect(frontendUrl + "/?oauth=" + safeErrorCode(exception));
    }

    private static String safeErrorCode(AuthenticationException exception) {
        if (!(exception instanceof OAuth2AuthenticationException oauthException)) {
            return "failed";
        }

        String providerCode = oauthException.getError().getErrorCode();
        if ("account_disabled".equals(providerCode)) {
            return "disabled";
        }
        if ("external_identity_conflict".equals(providerCode)) {
            return "identity_conflict";
        }
        if (EMAIL_ERROR_CODES.contains(providerCode)) {
            return "email_missing";
        }
        return "failed";
    }
}
