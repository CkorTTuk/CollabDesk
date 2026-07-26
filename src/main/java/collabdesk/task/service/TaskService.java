package collabdesk.task.service;

import collabdesk.project.service.AccessibleProject;
import collabdesk.project.service.ProjectAccessService;
import collabdesk.task.dto.TaskResponse;
import collabdesk.task.entity.Task;
import collabdesk.task.entity.TaskStatus;
import collabdesk.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectAccessService projectAccessService;

    public TaskService(
            TaskRepository taskRepository,
            ProjectAccessService projectAccessService
    ) {
        this.taskRepository = taskRepository;
        this.projectAccessService = projectAccessService;
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

        return toResponse(taskRepository.save(task));
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

        return taskRepository
                .findByProject_IdOrderByCreatedAtAsc(projectId)
                .stream()
                .map(this::toResponse)
                .toList();
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
        return toResponse(task);
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getProject().getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getCreatedBy().getId(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
