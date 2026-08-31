package collabdesk.project.repository;

import collabdesk.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository
        extends JpaRepository<Project, Long> {

    List<Project> findByWorkspace_IdOrderByCreatedAtAsc(
            Long workspaceId
    );

    Optional<Project> findByIdAndWorkspace_Id(
            Long projectId,
            Long workspaceId
    );

    @Query("""
            select distinct p
            from Project p
            where p.workspace.id = :workspaceId
              and (
                    :manager = true
                    or p.createdBy.id = :userId
                    or (
                        not exists (
                            select pm.id from ProjectMember pm
                            where pm.project = p and pm.grantsAccess = true
                        )
                        and not exists (
                            select par.id from ProjectAllowedRole par
                            where par.project = p
                        )
                    )
                    or exists (
                        select pm.id
                        from ProjectMember pm
                        where pm.project = p
                          and pm.workspaceMember.user.id = :userId
                          and pm.grantsAccess = true
                    )
                    or exists (
                        select par.id
                        from ProjectAllowedRole par
                        where par.project = p
                          and exists (
                              select wmar.id
                              from WorkspaceMemberAccessRole wmar
                              where wmar.workspaceMember.id = :workspaceMemberId
                                and wmar.role = par.role
                          )
                    )
              )
            order by p.createdAt asc
            """)
    List<Project> findAccessibleForWorkspace(
            @Param("workspaceId") Long workspaceId,
            @Param("userId") Long userId,
            @Param("workspaceMemberId") Long workspaceMemberId,
            @Param("manager") boolean manager
    );
}
