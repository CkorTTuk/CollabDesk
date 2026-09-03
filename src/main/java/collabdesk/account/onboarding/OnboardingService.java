package collabdesk.account.onboarding;

import collabdesk.auth.security.AuthenticatedUserPrincipal;
import collabdesk.auth.security.CollabDeskPrincipal;
import collabdesk.auth.security.GoogleOidcPrincipal;
import collabdesk.auth.security.GitHubOAuth2Principal;
import collabdesk.user.entity.User;
import collabdesk.user.entity.UserStatus;
import collabdesk.user.repository.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {
    private final UserRepository userRepository;

    public OnboardingService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public OnboardingResponse getOnboarding(CollabDeskPrincipal principal) {
        User user = requireActiveUser(principal.getUserId());
        return OnboardingResponse.from(user, principal);
    }

    @Transactional
    public OnboardingResponse completeOnboarding(
            CollabDeskPrincipal principal,
            Authentication authentication,
            CompleteOnboardingRequest request
    ) {
        User user = requireActiveUser(principal.getUserId());

        if (!user.isOnboardingCompleted()) {
            user.completeOnboarding(
                    request.firstName(),
                    request.lastName(),
                    request.birthDate()
            );
            userRepository.saveAndFlush(user);
        }

        CollabDeskPrincipal refreshedPrincipal = refreshPrincipal(user, principal);
        replaceAuthentication(authentication, refreshedPrincipal);
        return OnboardingResponse.from(user, refreshedPrincipal);
    }

    private User requireActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(OnboardingAccountUnavailableException::new);
    }

    private CollabDeskPrincipal refreshPrincipal(
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
                    user.getId(),
                    user.getEmail(),
                    user.getDisplayName(),
                    user.getStatus(),
                    user.isEmailVerified(),
                    user.isOnboardingCompleted(),
                    localPrincipal.getPassword()
            );
        }
        throw new IllegalStateException("Unsupported CollabDesk principal type");
    }

    private void replaceAuthentication(
            Authentication authentication,
            CollabDeskPrincipal principal
    ) {
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
                    principal instanceof AuthenticatedUserPrincipal localPrincipal
                            ? localPrincipal.getAuthorities()
                            : authentication.getAuthorities()
            );
        }
        refreshedAuthentication.setDetails(authentication.getDetails());
        SecurityContextHolder.getContext().setAuthentication(
                refreshedAuthentication
        );
    }
}
