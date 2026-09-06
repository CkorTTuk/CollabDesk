package collabdesk.account.onboarding;

import collabdesk.auth.security.CollabDeskPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary for reading and completing the first-login profile. */
@RestController
@RequestMapping("/api/v1/account/onboarding")
public class OnboardingController {
    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GetMapping
    public OnboardingResponse getOnboarding(
            @AuthenticationPrincipal CollabDeskPrincipal principal
    ) {
        return onboardingService.getOnboarding(principal);
    }

    @PostMapping("/complete")
    public OnboardingResponse completeOnboarding(
            @AuthenticationPrincipal CollabDeskPrincipal principal,
            Authentication authentication,
            @Valid @RequestBody CompleteOnboardingRequest request
    ) {
        return onboardingService.completeOnboarding(
                principal,
                authentication,
                request
        );
    }
}
