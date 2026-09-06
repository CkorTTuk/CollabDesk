package collabdesk.auth.verification;

public class VerificationResendTooSoonException extends RuntimeException {
    private final long retryAfterSeconds;

    public VerificationResendTooSoonException(long retryAfterSeconds) {
        super("A new verification code was requested too soon");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
