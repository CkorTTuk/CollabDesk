package collabdesk.workspace.service.exceptions;

public class WorkspaceOperationForbiddenException extends RuntimeException {
    public WorkspaceOperationForbiddenException(String message) {
        super(message);
    }
}
