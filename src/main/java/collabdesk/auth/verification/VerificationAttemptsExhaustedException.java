package collabdesk.auth.verification;

public class VerificationAttemptsExhaustedException extends RuntimeException {
    public VerificationAttemptsExhaustedException() {
        super("Verification attempts are exhausted");
    }
}
