package collabdesk.controller;

import collabdesk.auth.security.AuthenticatedUserPrincipal;
import collabdesk.task.dto.CreateTaskRequest;
import collabdesk.task.dto.TaskResponse;
import collabdesk.task.dto.UpdateTaskStatusRequest;
import collabdesk.task.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(
        "/api/v1/workspaces/{workspaceId}/projects/{projectId}/tasks"
)
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
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

    @PatchMapping("/{taskId}/status")
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
}
