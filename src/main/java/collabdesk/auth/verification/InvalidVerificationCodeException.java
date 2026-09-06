package collabdesk.auth.verification;

public class InvalidVerificationCodeException extends RuntimeException {
    public InvalidVerificationCodeException() {
        super("Verification code is invalid or expired");
    }
}
