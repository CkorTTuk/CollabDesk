package collabdesk.project.role.repository;

import collabdesk.project.role.entity.ProjectAllowedRole;
import collabdesk.project.role.entity.ProjectAllowedRoleId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;

public interface ProjectAllowedRoleRepository
        extends JpaRepository<ProjectAllowedRole, ProjectAllowedRoleId> {
    boolean existsByProject_Id(Long projectId);

    boolean existsByRole_Id(Long roleId);

    @EntityGraph(attributePaths = "role")
    List<ProjectAllowedRole> findByProject_IdOrderByRole_NameAsc(Long projectId);

    @EntityGraph(attributePaths = "role")
    List<ProjectAllowedRole> findByProject_IdIn(Collection<Long> projectIds);

    void deleteByProject_Id(Long projectId);

    @Query("""
            select case when count(par) > 0 then true else false end
            from ProjectAllowedRole par
            where par.project.id = :projectId
              and exists (
                    select wmar.id
                    from WorkspaceMemberAccessRole wmar
                    where wmar.workspaceMember.id = :workspaceMemberId
                      and wmar.role = par.role
              )
            """)
    boolean existsAllowedRoleForUser(
            @Param("projectId") Long projectId,
            @Param("workspaceMemberId") Long workspaceMemberId
    );

    @Query("""
            select case when count(par) > 0 then true else false end
            from ProjectAllowedRole par
            join AccessRolePermission arp on arp.role = par.role
            where par.project.id = :projectId
              and arp.permission = collabdesk.project.role.entity.ProjectPermission.EDIT_PROJECT
              and exists (
                    select wmar.id from WorkspaceMemberAccessRole wmar
                    where wmar.workspaceMember.id = :workspaceMemberId
                      and wmar.role = par.role
              )
            """)
    boolean canEditProject(
            @Param("projectId") Long projectId,
            @Param("workspaceMemberId") Long workspaceMemberId
    );
}
