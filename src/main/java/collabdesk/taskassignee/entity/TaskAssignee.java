package collabdesk.taskassignee.entity;

import collabdesk.projectmember.entity.ProjectMember;
import collabdesk.task.entity.Task;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "task_assignees",
        uniqueConstraints = @UniqueConstraint(
                name = "task_assignees_task_project_member_uk",
                columnNames = {"task_id", "project_member_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskAssignee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_member_id", nullable = false)
    private ProjectMember projectMember;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    public TaskAssignee(Task task, ProjectMember projectMember) {
        this.task = Objects.requireNonNull(task, "Task cannot be null");
        this.projectMember = Objects.requireNonNull(
                projectMember,
                "Project member cannot be null"
        );

        Long taskProjectId = task.getProject().getId();
        Long memberProjectId = projectMember.getProject().getId();
        if (taskProjectId != null
                && memberProjectId != null
                && !taskProjectId.equals(memberProjectId)) {
            throw new IllegalArgumentException(
                    "Assignee must belong to the task's project"
            );
        }
        this.assignedAt = Instant.now();
    }
}
