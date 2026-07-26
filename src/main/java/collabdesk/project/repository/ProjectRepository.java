package collabdesk.project.repository;

import collabdesk.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository
        extends JpaRepository<Project, Long> {

    List<Project> findByWorkspace_IdOrderByCreatedAtAsc(
            Long workspaceId
    );

    Optional<Project> findByIdAndWorkspace_Id(
            Long projectId,
            Long workspaceId
    );
}
