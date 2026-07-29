package collabdesk.task.service;

public class TaskVisibilityConflictException extends RuntimeException {
    public TaskVisibilityConflictException(String message) {
        super(message);
    }
}
