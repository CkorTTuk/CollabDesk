package collabdesk.account.onboarding;

import collabdesk.auth.external.ExternalProfileSuggestion;
import collabdesk.auth.security.CollabDeskPrincipal;
import collabdesk.user.entity.User;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

public record OnboardingResponse(
        Long id,
        String email,
        String displayName,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable LocalDate birthDate,
        boolean onboardingCompleted,
        @Nullable ExternalProfileSuggestion suggestion
) {
    public static OnboardingResponse from(
            User user,
            CollabDeskPrincipal principal
    ) {
        return new OnboardingResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getFirstName(),
                user.getLastName(),
                user.getBirthDate(),
                user.isOnboardingCompleted(),
                user.isOnboardingCompleted()
                        ? null
                        : principal.getExternalProfileSuggestion()
        );
    }
}
