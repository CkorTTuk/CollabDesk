package collabdesk.project.role.service;

public class AccessRoleInUseException extends RuntimeException {
    public AccessRoleInUseException(String message) {
        super(message);
    }
}
