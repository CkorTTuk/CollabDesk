package collabdesk.auth.external;

import collabdesk.user.entity.User;

public record ExternalAccountResult(
        User user,
        boolean newlyCreated,
        boolean onboardingRequired
) {
}
