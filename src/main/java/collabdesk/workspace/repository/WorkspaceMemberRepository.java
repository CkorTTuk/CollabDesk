package collabdesk.workspace.repository;

import collabdesk.workspace.entity.WorkspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {
    List<WorkspaceMember> findByUser_IdOrderByWorkspace_CreatedAtAsc(Long userId);

    Optional<WorkspaceMember> findByWorkspace_IdAndUser_Id(
            Long workspace_Id,
            Long userId
    );

    boolean existsByWorkspace_IdAndUser_Id(
            Long workspace_Id,
            Long userId
    );
}
