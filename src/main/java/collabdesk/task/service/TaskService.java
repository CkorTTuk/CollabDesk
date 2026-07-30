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

    public TaskService(
            TaskRepository taskRepository,
            ProjectAccessService projectAccessService,
            TaskAssigneeRepository taskAssigneeRepository,
            TaskResponseMapper taskResponseMapper,
            TaskAccessService taskAccessService,
            ProjectPermissionService projectPermissionService
    ) {
        this.taskRepository = taskRepository;
        this.projectAccessService = projectAccessService;
        this.taskAssigneeRepository = taskAssigneeRepository;
        this.taskResponseMapper = taskResponseMapper;
        this.taskAccessService = taskAccessService;
        this.projectPermissionService = projectPermissionService;
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
        projectPermissionService.requireProjectPermission(
                access,
                currentUserId,
                ProjectPermission.CREATE_TASK
        );

        Task task = new Task(
                access.project(),
                title,
                description,
                access.membership().getUser()
        );

        return taskResponseMapper.toResponse(
                taskRepository.save(task),
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
        AccessibleTask access = taskAccessService.requireAccessibleTask(
                workspaceId,
                projectId,
                taskId,
                currentUserId
        );
        projectPermissionService.requireTaskPermission(
                access,
                currentUserId,
                ProjectPermission.EDIT_TASK
        );
        access.task().edit(title, description);
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
        AccessibleTask access = taskAccessService.requireAccessibleTask(
                workspaceId,
                projectId,
                taskId,
                currentUserId
        );
        projectPermissionService.requireTaskPermission(
                access,
                currentUserId,
                ProjectPermission.CHANGE_TASK_STATUS
        );
        WorkspaceRole workspaceRole =
                access.projectAccess().membership().getRole();
        boolean manager = workspaceRole == WorkspaceRole.OWNER
                || workspaceRole == WorkspaceRole.ADMIN;
        if (!manager && !taskAssigneeRepository
                .existsByTask_IdAndProjectMember_WorkspaceMember_User_Id(
                        taskId,
                        currentUserId
                )) {
            throw new WorkspaceOperationForbiddenException(
                    "Only the assigned project member can change task status"
            );
        }
        Task task = access.task();

        task.changeStatus(newStatus);
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
        AccessibleTask access = taskAccessService.requireAccessibleTask(
                workspaceId,
                projectId,
                taskId,
                currentUserId
        );
        Task task = access.task();
        projectPermissionService.requireTaskPermission(
                access,
                currentUserId,
                ProjectPermission.CHANGE_TASK_VISIBILITY
        );
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
                        .findByTask_Id(taskId)
                        .orElse(null),
                projectPermissionService.findEffectiveTaskPermissions(
                        access,
                        currentUserId
                )
        );
    }
}
