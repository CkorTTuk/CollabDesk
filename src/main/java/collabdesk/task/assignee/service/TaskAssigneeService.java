package collabdesk.task.assignee.service;

import collabdesk.project.role.entity.ProjectPermission;
import collabdesk.project.role.service.ProjectPermissionService;
import collabdesk.project.member.entity.ProjectMember;
import collabdesk.project.member.repository.ProjectMemberRepository;
import collabdesk.project.member.service.ProjectMemberNotFoundException;
import collabdesk.task.dto.TaskResponse;
import collabdesk.task.entity.Task;
import collabdesk.task.entity.TaskVisibility;
import collabdesk.task.service.AccessibleTask;
import collabdesk.task.service.TaskAccessService;
import collabdesk.task.service.TaskResponseMapper;
import collabdesk.task.assignee.entity.TaskAssignee;
import collabdesk.task.assignee.repository.TaskAssigneeRepository;
import collabdesk.workspace.service.WorkspaceAccessService;
import collabdesk.task.activity.entity.TaskActivityType;
import collabdesk.task.activity.service.TaskActivityService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assigns, claims and releases a task's single assignee. Permission and race
 * checks keep concurrent claims from silently overwriting each other.
 */
@Service
public class TaskAssigneeService {

    private final ProjectMemberRepository projectMemberRepository;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final TaskResponseMapper taskResponseMapper;
    private final WorkspaceAccessService workspaceAccessService;
    private final TaskAccessService taskAccessService;
    private final TaskActivityService taskActivityService;
    private final ProjectPermissionService projectPermissionService;

    public TaskAssigneeService(
            ProjectMemberRepository projectMemberRepository,
            TaskAssigneeRepository taskAssigneeRepository,
            TaskResponseMapper taskResponseMapper,
            WorkspaceAccessService workspaceAccessService,
            TaskAccessService taskAccessService,
            TaskActivityService taskActivityService,
            ProjectPermissionService projectPermissionService
    ) {
        this.projectMemberRepository = projectMemberRepository;
        this.taskAssigneeRepository = taskAssigneeRepository;
        this.taskResponseMapper = taskResponseMapper;
        this.workspaceAccessService = workspaceAccessService;
        this.taskAccessService = taskAccessService;
        this.taskActivityService = taskActivityService;
        this.projectPermissionService = projectPermissionService;
    }

    /** Assigns or clears the task's assignee after management checks. */
    @Transactional
    public TaskResponse assign(
            Long workspaceId,
            Long projectId,
            Long taskId,
            Long currentUserId,
            Long projectMemberId
    ) {
        workspaceAccessService.requireManager(workspaceId, currentUserId);
        AccessibleTask access = taskAccessService.requireAccessibleTask(
                workspaceId,
                projectId,
                taskId,
                currentUserId
        );
        Task task = access.task();
        TaskAssignee oldAssignment = taskAssigneeRepository.findByTask_Id(taskId).orElse(null);

        ProjectMember member = projectMemberId == null
                ? null
                : projectMemberRepository
                .findByIdAndProject_Id(projectMemberId, projectId)
                .orElseThrow(() -> new ProjectMemberNotFoundException(
                        "Project member was not found"
                ));

        if (oldAssignment != null && member != null
                && oldAssignment.getProjectMember().getId().equals(member.getId())) {
            return response(access, oldAssignment);
        }

        TaskVisibility oldVisibility = task.getVisibility();
        if (member == null
                && task.getVisibility() == TaskVisibility.ASSIGNEES) {
            task.changeVisibility(TaskVisibility.PROJECT);
        }
        taskAssigneeRepository.deleteByTask_Id(taskId);
        taskAssigneeRepository.flush();
        TaskAssignee saved = member == null
                ? null
                : taskAssigneeRepository.save(new TaskAssignee(task, member));
        taskActivityService.record(
                task,
                access.projectAccess().membership().getUser(),
                TaskActivityType.ASSIGNEE_CHANGED,
                oldAssignment == null ? null : oldAssignment.getProjectMember()
                        .getWorkspaceMember().getUser().getDisplayName(),
                member == null ? null : member.getWorkspaceMember().getUser().getDisplayName()
        );
        if (oldVisibility != task.getVisibility()) {
            taskActivityService.record(
                    task,
                    access.projectAccess().membership().getUser(),
                    TaskActivityType.VISIBILITY_CHANGED,
                    oldVisibility.name(),
                    task.getVisibility().name()
            );
        }
        return response(access, saved);
    }

