package collabdesk.task.repository;

import collabdesk.task.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByProject_IdOrderByCreatedAtAsc(Long projectId);

    Optional<Task> findByIdAndProject_Id(
            Long taskId,
            Long projectId
    );
    @Query("""
            select distinct t
            from Task t
            where t.project.id = :projectId
              and (
                    :manager = true
                    or t.visibility = collabdesk.task.entity.TaskVisibility.PROJECT
                    or t.createdBy.id = :userId
                    or exists (
                        select ta.id
                        from TaskAssignee ta
                        where ta.task = t
                          and ta.projectMember.workspaceMember.user.id = :userId
                    )
              )
            order by t.createdAt asc
            """)
    List<Task> findAccessibleForProject(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId,
            @Param("manager") boolean manager
    );
}
