package collabdesk.auth.dto;

import collabdesk.auth.registration.RegistrationResult;
import collabdesk.user.entity.UserStatus;

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