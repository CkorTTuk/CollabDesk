package collabdesk.task.service;

import collabdesk.task.dto.TaskResponse;
import collabdesk.task.entity.Task;
import collabdesk.taskassignee.dto.TaskAssigneeResponse;
import collabdesk.taskassignee.entity.TaskAssignee;
import collabdesk.workspacemember.entity.WorkspaceMember;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TaskResponseMapper {

    public TaskResponse toResponse(
            Task task,
            List<TaskAssignee> assignments
    ) {
        return new TaskResponse(
                task.getId(),
                task.getProject().getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getCreatedBy().getId(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                assignments.stream().map(this::toAssigneeResponse).toList()
        );
    }

    public List<TaskResponse> toResponses(
            List<Task> tasks,
            List<TaskAssignee> assignments
    ) {
        Map<Long, List<TaskAssignee>> byTask = assignments.stream()
                .collect(Collectors.groupingBy(
                        assignment -> assignment.getTask().getId()
                ));
        return tasks.stream()
                .map(task -> toResponse(
                        task,
                        byTask.getOrDefault(task.getId(), List.of())
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
                member.getUser().getDisplayName()
        );
    }
}
