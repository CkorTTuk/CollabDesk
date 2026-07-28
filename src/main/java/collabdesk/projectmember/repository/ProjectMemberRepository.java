package collabdesk.projectmember.repository;

import collabdesk.projectmember.entity.ProjectMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository
        extends JpaRepository<ProjectMember, Long> {

    @EntityGraph(attributePaths = {"workspaceMember", "workspaceMember.user"})
    List<ProjectMember> findByProject_IdOrderByJoinedAtAsc(Long projectId);

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
}
