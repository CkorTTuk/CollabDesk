package collabdesk.project.member.service;

public class ProjectMemberNotFoundException extends RuntimeException {
    public ProjectMemberNotFoundException(String message) {
        super(message);
    }
}
