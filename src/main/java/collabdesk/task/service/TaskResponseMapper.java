package collabdesk.task.service;

import collabdesk.account.AvatarUrlFactory;
import collabdesk.project.role.entity.ProjectPermission;
import collabdesk.task.dto.TaskResponse;
import collabdesk.task.entity.Task;
import collabdesk.task.assignee.dto.TaskAssigneeResponse;
import collabdesk.task.assignee.entity.TaskAssignee;
import collabdesk.workspace.member.entity.WorkspaceMember;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Maps task entities and related assignment data to API-safe responses. */
@Component
public class TaskResponseMapper {
    private final AvatarUrlFactory avatarUrlFactory;

    public TaskResponseMapper(AvatarUrlFactory avatarUrlFactory) {
        this.avatarUrlFactory = avatarUrlFactory;
    }

    public TaskResponse toResponse(
            Task task,
            TaskAssignee assignment,
            Set<ProjectPermission> currentUserPermissions
    ) {
        return new TaskResponse(
                task.getId(),
                task.getProject().getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getVisibility(),
                task.getCreatedBy().getId(),
                task.getCreatedBy().getDisplayName(),
                task.getCreatedBy().getEmail(),
                avatarUrlFactory.create(task.getCreatedBy().getAvatarKey()),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                assignment == null ? null : toAssigneeResponse(assignment),
                Set.copyOf(currentUserPermissions)
        );
    }

    public List<TaskResponse> toResponses(
            List<Task> tasks,
            List<TaskAssignee> assignments,
            Set<ProjectPermission> currentUserPermissions
    ) {
        Map<Long, TaskAssignee> byTask = assignments.stream()
                .collect(Collectors.toMap(
                        assignment -> assignment.getTask().getId(),
                        assignment -> assignment
                ));
        return tasks.stream()
                .map(task -> toResponse(
                        task,
                        byTask.get(task.getId()),
                        currentUserPermissions
                ))
                .toList();
    }

    private TaskAssigneeResponse toAssigneeResponse(TaskAssignee assignment) {
        WorkspaceMember member =
                assignment.getProjectMember().getWorkspaceMember();
        return new TaskAssigneeResponse(
                assignment.getProjectMember().getId(),
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getUser().getDisplayName(),
                avatarUrlFactory.create(member.getUser().getAvatarKey())
        );
    }
}
