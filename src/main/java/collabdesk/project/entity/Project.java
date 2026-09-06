package collabdesk.project.entity;

import collabdesk.user.entity.User;
import collabdesk.user.entity.UserStatus;
import collabdesk.workspace.entity.Workspace;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** Project aggregate contained by a workspace with its own visibility policy. */
@Entity
@Table(name = "projects")

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    Workspace workspace;

    @Column(name = "name", length = 100, nullable = false)
    @Size(max = 100)
    @NotBlank
    String name;

    @Column(name = "description", length = 500)
    @Size(max = 500)
    String description;

    @Column(name = "status", length =  20, nullable = false)
    @Enumerated(EnumType.STRING)
    ProjectStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    User createdBy;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Version
    @Nullable
    Long version;

    public Project(
            Workspace workspace,
            String name,
            String description,
            User createdBy
    ) {
        Objects.requireNonNull(workspace);
        Objects.requireNonNull(createdBy);
        if(name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        String normalizedName = name.trim();
        if(normalizedName.length() < 2 || normalizedName.length() > 100)
            throw new IllegalArgumentException("Name must be between 2 and 100 characters");
        String normalizedDescription = description == null ? null : description.trim();
        if (normalizedDescription != null && normalizedDescription.isEmpty()) {
            normalizedDescription = null;
        }

        if (normalizedDescription != null && normalizedDescription.length() > 500) {
            throw new IllegalArgumentException(
                    "Description cannot be longer than 500 characters."
            );
        }
        if(createdBy.getStatus() == UserStatus.DISABLED){
            throw new IllegalArgumentException("User cannot be DISABLED.");
        }
        this.workspace = workspace;
        this.name = normalizedName;
        this.description = normalizedDescription;
        this.status = ProjectStatus.ACTIVE;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
