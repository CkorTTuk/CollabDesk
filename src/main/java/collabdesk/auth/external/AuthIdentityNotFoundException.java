package collabdesk.auth.external;

public class AuthIdentityNotFoundException extends RuntimeException {
    public AuthIdentityNotFoundException(String message) {
        super(message);
    }
}

