package collabdesk.task.entity;

import collabdesk.project.entity.Project;
import collabdesk.user.entity.User;
import collabdesk.user.entity.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "tasks")

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "title", nullable = false, length = 150)
    @Size(max = 150, min = 2)
    @NotBlank
    private String title;

    @Column(name = "description", length = 1000)
    @Size(max = 1000)
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Nullable
    private Long version;

    public Task(
            Project project,
            String title,
            String description,
            User createdBy
    ) {
        this.project = Objects.requireNonNull(
                project,
                "Project cannot be null"
        );
        this.createdBy = Objects.requireNonNull(
                createdBy,
                "Creator cannot be null"
        );

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title cannot be blank");
        }
        String normalizedTitle = title.trim();
        if (normalizedTitle.length() < 2 || normalizedTitle.length() > 150) {
            throw new IllegalArgumentException(
                    "Title must be between 2 and 150 characters"
            );
        }

        String normalizedDescription =
                description == null ? null : description.trim();
        if (normalizedDescription != null && normalizedDescription.isEmpty()) {
            normalizedDescription = null;
        }
        if (normalizedDescription != null
                && normalizedDescription.length() > 1000) {
            throw new IllegalArgumentException(
                    "Description cannot be longer than 1000 characters"
            );
        }
        if (createdBy.getStatus() == UserStatus.DISABLED) {
            throw new IllegalArgumentException(
                    "Disabled user cannot create tasks"
            );
        }

        this.title = normalizedTitle;
        this.description = normalizedDescription;
        this.status = TaskStatus.TODO;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void changeStatus(TaskStatus newStatus) {
        this.status = Objects.requireNonNull(
                newStatus,
                "Task status cannot be null"
        );
        this.updatedAt = Instant.now();
    }
}
