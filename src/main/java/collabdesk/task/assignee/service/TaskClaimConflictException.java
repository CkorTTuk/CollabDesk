package collabdesk.task.assignee.service;

public class TaskClaimConflictException extends RuntimeException {
    public TaskClaimConflictException(String message) {
        super(message);
    }
}
