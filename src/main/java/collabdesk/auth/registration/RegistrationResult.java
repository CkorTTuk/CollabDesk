package collabdesk.auth.registration;

import collabdesk.user.entity.UserStatus;

import java.time.Instant;

public record RegistrationResult(
        Long id,
        String email,
        String displayName,
        UserStatus status,
        boolean verificationRequired,
        Instant expiresAt,
        Instant resendAvailableAt
){}
