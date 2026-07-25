package collabdesk.project.repository;

import collabdesk.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository
        extends JpaRepository<Project, Long> {

    List<Project> findByWorkspace_IdOrderByCreatedAtAsc(
            Long workspaceId
    );
}
