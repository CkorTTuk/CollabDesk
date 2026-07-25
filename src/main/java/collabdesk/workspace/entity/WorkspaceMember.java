package collabdesk.workspace.entity;

import collabdesk.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "workspace_members")

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role",length = 20,nullable = false)
    private WorkspaceRole role;

    @Column(name = "joined_at",nullable = false)
    private Instant joinedAt;

    private WorkspaceMember(Workspace workspace, User user, WorkspaceRole role) {
        Objects.requireNonNull(workspace, "Workspace cannot be null");
        Objects.requireNonNull(user, "User must not be null");
        Objects.requireNonNull(role, "WorkspaceRole must not be null");

        this.workspace = workspace;
        this.user = user;
        this.role = role;
        this.joinedAt = Instant.now();

    }

    public static WorkspaceMember owner(Workspace workspace, User user) {
        return new WorkspaceMember(workspace, user, WorkspaceRole.OWNER);
    }
    public static WorkspaceMember member(Workspace workspace, User user) {
        return new WorkspaceMember(workspace, user, WorkspaceRole.MEMBER);
    }
}
