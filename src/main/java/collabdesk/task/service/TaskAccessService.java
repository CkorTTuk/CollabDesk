package collabdesk.task.service;

import collabdesk.project.service.AccessibleProject;
import collabdesk.project.service.ProjectAccessService;
import collabdesk.task.entity.Task;
import collabdesk.task.entity.TaskVisibility;
import collabdesk.task.repository.TaskRepository;
import collabdesk.taskassignee.repository.TaskAssigneeRepository;
import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.service.exceptions.WorkspaceOperationForbiddenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskAccessService {

    private final ProjectAccessService projectAccessService;
    private final TaskRepository taskRepository;
    private final TaskAssigneeRepository taskAssigneeRepository;

    public TaskAccessService(
            ProjectAccessService projectAccessService,
            TaskRepository taskRepository,
            TaskAssigneeRepository taskAssigneeRepository
    ) {
        this.projectAccessService = projectAccessService;
        this.taskRepository = taskRepository;
        this.taskAssigneeRepository = taskAssigneeRepository;
    }

    @Transactional(readOnly = true)
    public AccessibleTask requireAccessibleTask(
            Long workspaceId,
            Long projectId,
            Long taskId,
            Long currentUserId
    ) {
        AccessibleProject projectAccess =
                projectAccessService.requireAccessibleProject(
                        workspaceId,
                        projectId,
                        currentUserId
                );
        Task task = taskRepository.findByIdAndProject_Id(taskId, projectId)
                .orElseThrow(() -> new TaskNotFoundException(
                        "Task was not found"
                ));

        WorkspaceRole role = projectAccess.membership().getRole();
        if (task.getVisibility() == TaskVisibility.PROJECT
                || role == WorkspaceRole.OWNER
                || role == WorkspaceRole.ADMIN
                || task.getCreatedBy().getId().equals(currentUserId)
                || taskAssigneeRepository
                .existsByTask_IdAndProjectMember_WorkspaceMember_User_Id(
                        taskId,
                        currentUserId
                )) {
            return new AccessibleTask(task, projectAccess);
        }

        throw new TaskNotFoundException(
                "Task was not found or is not accessible"
        );
    }

    @Transactional(readOnly = true)
    public AccessibleTask requireWritableTask(
            Long workspaceId,
            Long projectId,
            Long taskId,
            Long currentUserId
    ) {
        AccessibleTask access = requireAccessibleTask(
                workspaceId,
                projectId,
                taskId,
                currentUserId
        );
        if (access.projectAccess().membership().getRole()
                == WorkspaceRole.VIEWER) {
            throw new WorkspaceOperationForbiddenException(
                    "Viewer has read-only access"
            );
        }
        return access;
    }
}
