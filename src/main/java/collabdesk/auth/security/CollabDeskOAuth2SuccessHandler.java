package collabdesk.auth.security;

import collabdesk.user.entity.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Redirects a successful OAuth login to onboarding or the main application. */
@Component
public class CollabDeskOAuth2SuccessHandler
        implements AuthenticationSuccessHandler {
    private final String frontendUrl;

    public CollabDeskOAuth2SuccessHandler(
            @Value("${app.frontend-url}") String configuredFrontendUrl
    ) {
        this.frontendUrl = normalizeFrontendUrl(configuredFrontendUrl);
    }

    @Override
    public void onAuthenticationSuccess(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Authentication authentication
    ) throws IOException {
        if (!(authentication.getPrincipal() instanceof CollabDeskPrincipal principal)) {
            clearSession(request);
            response.sendRedirect(frontendUrl + "/?oauth=failed");
            return;
        }

        if (principal.getStatus() != UserStatus.ACTIVE) {
            clearSession(request);
            response.sendRedirect(frontendUrl + "/?oauth=disabled");
            return;
        }

        if (!principal.isOnboardingCompleted()) {
            response.sendRedirect(frontendUrl + "/onboarding");
            return;
        }

        if (authentication.getAuthorities().stream().anyMatch(authority ->
                CollabDeskAuthorities.MFA_PENDING.equals(authority.getAuthority()))) {
            response.sendRedirect(frontendUrl + "/auth/mfa");
            return;
        }

        response.sendRedirect(frontendUrl + "/");
    }

    static String normalizeFrontendUrl(String configuredFrontendUrl) {
        if (configuredFrontendUrl == null || configuredFrontendUrl.isBlank()) {
            throw new IllegalArgumentException("app.frontend-url must not be blank");
        }
        String normalized = configuredFrontendUrl.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    static void clearSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
