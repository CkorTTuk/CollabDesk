package collabdesk.workspace.service;

import collabdesk.user.entity.User;
import collabdesk.user.repository.UserRepository;
import collabdesk.workspace.dto.WorkspaceResponse;
import collabdesk.workspace.entity.Workspace;
import collabdesk.workspace.member.entity.WorkspaceMember;
import collabdesk.workspace.entity.WorkspaceRole;
import collabdesk.workspace.member.repository.WorkspaceMemberRepository;
import collabdesk.workspace.repository.WorkspaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates workspaces and lists those visible to a user. Creation also assigns
 * the creator as the workspace owner in the same transaction.
 */
@Service
public class WorkspaceService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
    }
    /** Creates a workspace and its immutable OWNER membership together. */
    @Transactional
    public WorkspaceResponse create(
            Long currentUserId,
            String name,
            String description
    ){
        if(!userRepository.existsById(currentUserId)){
            throw new IllegalArgumentException("User does not exist");
        }
        User user =  userRepository.findById(currentUserId).get();

        Workspace workspace = new Workspace(name, description, user);
        Workspace saved = workspaceRepository.save(workspace);

        WorkspaceMember workspaceMember = WorkspaceMember.owner(workspace, user);
        workspaceMemberRepository.save(workspaceMember);

        return new WorkspaceResponse(
                saved.getId(),
                saved.getName(),
                saved.getDescription(),
                WorkspaceRole.OWNER,
                saved.getCreatedAt()
                );

    }

    /** Returns only workspaces in which the user currently has membership. */
    @Transactional(readOnly = true)
    public List<WorkspaceResponse> findForUser(Long currentUserId){
        if(!userRepository.existsById(currentUserId)){
            throw new IllegalArgumentException("User does not exist");
        }
        List<WorkspaceMember> workspaces = workspaceMemberRepository.findByUser_IdOrderByWorkspace_CreatedAtAsc(currentUserId);
        List<WorkspaceResponse> workspacesResponse = new ArrayList<WorkspaceResponse>();
        for(WorkspaceMember workspaceMember : workspaces){
            Workspace w = workspaceMember.getWorkspace();
            workspacesResponse.add(
                    new WorkspaceResponse(
                            w.getId(),
                            w.getName(),
                            w.getDescription(),
                            workspaceMember.getRole(),
                            w.getCreatedAt()
            ));
        }
        return List.copyOf(workspacesResponse);
    }
}
