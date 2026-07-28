package collabdesk.controller;

import collabdesk.auth.registration.EmailAlreadyExistsException;
import collabdesk.project.service.ProjectNotFoundException;
import collabdesk.projectmember.service.ProjectMemberAlreadyExistsException;
import collabdesk.projectmember.service.ProjectMemberNotFoundException;
import collabdesk.task.service.TaskNotFoundException;
import collabdesk.workspace.service.exceptions.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler{
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ProblemDetail handleEmailAlreadyExistsException(EmailAlreadyExistsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "An account with this email already exists"
        );

        problem.setTitle("Email already registered");
        return problem;
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        Map<String, String> errors =  new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });
        problem.setProperty("errors", errors);
        problem.setTitle("Validation failed");
        return problem;
    }

    @ExceptionHandler(WorkspaceAccessDeniedException.class)
    public ProblemDetail handleWorkspaceAccessDeniedException(
            WorkspaceAccessDeniedException ex
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Workspace was not found or is not accessible"
        );
        problem.setTitle("Workspace not found");
        return problem;
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    public ProblemDetail handleProjectNotFoundException(
            ProjectNotFoundException ex
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Project was not found or is not accessible"
        );
        problem.setTitle("Project not found");
        return problem;
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ProblemDetail handleTaskNotFoundException(
            TaskNotFoundException ex
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Task was not found or is not accessible"
        );
        problem.setTitle("Task not found");
        return problem;
    }

    @ExceptionHandler(ProjectMemberNotFoundException.class)
    public ProblemDetail handleProjectMemberNotFoundException(
            ProjectMemberNotFoundException ex
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Project member was not found"
        );
        problem.setTitle("Project member not found");
        return problem;
    }

    @ExceptionHandler(ProjectMemberAlreadyExistsException.class)
    public ProblemDetail handleProjectMemberAlreadyExistsException(
            ProjectMemberAlreadyExistsException ex
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Workspace member is already in this project"
        );
        problem.setTitle("Project member already exists");
        return problem;
    }
    @ExceptionHandler(WorkspaceOperationForbiddenException.class)
    public ProblemDetail handleWorkspaceOperationForbiddenException(
            WorkspaceOperationForbiddenException ex
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "Your workspace role does not allow this operation"
        );
        problem.setTitle("Workspace operation forbidden");
        return problem;
    }
    @ExceptionHandler(WorkspaceMemberNotFoundException.class)
    public ProblemDetail handleWorkspaceMemberNotFoundException(
            WorkspaceMemberNotFoundException ex
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Workspace member was not found"
        );
        problem.setTitle("Workspace member not found");
        return problem;
    }
    @ExceptionHandler(WorkspaceMemberAlreadyExistsException.class)
    public ProblemDetail handleWorkspaceMemberAlreadyExistsException(
            WorkspaceMemberAlreadyExistsException ex
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "User is already a workspace member"
        );
        problem.setTitle("Workspace member already exists");
        return problem;
    }
    @ExceptionHandler(WorkspaceUserNotFoundException.class)
    public ProblemDetail handleWorkspaceUserNotFoundException(
            WorkspaceUserNotFoundException ex
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Account with this email was not found"
        );
        problem.setTitle("Account not found");
        return problem;
    }

    @ExceptionHandler(WorkspaceOwnerMutationException.class)
    public ProblemDetail handleWorkspaceOwnerMutationException(
            WorkspaceOwnerMutationException ex
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Owner membership cannot be changed"
        );
        problem.setTitle("Workspace owner conflict");
        return problem;
    }
}
