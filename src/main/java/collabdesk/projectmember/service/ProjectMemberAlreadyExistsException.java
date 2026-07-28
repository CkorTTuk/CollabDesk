package collabdesk.projectmember.service;

public class ProjectMemberAlreadyExistsException extends RuntimeException {
    public ProjectMemberAlreadyExistsException(String message) {
        super(message);
    }
}
