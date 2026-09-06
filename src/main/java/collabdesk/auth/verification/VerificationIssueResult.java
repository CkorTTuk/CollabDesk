package collabdesk.auth.verification;

import java.time.Instant;

public record VerificationIssueResult(
        Instant expiresAt,
        Instant resendAvailableAt
) {
}
