package collabdesk.account.onboarding;

import collabdesk.auth.security.CollabDeskPrincipal;
import collabdesk.auth.security.CurrentAuthenticationRefresher;
import collabdesk.user.entity.User;
import collabdesk.user.entity.UserStatus;
import collabdesk.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and completes the profile required after first authentication. It is
 * also the boundary that decides whether the current account may onboard.
 */
@Service
public class OnboardingService {
    private final UserRepository userRepository;
    private final CurrentAuthenticationRefresher authenticationRefresher;

    public OnboardingService(
            UserRepository userRepository,
            CurrentAuthenticationRefresher authenticationRefresher
    ) {
        this.userRepository = userRepository;
        this.authenticationRefresher = authenticationRefresher;
    }

    /** Returns the current incomplete profile and provider suggestions. */
    @Transactional(readOnly = true)
    public OnboardingResponse getOnboarding(CollabDeskPrincipal principal) {
        User user = requireActiveUser(principal.getUserId());
        return OnboardingResponse.from(user, principal);
    }

    /** Validates and stores the profile, then unlocks normal application access. */
    @Transactional
    public OnboardingResponse completeOnboarding(
            CollabDeskPrincipal principal,
            Authentication authentication,
            HttpServletRequest servletRequest,
            HttpServletResponse response,
            CompleteOnboardingRequest update
    ) {
        User user = requireActiveUser(principal.getUserId());

        if (!user.isOnboardingCompleted()) {
            user.completeOnboarding(
                    update.firstName(),
                    update.lastName(),
                    update.birthDate()
            );
            userRepository.saveAndFlush(user);
        }

        CollabDeskPrincipal refreshedPrincipal = authenticationRefresher.refresh(
                user, authentication, servletRequest, response
        );
        return OnboardingResponse.from(user, refreshedPrincipal);
    }

    private User requireActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(OnboardingAccountUnavailableException::new);
    }

}
