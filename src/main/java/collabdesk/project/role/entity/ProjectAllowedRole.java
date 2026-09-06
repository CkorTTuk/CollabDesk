package collabdesk.project.role.entity;

import collabdesk.project.entity.Project;
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

/** Join entity allowing holders of a workspace role into a restricted project. */
@Entity
@Table(name = "project_allowed_roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectAllowedRole {
    @EmbeddedId
    private ProjectAllowedRoleId id;

    @MapsId("projectId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @MapsId("roleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private AccessRole role;

    public ProjectAllowedRole(Project project, AccessRole role) {
        this.project = Objects.requireNonNull(project);
        this.role = Objects.requireNonNull(role);
        Long projectWorkspaceId = project.getWorkspace().getId();
        Long roleWorkspaceId = role.getWorkspace().getId();
        if (projectWorkspaceId != null && roleWorkspaceId != null
                && !projectWorkspaceId.equals(roleWorkspaceId)) {
            throw new IllegalArgumentException("Role and project must belong to the same workspace");
        }
        this.id = new ProjectAllowedRoleId(project.getId(), role.getId());
    }
}
