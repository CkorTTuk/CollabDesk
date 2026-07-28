package collabdesk.taskassignee.service;

import collabdesk.project.service.ProjectAccessService;
import collabdesk.projectmember.entity.ProjectMember;
import collabdesk.projectmember.repository.ProjectMemberRepository;
import collabdesk.projectmember.service.ProjectMemberNotFoundException;
import collabdesk.task.dto.TaskResponse;
import collabdesk.task.entity.Task;
import collabdesk.task.repository.TaskRepository;
import collabdesk.task.service.TaskNotFoundException;
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

    private final TaskRepository taskRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final ProjectAccessService projectAccessService;
    private final TaskResponseMapper taskResponseMapper;
    private final WorkspaceAccessService workspaceAccessService;

    public TaskAssigneeService(
            TaskRepository taskRepository,
            ProjectMemberRepository projectMemberRepository,
            TaskAssigneeRepository taskAssigneeRepository,
            ProjectAccessService projectAccessService,
            TaskResponseMapper taskResponseMapper,
            WorkspaceAccessService workspaceAccessService
    ) {
        this.taskRepository = taskRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.taskAssigneeRepository = taskAssigneeRepository;
        this.projectAccessService = projectAccessService;
        this.taskResponseMapper = taskResponseMapper;
        this.workspaceAccessService = workspaceAccessService;
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
        projectAccessService.requireAccessibleProject(
                workspaceId,
                projectId,
                currentUserId
        );
        Task task = taskRepository.findByIdAndProject_Id(taskId, projectId)
                .orElseThrow(() -> new TaskNotFoundException(
                        "Task was not found"
                ));

        List<ProjectMember> members = projectMemberRepository
                .findAllByProject_IdAndIdIn(projectId, projectMemberIds);
        if (members.size() != projectMemberIds.size()) {
            throw new ProjectMemberNotFoundException(
                    "At least one project member was not found"
            );
        }
        members.sort(Comparator.comparing(ProjectMember::getId));

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
