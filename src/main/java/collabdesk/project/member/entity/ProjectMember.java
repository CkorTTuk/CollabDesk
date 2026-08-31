package collabdesk.project.member.entity;

import collabdesk.project.entity.Project;
import collabdesk.workspace.member.entity.WorkspaceMember;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "project_members",
        uniqueConstraints = @UniqueConstraint(
                name = "project_members_project_workspace_member_uk",
                columnNames = {"project_id", "workspace_member_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_member_id", nullable = false)
    private WorkspaceMember workspaceMember;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "grants_access", nullable = false)
    private boolean grantsAccess;

    public ProjectMember(Project project, WorkspaceMember workspaceMember) {
        this(project, workspaceMember, true);
    }

    public ProjectMember(
            Project project,
            WorkspaceMember workspaceMember,
            boolean grantsAccess
    ) {
        this.project = Objects.requireNonNull(project, "Project cannot be null");
        this.workspaceMember = Objects.requireNonNull(
                workspaceMember,
                "Workspace member cannot be null"
        );

        Long projectWorkspaceId = project.getWorkspace().getId();
        Long memberWorkspaceId = workspaceMember.getWorkspace().getId();
        if (projectWorkspaceId != null
                && memberWorkspaceId != null
                && !projectWorkspaceId.equals(memberWorkspaceId)) {
            throw new IllegalArgumentException(
                    "Project member must belong to the project's workspace"
            );
        }

        this.joinedAt = Instant.now();
        this.grantsAccess = grantsAccess;
    }

    public void grantAccess() {
        this.grantsAccess = true;
    }

}
