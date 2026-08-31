package collabdesk.project.role.repository;

import collabdesk.project.role.entity.WorkspaceMemberAccessRole;
import collabdesk.project.role.entity.WorkspaceMemberAccessRoleId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WorkspaceMemberAccessRoleRepository
        extends JpaRepository<WorkspaceMemberAccessRole, WorkspaceMemberAccessRoleId> {
    @EntityGraph(attributePaths = "role")
    List<WorkspaceMemberAccessRole> findByWorkspaceMember_Id(Long workspaceMemberId);

    @EntityGraph(attributePaths = "role")
    List<WorkspaceMemberAccessRole> findByWorkspaceMember_IdIn(Collection<Long> memberIds);

    void deleteByWorkspaceMember_Id(Long workspaceMemberId);

    boolean existsByRole_Id(Long roleId);
}
