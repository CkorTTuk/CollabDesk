package collabdesk.project.member.service;

public class ProjectMemberAlreadyExistsException extends RuntimeException {
    public ProjectMemberAlreadyExistsException(String message) {
        super(message);
    }
}
