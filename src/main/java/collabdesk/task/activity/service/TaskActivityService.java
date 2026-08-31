package collabdesk.task.activity.service;

import collabdesk.task.activity.dto.TaskActivityResponse;
import collabdesk.task.activity.entity.TaskActivity;
import collabdesk.task.activity.entity.TaskActivityType;
import collabdesk.task.activity.repository.TaskActivityRepository;
import collabdesk.task.entity.Task;
import collabdesk.task.service.TaskAccessService;
import collabdesk.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskActivityService {
    private final TaskActivityRepository repository;
    private final TaskAccessService taskAccessService;

    public TaskActivityService(
            TaskActivityRepository repository,
            TaskAccessService taskAccessService
    ) {
        this.repository = repository;
        this.taskAccessService = taskAccessService;
    }

    public void record(
            Task task,
            User actor,
            TaskActivityType type,
            String oldValue,
            String newValue
    ) {
        repository.save(new TaskActivity(task, actor, type, oldValue, newValue));
    }

    @Transactional(readOnly = true)
    public List<TaskActivityResponse> findForTask(
            Long workspaceId,
            Long projectId,
            Long taskId,
            Long currentUserId
    ) {
        taskAccessService.requireManageableTask(
                workspaceId,
                projectId,
                taskId,
                currentUserId
        );
        return repository.findByTask_IdOrderByCreatedAtDescIdDesc(taskId)
                .stream()
                .map(activity -> new TaskActivityResponse(
                        activity.getId(),
                        activity.getType(),
                        activity.getActor().getId(),
                        activity.getActor().getDisplayName(),
                        activity.getOldValue(),
                        activity.getNewValue(),
                        activity.getCreatedAt()
                ))
                .toList();
    }
}
