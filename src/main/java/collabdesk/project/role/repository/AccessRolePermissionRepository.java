package collabdesk.project.role.repository;

import collabdesk.project.role.entity.AccessRolePermission;
import collabdesk.project.role.entity.AccessRolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AccessRolePermissionRepository
        extends JpaRepository<AccessRolePermission, AccessRolePermissionId> {

    List<AccessRolePermission> findByRole_Id(Long roleId);

    List<AccessRolePermission> findByRole_IdIn(Collection<Long> roleIds);

    void deleteByRole_Id(Long roleId);
}
