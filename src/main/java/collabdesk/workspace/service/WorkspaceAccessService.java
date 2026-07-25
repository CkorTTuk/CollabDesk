package collabdesk.workspace.service;

import collabdesk.workspace.entity.WorkspaceMember;
import collabdesk.workspace.repository.WorkspaceMemberRepository;
import collabdesk.workspace.repository.WorkspaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class WorkspaceAccessService {
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceRepository workspaceRepository;
    public WorkspaceAccessService(
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceRepository workspaceRepository) {
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceRepository = workspaceRepository;
    }
    @Transactional(readOnly = true)
    public WorkspaceMember requireMember(
            Long workspaceId,
            Long currentUserId
    ){
        return workspaceMemberRepository
                .findByWorkspace_IdAndUser_Id(workspaceId, currentUserId)
                .orElseThrow(() -> new WorkspaceAccessDeniedException(
                        "Workspace membership not found"
                ));
    }
}
