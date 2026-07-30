package collabdesk.task.assignee.service;

import collabdesk.project.role.entity.ProjectPermission;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

@Service
public class TaskAssigneeService {

    private final ProjectMemberRepository projectMemberRepository;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final TaskResponseMapper taskResponseMapper;
    private final WorkspaceAccessService workspaceAccessService;
    private final TaskAccessService taskAccessService;

    public TaskAssigneeService(
            ProjectMemberRepository projectMemberRepository,
            TaskAssigneeRepository taskAssigneeRepository,
            TaskResponseMapper taskResponseMapper,
            WorkspaceAccessService workspaceAccessService,
            TaskAccessService taskAccessService
    ) {
        this.projectMemberRepository = projectMemberRepository;
        this.taskAssigneeRepository = taskAssigneeRepository;
        this.taskResponseMapper = taskResponseMapper;
        this.workspaceAccessService = workspaceAccessService;
        this.taskAccessService = taskAccessService;
    }

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

        ProjectMember member = projectMemberId == null
                ? null
                : projectMemberRepository
                .findByIdAndProject_Id(projectMemberId, projectId)
                .orElseThrow(() -> new ProjectMemberNotFoundException(
                        "Project member was not found"
                ));

        if (member == null
                && task.getVisibility() == TaskVisibility.ASSIGNEES) {
            task.changeVisibility(TaskVisibility.PROJECT);
        }
        taskAssigneeRepository.deleteByTask_Id(taskId);
        taskAssigneeRepository.flush();
        TaskAssignee saved = member == null
                ? null
                : taskAssigneeRepository.save(new TaskAssignee(task, member));
        return taskResponseMapper.toResponse(
                task,
                saved,
                EnumSet.allOf(ProjectPermission.class)
        );
    }
}
