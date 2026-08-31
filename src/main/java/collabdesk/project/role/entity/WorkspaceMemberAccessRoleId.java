package collabdesk.project.role.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class WorkspaceMemberAccessRoleId implements Serializable {
    @Column(name = "workspace_member_id")
    private Long workspaceMemberId;

    @Column(name = "role_id")
    private Long roleId;
}
