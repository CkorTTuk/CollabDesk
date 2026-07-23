package collabdesk.auth.dto;

import collabdesk.auth.security.AuthenticatedUserPrincipal;
import collabdesk.user.entity.UserStatus;

public record CurrentUserResponse(
        Long id,
        String email,
        String displayName,
        UserStatus status
) {
    public static CurrentUserResponse from(
            AuthenticatedUserPrincipal principal
    ) {
        return new CurrentUserResponse(
                principal.getUserId(),
                principal.getUsername(),
                principal.getDisplayName(),
                principal.getStatus()
        );
    }
}
