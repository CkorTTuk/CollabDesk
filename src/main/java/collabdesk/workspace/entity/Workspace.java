package collabdesk.workspace.entity;

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

/** Top-level collaboration container owned and administered by its members. */
@Entity
@Table(name = "workspaces")

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workspace {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name",length = 100,nullable = false)
    @Size(max = 100)
    @NotBlank
    private String name;

    @Column(name = "description",length = 500)
    @Size(max = 500)
    @Nullable
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name= "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Nullable
    @Version
    private Long version;

    public Workspace(String name, String description, User createdBy) {
        if(name == null ) {
            throw new IllegalArgumentException("Name cannot be null.");
        }
        String normalizedDescription = description == null ? null : description.trim();

        if (normalizedDescription != null && normalizedDescription.length() > 500) {
            throw new IllegalArgumentException(
                    "Description cannot be longer than 500 characters."
            );
        }
        Objects.requireNonNull(createdBy,"User cannot be null.");
        if(name.isBlank()) {
            throw new IllegalArgumentException("Name  cannot be blank.");
        }
        if(!(name.trim().length() >=2 && name.trim().length() <= 100)) {
            throw new IllegalArgumentException("Name must be between 2 and 100 characters.");
        }
        if(createdBy.getStatus() == UserStatus.DISABLED) {
            throw new IllegalArgumentException("User cannot be DISABLED.");
        }



        this.name = name.trim();
        this.description = normalizedDescription;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
