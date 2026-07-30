package collabdesk.project.role.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "access_role_permissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccessRolePermission {

    @EmbeddedId
    private AccessRolePermissionId id;

    @MapsId("roleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private AccessRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", length = 40, nullable = false,
            insertable = false, updatable = false)
    private ProjectPermission permission;

    public AccessRolePermission(
            AccessRole role,
            ProjectPermission permission
    ) {
        this.role = Objects.requireNonNull(role, "Role cannot be null");
        this.permission = Objects.requireNonNull(
                permission,
                "Permission cannot be null"
        );
        this.id = new AccessRolePermissionId(role.getId(), permission);
    }
}
