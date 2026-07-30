package collabdesk.task.assignee.repository;

import collabdesk.task.assignee.entity.TaskAssignee;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface TaskAssigneeRepository
        extends JpaRepository<TaskAssignee, Long> {

    @EntityGraph(attributePaths = {
            "projectMember",
            "projectMember.workspaceMember",
            "projectMember.workspaceMember.user"
    })
    List<TaskAssignee> findByTask_Project_IdOrderByAssignedAtAsc(Long projectId);

    @EntityGraph(attributePaths = {
            "projectMember",
            "projectMember.workspaceMember",
            "projectMember.workspaceMember.user"
    })
    Optional<TaskAssignee> findByTask_Id(Long taskId);

    @EntityGraph(attributePaths = {
            "projectMember",
            "projectMember.workspaceMember",
            "projectMember.workspaceMember.user"
    })
    List<TaskAssignee> findByTask_IdIn(
            Collection<Long> taskIds
    );

    void deleteByTask_Id(Long taskId);

    boolean existsByTask_IdAndProjectMember_WorkspaceMember_User_Id(
            Long taskId,
            Long userId
    );

    boolean existsByTask_Id(Long taskId);
}
