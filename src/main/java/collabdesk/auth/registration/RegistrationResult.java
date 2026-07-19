package collabdesk.auth.registration;

import collabdesk.user.entity.UserStatus;


public record RegistrationResult(
        Long id,
        String email,
        String displayName,
        UserStatus status
){}
