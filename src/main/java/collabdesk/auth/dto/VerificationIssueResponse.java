package collabdesk.auth.dto;

import collabdesk.auth.verification.VerificationIssueResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Current verification-code validity and resend timing")
public record VerificationIssueResponse(
        Instant expiresAt,
        Instant resendAvailableAt
) {
    public static VerificationIssueResponse from(VerificationIssueResult result) {
        return new VerificationIssueResponse(
                result.expiresAt(),
                result.resendAvailableAt()
        );
    }
}
