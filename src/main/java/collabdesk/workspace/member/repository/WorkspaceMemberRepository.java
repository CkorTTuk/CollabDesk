package collabdesk.workspace.member.repository;

import collabdesk.workspace.member.entity.WorkspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {
    List<WorkspaceMember> findByUser_IdOrderByWorkspace_CreatedAtAsc(Long userId);

    List<WorkspaceMember> findByWorkspace_IdOrderByJoinedAtAsc(
            Long workspaceId
    );

    Optional<WorkspaceMember> findByIdAndWorkspace_Id(
            Long memberId,
            Long workspaceId
    );

    Optional<WorkspaceMember> findByWorkspace_IdAndUser_Id(
            Long workspace_Id,
            Long userId
    );

    List<WorkspaceMember> findAllByWorkspace_IdAndIdIn(
            Long workspaceId,
            Collection<Long> ids
    );

    boolean existsByWorkspace_IdAndUser_Id(
            Long workspace_Id,
            Long userId
    );
}
