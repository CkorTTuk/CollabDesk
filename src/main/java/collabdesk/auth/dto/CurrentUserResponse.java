package collabdesk.auth.dto;

import collabdesk.auth.security.CollabDeskPrincipal;
import collabdesk.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Account represented by the current HTTP session")
public record CurrentUserResponse(
        Long id,
        String email,
        String displayName,
        UserStatus status,
        boolean emailVerified,
        boolean onboardingCompleted
) {
    public static CurrentUserResponse from(
            CollabDeskPrincipal principal
    ) {
        return new CurrentUserResponse(
                principal.getUserId(),
                principal.getEmail(),
                principal.getDisplayName(),
                principal.getStatus(),
                principal.isEmailVerified(),
                principal.isOnboardingCompleted()
        );
    }
}
