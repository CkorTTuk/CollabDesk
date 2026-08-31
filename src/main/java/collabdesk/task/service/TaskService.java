package collabdesk.task.service;

import collabdesk.project.role.entity.ProjectPermission;
import collabdesk.project.role.service.ProjectPermissionService;
import collabdesk.project.service.AccessibleProject;
import collabdesk.project.service.ProjectAccessService;
import collabdesk.task.dto.TaskResponse;
import collabdesk.task.entity.Task;
import collabdesk.task.entity.TaskStatus;
import collabdesk.task.entity.TaskVisibility;
import collabdesk.task.repository.TaskRepository;
import collabdesk.task.assignee.repository.TaskAssigneeRepository;
import collabdesk.task.activity.entity.TaskActivityType;
import collabdesk.task.activity.service.TaskActivityService;
import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.service.exceptions.WorkspaceOperationForbiddenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectAccessService projectAccessService;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final TaskResponseMapper taskResponseMapper;
    private final TaskAccessService taskAccessService;
    private final ProjectPermissionService projectPermissionService;
    private final TaskActivityService taskActivityService;

    public TaskService(
            TaskRepository taskRepository,
            ProjectAccessService projectAccessService,
            TaskAssigneeRepository taskAssigneeRepository,
            TaskResponseMapper taskResponseMapper,
            TaskAccessService taskAccessService,
            ProjectPermissionService projectPermissionService,
            TaskActivityService taskActivityService
    ) {
        this.taskRepository = taskRepository;
        this.projectAccessService = projectAccessService;
        this.taskAssigneeRepository = taskAssigneeRepository;
        this.taskResponseMapper = taskResponseMapper;
        this.taskAccessService = taskAccessService;
        this.projectPermissionService = projectPermissionService;
        this.taskActivityService = taskActivityService;
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
                projectAccessService.requireAccessibleProject(
                        workspaceId,
                        projectId,
                        currentUserId
                );
        requireTaskContributor(access);

        Task task = new Task(
                access.project(),
                title,
                description,
                access.membership().getUser()
        );

        Task saved = taskRepository.save(task);
        taskActivityService.record(
                saved,
                access.membership().getUser(),
                TaskActivityType.CREATED,
                null,
                TaskStatus.TODO.name()
        );
        return taskResponseMapper.toResponse(
                saved,
                null,
                projectPermissionService.findEffectiveProjectPermissions(
                        access,
                        currentUserId
                )
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
        Set<ProjectPermission> permissions =
                projectPermissionService.findEffectiveProjectPermissions(
                        access,
                        currentUserId
                );
        return taskResponseMapper.toResponses(
                tasks,
                tasks.isEmpty()
                        ? List.of()
                        : taskAssigneeRepository
                        .findByTask_IdIn(
                                tasks.stream().map(Task::getId).toList()
                        )
                ,
                permissions
        );
    }

    @Transactional
    public TaskResponse edit(
            Long workspaceId,
            Long projectId,
            Long taskId,
            Long currentUserId,
            String title,
            String description
    ) {
        AccessibleTask access = taskAccessService.requireManageableTask(
                workspaceId,
                projectId,
                taskId,
                currentUserId
        );
        boolean changed = !java.util.Objects.equals(access.task().getTitle(), title == null ? null : title.trim())
                || !java.util.Objects.equals(
                        access.task().getDescription(),
                        description == null || description.trim().isEmpty() ? null : description.trim()
                );
        access.task().edit(title, description);
        if (changed) {
            taskActivityService.record(
                    access.task(),
                    access.projectAccess().membership().getUser(),
                    TaskActivityType.EDITED,
                    null,
                    null
            );
        }
        return taskResponseMapper.toResponse(
                access.task(),
                taskAssigneeRepository
                        .findByTask_Id(taskId)
                        .orElse(null),
                projectPermissionService.findEffectiveTaskPermissions(
                        access,
                        currentUserId
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
        AccessibleTask access = taskAccessService.requireManageableTask(
                workspaceId,
                projectId,
                taskId,
                currentUserId
        );
        Task task = access.task();
        TaskStatus oldStatus = task.getStatus();
        if (oldStatus != newStatus) {
            task.changeStatus(newStatus);
            taskActivityService.record(
                    task,
                    access.projectAccess().membership().getUser(),
                    TaskActivityType.STATUS_CHANGED,
                    oldStatus.name(),
                    newStatus.name()
            );
        }
        return taskResponseMapper.toResponse(
                task,
                taskAssigneeRepository
                        .findByTask_Id(taskId)
                        .orElse(null),
                projectPermissionService.findEffectiveTaskPermissions(
                        access,
                        currentUserId
                )
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
        AccessibleTask access = taskAccessService.requireManageableTask(
                workspaceId,
                projectId,
                taskId,
                currentUserId
        );
        Task task = access.task();
        if (visibility == TaskVisibility.ASSIGNEES
                && !taskAssigneeRepository.existsByTask_Id(taskId)) {
            throw new TaskVisibilityConflictException(
                    "Assign at least one project member before restricting the task"
            );
        }

        TaskVisibility oldVisibility = task.getVisibility();
        if (oldVisibility != visibility) {
            task.changeVisibility(visibility);
            taskActivityService.record(
                    task,
                    access.projectAccess().membership().getUser(),
                    TaskActivityType.VISIBILITY_CHANGED,
                    oldVisibility.name(),
                    visibility.name()
            );
        }
        return taskResponseMapper.toResponse(
                task,
                taskAssigneeRepository
                        .findByTask_Id(taskId)
                        .orElse(null),
                projectPermissionService.findEffectiveTaskPermissions(
                        access,
                        currentUserId
                )
        );
    }

    private void requireTaskContributor(AccessibleProject access) {
        if (access.membership().getRole() == WorkspaceRole.VIEWER) {
            throw new WorkspaceOperationForbiddenException(
                    "Viewer has read-only access"
            );
        }
    }
}
