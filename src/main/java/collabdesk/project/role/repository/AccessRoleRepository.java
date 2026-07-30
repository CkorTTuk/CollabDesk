package collabdesk.project.role.repository;

import collabdesk.project.role.entity.AccessRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccessRoleRepository extends JpaRepository<AccessRole, Long> {

    @EntityGraph(attributePaths = "workspace")
    List<AccessRole> findByWorkspace_IdOrderByNameAsc(Long workspaceId);

    @EntityGraph(attributePaths = "workspace")
    Optional<AccessRole> findByIdAndWorkspace_Id(Long id, Long workspaceId);

    @EntityGraph(attributePaths = "workspace")
    List<AccessRole> findAllByWorkspace_IdAndIdIn(
            Long workspaceId,
            Collection<Long> ids
    );

    boolean existsByWorkspace_IdAndNameIgnoreCase(
            Long workspaceId,
            String name
    );

    boolean existsByWorkspace_IdAndNameIgnoreCaseAndIdNot(
            Long workspaceId,
            String name,
            Long id
    );
}
