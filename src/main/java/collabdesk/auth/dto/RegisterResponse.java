package collabdesk.auth.dto;

import collabdesk.auth.registration.RegistrationResult;
import collabdesk.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Newly registered CollabDesk account")
public record RegisterResponse(
        Long id,
        String email,
        String displayName,
        UserStatus status
) {
    public static RegisterResponse from (RegistrationResult result){
        return new RegisterResponse(
                result.id(),
                result.email(),
                result.displayName(),
                result.status()
        );
    }
}
