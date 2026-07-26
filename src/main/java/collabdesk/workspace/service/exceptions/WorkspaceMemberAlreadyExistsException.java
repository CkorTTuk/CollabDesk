package collabdesk.workspace.service.exceptions;

public class WorkspaceMemberAlreadyExistsException extends RuntimeException {
    public WorkspaceMemberAlreadyExistsException(String message) {
        super(message);
    }
}
