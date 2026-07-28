package collabdesk.taskassignee.repository;

import collabdesk.taskassignee.entity.TaskAssignee;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

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
    List<TaskAssignee> findByTask_IdOrderByAssignedAtAsc(Long taskId);

    void deleteByTask_Id(Long taskId);
}
