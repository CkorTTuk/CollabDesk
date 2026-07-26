package collabdesk.workspace.service.exceptions;

public class WorkspaceUserNotFoundException extends RuntimeException {
    public WorkspaceUserNotFoundException(String message) {
        super(message);
    }
}
