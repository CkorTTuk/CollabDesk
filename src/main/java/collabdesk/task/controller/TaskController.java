package collabdesk.task.controller;

import collabdesk.auth.security.AuthenticatedUserPrincipal;
import collabdesk.openapi.ApiProblemResponse;
import collabdesk.task.dto.CreateTaskRequest;
import collabdesk.task.dto.TaskResponse;
import collabdesk.task.dto.UpdateTaskStatusRequest;
import collabdesk.task.dto.UpdateTaskRequest;
import collabdesk.task.dto.UpdateTaskVisibilityRequest;
import collabdesk.task.service.TaskService;
import collabdesk.task.assignee.dto.UpdateTaskAssigneeRequest;
import collabdesk.task.assignee.service.TaskAssigneeService;
import collabdesk.task.activity.dto.TaskActivityResponse;
import collabdesk.task.activity.service.TaskActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(
        "/api/v1/workspaces/{workspaceId}/projects/{projectId}/tasks"
)
@Tag(
        name = "Tasks",
        description = "Project tasks and their status workflow"
)
@SecurityRequirement(name = "sessionCookie")
public class TaskController {

    private final TaskService taskService;
    private final TaskAssigneeService taskAssigneeService;
    private final TaskActivityService taskActivityService;

    public TaskController(
            TaskService taskService,
            TaskAssigneeService taskAssigneeService,
            TaskActivityService taskActivityService
    ) {
        this.taskService = taskService;
        this.taskAssigneeService = taskAssigneeService;
        this.taskActivityService = taskActivityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create a task",
            description = "Creates a TODO task. VIEWER cannot create tasks."
    )
    @Parameter(ref = "#/components/parameters/csrfToken")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Task created"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            ),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(
                    responseCode = "403",
                    description = "Workspace role is read-only",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Workspace or project is not accessible",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            )
    })
    public TaskResponse create(
            @PathVariable Long workspaceId,
            @PathVariable Long projectId,
            @Valid @RequestBody CreateTaskRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return taskService.create(
                workspaceId,
                projectId,
                principal.getUserId(),
                request.title(),
                request.description()
        );
    }

    @GetMapping
    @Operation(
            summary = "List project tasks",
            description = "Available to every workspace member, including VIEWER"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task list"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Workspace or project is not accessible",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            )
    })
    public List<TaskResponse> findAll(
            @PathVariable Long workspaceId,
            @PathVariable Long projectId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return taskService.findForProject(
                workspaceId,
                projectId,
                principal.getUserId()
        );
    }

    @PatchMapping("/{taskId}")
    @Operation(
            summary = "Edit a task",
            description = "Updates task title and description when EDIT_TASK is effective"
    )
    @Parameter(ref = "#/components/parameters/csrfToken")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "403", description = "EDIT_TASK is required"),
            @ApiResponse(responseCode = "404", description = "Task is not accessible")
    })
    public TaskResponse edit(
            @PathVariable Long workspaceId,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return taskService.edit(
                workspaceId,
                projectId,
                taskId,
                principal.getUserId(),
                request.title(),
                request.description()
        );
    }

    @PatchMapping("/{taskId}/status")
    @Operation(
            summary = "Change task status",
            description = "Moves a task between TODO, IN_PROGRESS and DONE"
    )
    @Parameter(ref = "#/components/parameters/csrfToken")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task status changed"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            ),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(
                    responseCode = "403",
                    description = "Workspace role is read-only",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Workspace, project or task is not accessible",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            )
    })
    public TaskResponse changeStatus(
            @PathVariable Long workspaceId,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return taskService.changeStatus(
                workspaceId,
                projectId,
                taskId,
                principal.getUserId(),
                request.status()
        );
    }

    @PutMapping("/{taskId}/assignee")
    @Operation(
            summary = "Assign a project member to a task",
            description = "Sets or clears the task's single assignee. Requires workspace OWNER or ADMIN."
    )
    @Parameter(ref = "#/components/parameters/csrfToken")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assignee updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "OWNER or ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "Task or project member not found")
    })
    public TaskResponse updateAssignee(
            @PathVariable Long workspaceId,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskAssigneeRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return taskAssigneeService.assign(
                workspaceId,
                projectId,
                taskId,
                principal.getUserId(),
                request.projectMemberId()
        );
    }

    @PutMapping("/{taskId}/claim")
    @Operation(summary = "Claim a free task for the current user")
    @Parameter(ref = "#/components/parameters/csrfToken")
    public TaskResponse claim(
            @PathVariable Long workspaceId,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return taskAssigneeService.claim(
                workspaceId,
                projectId,
                taskId,
                principal.getUserId()
        );
    }

    @DeleteMapping("/{taskId}/claim")
    @Operation(summary = "Release a task claimed by the current user")
    @Parameter(ref = "#/components/parameters/csrfToken")
    public TaskResponse release(
            @PathVariable Long workspaceId,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return taskAssigneeService.release(
                workspaceId,
                projectId,
                taskId,
                principal.getUserId()
        );
    }

    @GetMapping("/{taskId}/activities")
    @Operation(summary = "List task activity, newest first")
    public List<TaskActivityResponse> activities(
            @PathVariable Long workspaceId,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return taskActivityService.findForTask(
                workspaceId,
                projectId,
                taskId,
                principal.getUserId()
        );
    }

    @PatchMapping("/{taskId}/visibility")
    @Operation(
            summary = "Change task visibility",
            description = "Switches between PROJECT and ASSIGNEES. Workspace managers or the task creator may change it."
    )
    @Parameter(ref = "#/components/parameters/csrfToken")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Visibility changed"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Operation is not allowed"),
            @ApiResponse(responseCode = "404", description = "Task is not accessible"),
            @ApiResponse(
                    responseCode = "409",
                    description = "ASSIGNEES visibility requires at least one assignee",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            )
    })
    public TaskResponse changeVisibility(
            @PathVariable Long workspaceId,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskVisibilityRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return taskService.changeVisibility(
                workspaceId,
                projectId,
                taskId,
                principal.getUserId(),
                request.visibility()
        );
    }
}
