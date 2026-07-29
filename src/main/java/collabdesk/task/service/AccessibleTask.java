package collabdesk.task.service;

import collabdesk.project.service.AccessibleProject;
import collabdesk.task.entity.Task;

public record AccessibleTask(
        Task task,
        AccessibleProject projectAccess
) {
}
