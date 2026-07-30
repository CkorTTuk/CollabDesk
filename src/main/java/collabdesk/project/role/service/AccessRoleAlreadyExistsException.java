package collabdesk.project.role.service;

public class AccessRoleAlreadyExistsException extends RuntimeException {
    public AccessRoleAlreadyExistsException(String message) {
        super(message);
    }
}
