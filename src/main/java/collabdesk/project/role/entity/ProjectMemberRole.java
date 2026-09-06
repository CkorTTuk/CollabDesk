package collabdesk.project.role.entity;

import collabdesk.project.member.entity.ProjectMember;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/** Join entity granting a custom access role to an explicit project member. */
@Entity
@Table(name = "project_member_roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMemberRole {

    @EmbeddedId
    private ProjectMemberRoleId id;

    @MapsId("projectMemberId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_member_id", nullable = false)
    private ProjectMember projectMember;

    @MapsId("roleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private AccessRole role;

    public ProjectMemberRole(
            ProjectMember projectMember,
            AccessRole role
    ) {
        this.projectMember = Objects.requireNonNull(
                projectMember,
                "Project member cannot be null"
        );
        this.role = Objects.requireNonNull(role, "Role cannot be null");

        Long projectWorkspaceId = projectMember.getProject()
                .getWorkspace()
                .getId();
        Long roleWorkspaceId = role.getWorkspace().getId();
        if (projectWorkspaceId != null
                && roleWorkspaceId != null
                && !projectWorkspaceId.equals(roleWorkspaceId)) {
            throw new IllegalArgumentException(
                    "Role must belong to the project's workspace"
            );
        }
        this.id = new ProjectMemberRoleId(
                projectMember.getId(),
                role.getId()
        );
    }
}
