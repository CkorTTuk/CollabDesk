package collabdesk.task.activity.entity;

import collabdesk.task.entity.Task;
import collabdesk.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/** Immutable-style audit entry describing a meaningful task state change. */
@Entity
@Table(name = "task_activities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", length = 30, nullable = false)
    private TaskActivityType type;

    @Column(name = "old_value", length = 150)
    private String oldValue;

    @Column(name = "new_value", length = 150)
    private String newValue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public TaskActivity(
            Task task,
            User actor,
            TaskActivityType type,
            String oldValue,
            String newValue
    ) {
        this.task = Objects.requireNonNull(task);
        this.actor = Objects.requireNonNull(actor);
        this.type = Objects.requireNonNull(type);
        this.oldValue = truncate(oldValue);
        this.newValue = truncate(newValue);
        this.createdAt = Instant.now();
    }

    private String truncate(String value) {
        return value == null || value.length() <= 150
                ? value
                : value.substring(0, 150);
    }
}
