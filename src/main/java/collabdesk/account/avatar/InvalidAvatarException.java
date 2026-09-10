package collabdesk.account.avatar;

public class InvalidAvatarException extends RuntimeException {
    public InvalidAvatarException(String message) { super(message); }
    public InvalidAvatarException(String message, Throwable cause) { super(message, cause); }
}
