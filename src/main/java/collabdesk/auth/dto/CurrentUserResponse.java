package collabdesk.auth.dto;

import collabdesk.auth.security.AuthenticatedUserPrincipal;
import collabdesk.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Account represented by the current HTTP session")
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
