package collabdesk.account.avatar;

public class AvatarTooLargeException extends RuntimeException {
    public AvatarTooLargeException() { super("Avatar must not exceed 5 MB"); }
}
