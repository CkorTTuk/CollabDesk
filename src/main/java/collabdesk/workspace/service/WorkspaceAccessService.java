package collabdesk.workspace.service;

import collabdesk.workspace.entity.WorkspaceMember;
import collabdesk.workspace.repository.WorkspaceMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceAccessService {
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public WorkspaceAccessService(WorkspaceMemberRepository workspaceMemberRepository) {
        this.workspaceMemberRepository = workspaceMemberRepository;
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
