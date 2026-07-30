package collabdesk.project.role.repository;

import collabdesk.project.role.entity.ProjectMemberRole;
import collabdesk.project.role.entity.ProjectMemberRoleId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProjectMemberRoleRepository
        extends JpaRepository<ProjectMemberRole, ProjectMemberRoleId> {

    @EntityGraph(attributePaths = "role")
    List<ProjectMemberRole> findByProjectMember_Id(Long projectMemberId);

    @EntityGraph(attributePaths = "role")
    List<ProjectMemberRole> findByProjectMember_IdIn(
            Collection<Long> projectMemberIds
    );

    boolean existsByProjectMember_Id(Long projectMemberId);

    boolean existsByRole_Id(Long roleId);

    void deleteByProjectMember_Id(Long projectMemberId);
}
