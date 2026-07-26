package collabdesk.task.repository;

import collabdesk.task.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByProject_IdOrderByCreatedAtAsc(Long projectId);

    Optional<Task> findByIdAndProject_Id(
            Long taskId,
            Long projectId
    );
}
