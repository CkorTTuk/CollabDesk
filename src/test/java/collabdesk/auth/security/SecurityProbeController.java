package collabdesk.auth.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SecurityProbeController {
    @GetMapping("/api/v1/security-probe")
    void protectedApi() {
    }

    @GetMapping("/api/v1/account/onboarding/probe")
    void onboardingApi() {
    }
}
