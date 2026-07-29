package collabdesk.taskassignee.service;

import collabdesk.projectmember.entity.ProjectMember;
import collabdesk.projectmember.repository.ProjectMemberRepository;
import collabdesk.projectmember.service.ProjectMemberNotFoundException;
import collabdesk.task.dto.TaskResponse;
import collabdesk.task.entity.Task;
import collabdesk.task.entity.TaskVisibility;
import collabdesk.task.service.AccessibleTask;
import collabdesk.task.service.TaskAccessService;
import collabdesk.task.service.TaskResponseMapper;
import collabdesk.taskassignee.entity.TaskAssignee;
import collabdesk.taskassignee.repository.TaskAssigneeRepository;
import collabdesk.workspace.service.WorkspaceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

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
    public TaskResponse replace(
            Long workspaceId,
            Long projectId,
            Long taskId,
            Long currentUserId,
            Set<Long> projectMemberIds
    ) {
        workspaceAccessService.requireManager(workspaceId, currentUserId);
        AccessibleTask access = taskAccessService.requireAccessibleTask(
                workspaceId,
                projectId,
                taskId,
                currentUserId
        );
        Task task = access.task();

        List<ProjectMember> members = projectMemberRepository
                .findAllByProject_IdAndIdIn(projectId, projectMemberIds);
        if (members.size() != projectMemberIds.size()) {
            throw new ProjectMemberNotFoundException(
                    "At least one project member was not found"
            );
        }
        members.sort(Comparator.comparing(ProjectMember::getId));

        if (members.isEmpty()
                && task.getVisibility() == TaskVisibility.ASSIGNEES) {
            task.changeVisibility(TaskVisibility.PROJECT);
        }
        taskAssigneeRepository.deleteByTask_Id(taskId);
        taskAssigneeRepository.flush();
        List<TaskAssignee> saved = taskAssigneeRepository.saveAll(
                members.stream()
                        .map(member -> new TaskAssignee(task, member))
                        .toList()
        );
        return taskResponseMapper.toResponse(task, saved);
    }
}
