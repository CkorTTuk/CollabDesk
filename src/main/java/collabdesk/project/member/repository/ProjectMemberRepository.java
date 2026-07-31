package collabdesk.project.member.repository;

import collabdesk.project.member.entity.ProjectMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository
        extends JpaRepository<ProjectMember, Long> {

    @EntityGraph(attributePaths = {"workspaceMember", "workspaceMember.user"})
    List<ProjectMember> findByProject_IdOrderByJoinedAtAsc(Long projectId);

    @EntityGraph(attributePaths = {
            "project",
            "workspaceMember",
            "workspaceMember.user"
    })
    List<ProjectMember> findByProject_IdInOrderByProject_IdAscJoinedAtAsc(
            Collection<Long> projectIds
    );

    @EntityGraph(attributePaths = {"workspaceMember", "workspaceMember.user"})
    Optional<ProjectMember> findByIdAndProject_Id(
            Long projectMemberId,
            Long projectId
    );

    boolean existsByProject_IdAndWorkspaceMember_Id(
            Long projectId,
            Long workspaceMemberId
    );

    @EntityGraph(attributePaths = {"workspaceMember", "workspaceMember.user"})
    List<ProjectMember> findAllByProject_IdAndIdIn(
            Long projectId,
            Collection<Long> ids
    );
    boolean existsByProject_IdAndWorkspaceMember_User_Id(
            Long projectId,
            Long userId
    );

    @EntityGraph(attributePaths = {"workspaceMember", "workspaceMember.user"})
    Optional<ProjectMember> findByProject_IdAndWorkspaceMember_User_Id(
            Long projectId,
            Long userId
    );
}
