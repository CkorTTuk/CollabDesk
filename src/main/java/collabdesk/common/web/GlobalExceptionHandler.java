package collabdesk.common.web;

import collabdesk.account.onboarding.OnboardingAccountUnavailableException;
import collabdesk.auth.registration.EmailAlreadyExistsException;
import collabdesk.auth.registration.PasswordsDoNotMatchException;
import collabdesk.project.member.service.ProjectMemberAlreadyExistsException;
import collabdesk.project.member.service.ProjectMemberNotFoundException;
import collabdesk.project.role.service.AccessRoleAlreadyExistsException;
import collabdesk.project.role.service.AccessRoleInUseException;
import collabdesk.project.role.service.AccessRoleNotFoundException;
import collabdesk.project.role.service.AccessRoleWorkspaceMismatchException;
import collabdesk.project.service.ProjectNotFoundException;
import collabdesk.task.assignee.service.TaskClaimConflictException;
import collabdesk.task.service.TaskNotFoundException;
import collabdesk.task.service.TaskVisibilityConflictException;
import collabdesk.workspace.service.exceptions.WorkspaceAccessDeniedException;
import collabdesk.workspace.service.exceptions.WorkspaceMemberAlreadyExistsException;
import collabdesk.workspace.service.exceptions.WorkspaceMemberNotFoundException;
import collabdesk.workspace.service.exceptions.WorkspaceOperationForbiddenException;
import collabdesk.workspace.service.exceptions.WorkspaceOwnerMutationException;
import collabdesk.workspace.service.exceptions.WorkspaceUserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(PasswordsDoNotMatchException.class)
    public ProblemDetail handlePasswordsDoNotMatch() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setProperty("code", "passwords_do_not_match");
        problem.setProperty(
                "errors",
                Map.of("passwordConfirmation", "Passwords do not match")
        );
        return problem;
    }

    @ExceptionHandler(OnboardingAccountUnavailableException.class)
    public ProblemDetail handleOnboardingAccountUnavailable() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "Account is unavailable"
        );
        problem.setTitle("Account unavailable");
        return problem;
    }

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
    public ProblemDetail handleAccessRoleNotFound() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Access role was not found"
        );
        problem.setTitle("Access role not found");
        return problem;
    }

    @ExceptionHandler(AccessRoleAlreadyExistsException.class)
    public ProblemDetail handleAccessRoleAlreadyExists() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "A role with this name already exists in the workspace"
        );
        problem.setTitle("Access role already exists");
        return problem;
    }

    @ExceptionHandler(AccessRoleInUseException.class)
    public ProblemDetail handleAccessRoleInUse() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "The role is assigned to at least one project member"
        );
        problem.setTitle("Access role is in use");
        return problem;
    }

    @ExceptionHandler(AccessRoleWorkspaceMismatchException.class)
    public ProblemDetail handleAccessRoleWorkspaceMismatch() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "At least one role was not found in this workspace"
        );
        problem.setTitle("Access role not found");
        return problem;
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ProblemDetail handleEmailAlreadyExistsException() {
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
    public ProblemDetail handleWorkspaceAccessDeniedException() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Workspace was not found or is not accessible"
        );
        problem.setTitle("Workspace not found");
        return problem;
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    public ProblemDetail handleProjectNotFoundException() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Project was not found or is not accessible"
        );
        problem.setTitle("Project not found");
        return problem;
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ProblemDetail handleTaskNotFoundException() {
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
    public ProblemDetail handleProjectMemberNotFoundException() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Project member was not found"
        );
        problem.setTitle("Project member not found");
        return problem;
    }

    @ExceptionHandler(ProjectMemberAlreadyExistsException.class)
    public ProblemDetail handleProjectMemberAlreadyExistsException() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Workspace member is already in this project"
        );
        problem.setTitle("Project member already exists");
        return problem;
    }
    @ExceptionHandler(WorkspaceOperationForbiddenException.class)
    public ProblemDetail handleWorkspaceOperationForbiddenException() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "Your workspace role does not allow this operation"
        );
        problem.setTitle("Workspace operation forbidden");
        return problem;
    }
    @ExceptionHandler(WorkspaceMemberNotFoundException.class)
    public ProblemDetail handleWorkspaceMemberNotFoundException() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Workspace member was not found"
        );
        problem.setTitle("Workspace member not found");
        return problem;
    }
    @ExceptionHandler(WorkspaceMemberAlreadyExistsException.class)
    public ProblemDetail handleWorkspaceMemberAlreadyExistsException() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "User is already a workspace member"
        );
        problem.setTitle("Workspace member already exists");
        return problem;
    }
    @ExceptionHandler(WorkspaceUserNotFoundException.class)
    public ProblemDetail handleWorkspaceUserNotFoundException() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Account with this email was not found"
        );
        problem.setTitle("Account not found");
        return problem;
    }

    @ExceptionHandler(WorkspaceOwnerMutationException.class)
    public ProblemDetail handleWorkspaceOwnerMutationException() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Owner membership cannot be changed"
        );
        problem.setTitle("Workspace owner conflict");
        return problem;
    }
}
