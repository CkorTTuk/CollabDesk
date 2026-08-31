package collabdesk.task.activity.repository;

import collabdesk.task.activity.entity.TaskActivity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskActivityRepository extends JpaRepository<TaskActivity, Long> {
    @EntityGraph(attributePaths = "actor")
    List<TaskActivity> findByTask_IdOrderByCreatedAtDescIdDesc(Long taskId);
}
