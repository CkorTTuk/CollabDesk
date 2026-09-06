package collabdesk.project.role.entity;

import collabdesk.workspace.member.entity.WorkspaceMember;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/** Join entity attaching a reusable custom role to a workspace member. */
@Entity
@Table(name = "workspace_member_access_roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceMemberAccessRole {
    @EmbeddedId
    private WorkspaceMemberAccessRoleId id;

    @MapsId("workspaceMemberId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_member_id", nullable = false)
    private WorkspaceMember workspaceMember;

    @MapsId("roleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private AccessRole role;

    public WorkspaceMemberAccessRole(
            WorkspaceMember workspaceMember,
            AccessRole role
    ) {
        this.workspaceMember = Objects.requireNonNull(workspaceMember);
        this.role = Objects.requireNonNull(role);
        Long memberWorkspaceId = workspaceMember.getWorkspace().getId();
        Long roleWorkspaceId = role.getWorkspace().getId();
        if (memberWorkspaceId != null && roleWorkspaceId != null
                && !memberWorkspaceId.equals(roleWorkspaceId)) {
            throw new IllegalArgumentException("Role and member must belong to the same workspace");
        }
        this.id = new WorkspaceMemberAccessRoleId(
                workspaceMember.getId(),
                role.getId()
        );
    }
}
