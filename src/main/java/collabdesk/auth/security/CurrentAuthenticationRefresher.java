package collabdesk.auth.security;

import collabdesk.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;

/** Rebuilds a session principal after mutable account fields have changed. */
@Component
public class CurrentAuthenticationRefresher {
    private final SecurityContextRepository securityContextRepository;

    public CurrentAuthenticationRefresher(
            SecurityContextRepository securityContextRepository
    ) {
        this.securityContextRepository = securityContextRepository;
    }

    public CollabDeskPrincipal refresh(
            User user,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        CollabDeskPrincipal principal = refreshedPrincipal(
                user,
                (CollabDeskPrincipal) authentication.getPrincipal()
        );
        AbstractAuthenticationToken refreshedAuthentication;
        if (authentication instanceof OAuth2AuthenticationToken oauthToken
                && principal instanceof OAuth2User oauthPrincipal) {
            refreshedAuthentication = new OAuth2AuthenticationToken(
                    oauthPrincipal,
                    oauthPrincipal.getAuthorities(),
                    oauthToken.getAuthorizedClientRegistrationId()
            );
        } else {
            refreshedAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal,
                    authentication.getCredentials(),
                    authorities(principal)
            );
        }
        refreshedAuthentication.setDetails(authentication.getDetails());
        SecurityContext context = SecurityContextHolder.getContext();
        context.setAuthentication(refreshedAuthentication);
        securityContextRepository.saveContext(context, request, response);
        return principal;
    }

    private Collection<? extends GrantedAuthority> authorities(
            CollabDeskPrincipal principal
    ) {
        if (principal instanceof AuthenticatedUserPrincipal localPrincipal) {
            return localPrincipal.getAuthorities();
        }
        if (principal instanceof OAuth2User oauthPrincipal) {
            return oauthPrincipal.getAuthorities();
        }
        throw new IllegalStateException("Unsupported CollabDesk principal type");
    }

    private CollabDeskPrincipal refreshedPrincipal(
            User user,
            CollabDeskPrincipal principal
    ) {
        if (principal instanceof GoogleOidcPrincipal googlePrincipal) {
            return new GoogleOidcPrincipal(user, googlePrincipal);
        }
        if (principal instanceof GitHubOAuth2Principal gitHubPrincipal) {
            return new GitHubOAuth2Principal(user, gitHubPrincipal);
        }
        if (principal instanceof AuthenticatedUserPrincipal localPrincipal) {
            return new AuthenticatedUserPrincipal(
                    user.getId(), user.getEmail(), user.getDisplayName(),
                    user.getStatus(), user.isEmailVerified(),
                    user.isOnboardingCompleted(), localPrincipal.getPassword()
            );
        }
        throw new IllegalStateException("Unsupported CollabDesk principal type");
    }
}
