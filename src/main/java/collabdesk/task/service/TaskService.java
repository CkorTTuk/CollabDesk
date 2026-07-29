package collabdesk.task.service;

import collabdesk.project.service.AccessibleProject;
import collabdesk.project.service.ProjectAccessService;
import collabdesk.task.dto.TaskResponse;
import collabdesk.task.entity.Task;
import collabdesk.task.entity.TaskStatus;
import collabdesk.task.entity.TaskVisibility;
import collabdesk.task.repository.TaskRepository;
import collabdesk.taskassignee.repository.TaskAssigneeRepository;
import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.service.exceptions.WorkspaceOperationForbiddenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectAccessService projectAccessService;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final TaskResponseMapper taskResponseMapper;
    private final TaskAccessService taskAccessService;

    public TaskService(
            TaskRepository taskRepository,
            ProjectAccessService projectAccessService,
            TaskAssigneeRepository taskAssigneeRepository,
            TaskResponseMapper taskResponseMapper,
            TaskAccessService taskAccessService
    ) {
        this.taskRepository = taskRepository;
        this.projectAccessService = projectAccessService;
        this.taskAssigneeRepository = taskAssigneeRepository;
        this.taskResponseMapper = taskResponseMapper;
        this.taskAccessService = taskAccessService;
    }

    @Transactional
    public TaskResponse create(
            Long workspaceId,
            Long projectId,
            Long currentUserId,
            String title,
            String description
    ) {
        AccessibleProject access =
                projectAccessService.requireWritableProject(
                        workspaceId,
                        projectId,
                        currentUserId
                );

        Task task = new Task(
                access.project(),
                title,
                description,
                access.membership().getUser()
        );

        return taskResponseMapper.toResponse(
                taskRepository.save(task),
                List.of()
        );
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> findForProject(
            Long workspaceId,
            Long projectId,
            Long currentUserId
    ) {
        AccessibleProject access = projectAccessService.requireAccessibleProject(
                workspaceId,
                projectId,
                currentUserId
        );

        boolean manager = access.membership().getRole() == WorkspaceRole.OWNER
                || access.membership().getRole() == WorkspaceRole.ADMIN;
        List<Task> tasks = taskRepository.findAccessibleForProject(
                projectId,
                currentUserId,
                manager
        );
        return taskResponseMapper.toResponses(
                tasks,
                tasks.isEmpty()
                        ? List.of()
                        : taskAssigneeRepository
                        .findByTask_IdInOrderByAssignedAtAsc(
                                tasks.stream().map(Task::getId).toList()
                        )
        );
    }

    @Transactional
    public TaskResponse changeStatus(
            Long workspaceId,
            Long projectId,
            Long taskId,
            Long currentUserId,
            TaskStatus newStatus
    ) {
        AccessibleTask access = taskAccessService.requireWritableTask(
                workspaceId,
                projectId,
                taskId,
                currentUserId
        );
        Task task = access.task();

        task.changeStatus(newStatus);
        return taskResponseMapper.toResponse(
                task,
                taskAssigneeRepository
                        .findByTask_IdOrderByAssignedAtAsc(taskId)
        );
    }

    @Transactional
    public TaskResponse changeVisibility(
            Long workspaceId,
            Long projectId,
            Long taskId,
            Long currentUserId,
            TaskVisibility visibility
    ) {
        AccessibleTask access = taskAccessService.requireAccessibleTask(
                workspaceId,
                projectId,
                taskId,
                currentUserId
        );
        Task task = access.task();
        WorkspaceRole role = access.projectAccess().membership().getRole();
        if (role == WorkspaceRole.VIEWER) {
            throw new WorkspaceOperationForbiddenException(
                    "Viewer has read-only access"
            );
        }
        boolean manager = role == WorkspaceRole.OWNER
                || role == WorkspaceRole.ADMIN;
        if (!manager && !task.getCreatedBy().getId().equals(currentUserId)) {
            throw new WorkspaceOperationForbiddenException(
                    "Only workspace managers or the task creator can change visibility"
            );
        }
        if (visibility == TaskVisibility.ASSIGNEES
                && !taskAssigneeRepository.existsByTask_Id(taskId)) {
            throw new TaskVisibilityConflictException(
                    "Assign at least one project member before restricting the task"
            );
        }

        task.changeVisibility(visibility);
        return taskResponseMapper.toResponse(
                task,
                taskAssigneeRepository
                        .findByTask_IdOrderByAssignedAtAsc(taskId)
        );
    }
}
