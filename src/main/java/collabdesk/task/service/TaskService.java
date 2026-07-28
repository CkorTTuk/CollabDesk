package collabdesk.task.service;

import collabdesk.project.service.AccessibleProject;
import collabdesk.project.service.ProjectAccessService;
import collabdesk.task.dto.TaskResponse;
import collabdesk.task.entity.Task;
import collabdesk.task.entity.TaskStatus;
import collabdesk.task.repository.TaskRepository;
import collabdesk.taskassignee.repository.TaskAssigneeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectAccessService projectAccessService;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final TaskResponseMapper taskResponseMapper;

    public TaskService(
            TaskRepository taskRepository,
            ProjectAccessService projectAccessService,
            TaskAssigneeRepository taskAssigneeRepository,
            TaskResponseMapper taskResponseMapper
    ) {
        this.taskRepository = taskRepository;
        this.projectAccessService = projectAccessService;
        this.taskAssigneeRepository = taskAssigneeRepository;
        this.taskResponseMapper = taskResponseMapper;
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
        projectAccessService.requireAccessibleProject(
                workspaceId,
                projectId,
                currentUserId
        );

        return taskResponseMapper.toResponses(
                taskRepository.findByProject_IdOrderByCreatedAtAsc(projectId),
                taskAssigneeRepository
                        .findByTask_Project_IdOrderByAssignedAtAsc(projectId)
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
        projectAccessService.requireWritableProject(
                workspaceId,
                projectId,
                currentUserId
        );

        Task task = taskRepository
                .findByIdAndProject_Id(taskId, projectId)
                .orElseThrow(() -> new TaskNotFoundException(
                        "Task was not found"
                ));

        task.changeStatus(newStatus);
        return taskResponseMapper.toResponse(
                task,
                taskAssigneeRepository
                        .findByTask_IdOrderByAssignedAtAsc(taskId)
        );
    }
}
