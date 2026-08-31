package collabdesk.common.web;

import collabdesk.project.role.service.*;
import collabdesk.auth.registration.EmailAlreadyExistsException;
import collabdesk.project.service.ProjectNotFoundException;
import collabdesk.project.member.service.ProjectMemberAlreadyExistsException;
import collabdesk.project.member.service.ProjectMemberNotFoundException;
import collabdesk.task.service.TaskNotFoundException;
import collabdesk.task.service.TaskVisibilityConflictException;
import collabdesk.task.assignee.service.TaskClaimConflictException;
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
    @ExceptionHandler(TaskClaimConflictException.class)
    public ProblemDetail handleTaskClaimConflict(TaskClaimConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problem.setTitle("Task claim conflict");
        return problem;
    }
    @ExceptionHandler(AccessRoleNotFoundException.class)
    public ProblemDetail handleAccessRoleNotFound(
            AccessRoleNotFoundException ex
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Access role was not found"
        );
        problem.setTitle("Access role not found");
        return problem;
    }

    @ExceptionHandler(AccessRoleAlreadyExistsException.class)
    public ProblemDetail handleAccessRoleAlreadyExists(
            AccessRoleAlreadyExistsException ex
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "A role with this name already exists in the workspace"
        );
        problem.setTitle("Access role already exists");
        return problem;
    }

    @ExceptionHandler(AccessRoleInUseException.class)
    public ProblemDetail handleAccessRoleInUse(AccessRoleInUseException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "The role is assigned to at least one project member"
        );
        problem.setTitle("Access role is in use");
        return problem;
    }

    @ExceptionHandler(AccessRoleWorkspaceMismatchException.class)
    public ProblemDetail handleAccessRoleWorkspaceMismatch(
            AccessRoleWorkspaceMismatchException ex
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "At least one role was not found in this workspace"
        );
        problem.setTitle("Access role not found");
        return problem;
    }

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

    @ExceptionHandler(TaskVisibilityConflictException.class)
    public ProblemDetail handleTaskVisibilityConflictException(
            TaskVisibilityConflictException ex
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problem.setTitle("Task visibility conflict");
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
