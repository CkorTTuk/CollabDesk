package collabdesk.workspace.member.entity;

import collabdesk.user.entity.User;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspace.entity.WorkspaceRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/** Links a user to a workspace and stores the member's built-in workspace role. */
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
    public static WorkspaceMember collaborator(
            Workspace workspace,
            User user,
            WorkspaceRole role
    ) {
        if (role == WorkspaceRole.OWNER) {
            throw new IllegalArgumentException(
                    "OWNER cannot be assigned through member management"
            );
        }

        return new WorkspaceMember(workspace, user, role);
    }
    public void changeRole(WorkspaceRole newRole) {
        Objects.requireNonNull(newRole);

        if (role == WorkspaceRole.OWNER) {
            throw new IllegalStateException(
                    "Owner role cannot be changed"
            );
        }
        if (newRole == WorkspaceRole.OWNER) {
            throw new IllegalArgumentException(
                    "Owner role cannot be assigned"
            );
        }

        role = newRole;
    }
}