    /** Lets the caller claim an unassigned task without overwriting another claim. */
    @Transactional
    public TaskResponse claim(
            Long workspaceId,
            Long projectId,
            Long taskId,
            Long currentUserId
    ) {
        AccessibleTask access = taskAccessService.requireWritableTask(
                workspaceId,
                projectId,
                taskId,
                currentUserId
        );
        if (taskAssigneeRepository.existsByTask_Id(taskId)) {
            throw new TaskClaimConflictException("Task has already been claimed");
        }
        ProjectMember member = projectMemberRepository
                .findByProject_IdAndWorkspaceMember_User_Id(projectId, currentUserId)
                .orElseGet(() -> projectMemberRepository.saveAndFlush(
                        new ProjectMember(
                                access.task().getProject(),
                                access.projectAccess().membership(),
                                false
                        )
                ));
        try {
            TaskAssignee assignment = taskAssigneeRepository.saveAndFlush(
                    new TaskAssignee(access.task(), member)
            );
            String name = member.getWorkspaceMember().getUser().getDisplayName();
            taskActivityService.record(
                    access.task(),
                    access.projectAccess().membership().getUser(),
                    TaskActivityType.CLAIMED,
                    null,
                    name
            );
            return response(access, assignment);
        } catch (DataIntegrityViolationException ex) {
            throw new TaskClaimConflictException("Task has already been claimed");
        }
    }

    /** Releases the caller's own assignment while preserving concurrent changes. */
    @Transactional
    public TaskResponse release(
            Long workspaceId,
            Long projectId,
            Long taskId,
            Long currentUserId
    ) {
        AccessibleTask access = taskAccessService.requireWritableTask(
                workspaceId,
                projectId,
                taskId,
                currentUserId
        );
        TaskAssignee assignment = taskAssigneeRepository.findByTask_Id(taskId)
                .filter(item -> item.getProjectMember().getWorkspaceMember()
                        .getUser().getId().equals(currentUserId))
                .orElseThrow(() -> new collabdesk.workspace.service.exceptions.WorkspaceOperationForbiddenException(
                        "Only the current assignee can release the task"
                ));
        String oldName = assignment.getProjectMember().getWorkspaceMember()
                .getUser().getDisplayName();
        taskAssigneeRepository.delete(assignment);
        taskAssigneeRepository.flush();
        Task task = access.task();
        if (task.getVisibility() == TaskVisibility.ASSIGNEES) {
            task.changeVisibility(TaskVisibility.PROJECT);
            taskActivityService.record(
                    task,
                    access.projectAccess().membership().getUser(),
                    TaskActivityType.VISIBILITY_CHANGED,
                    TaskVisibility.ASSIGNEES.name(),
                    TaskVisibility.PROJECT.name()
            );
        }
        taskActivityService.record(
                task,
                access.projectAccess().membership().getUser(),
                TaskActivityType.RELEASED,
                oldName,
                null
        );
        return response(access, null);
    }

    private TaskResponse response(AccessibleTask access, TaskAssignee assignment) {
        return taskResponseMapper.toResponse(
                access.task(),
                assignment,
                projectPermissionService.findEffectiveTaskPermissions(
                        access,
                        access.projectAccess().membership().getUser().getId()
                )
        );
    }
}
