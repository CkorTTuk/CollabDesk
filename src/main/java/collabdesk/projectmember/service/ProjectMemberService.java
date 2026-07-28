package collabdesk.projectmember.service;

import collabdesk.project.service.AccessibleProject;
import collabdesk.project.service.ProjectAccessService;
import collabdesk.projectmember.dto.ProjectMemberResponse;
import collabdesk.projectmember.entity.ProjectMember;
import collabdesk.projectmember.repository.ProjectMemberRepository;
import collabdesk.workspace.service.WorkspaceAccessService;
import collabdesk.workspacemember.entity.WorkspaceMember;
import collabdesk.workspacemember.repository.WorkspaceMemberRepository;
import collabdesk.workspace.service.exceptions.WorkspaceMemberNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ProjectAccessService projectAccessService;
    private final WorkspaceAccessService workspaceAccessService;

    public ProjectMemberService(
            ProjectMemberRepository projectMemberRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            ProjectAccessService projectAccessService,
            WorkspaceAccessService workspaceAccessService
    ) {
        this.projectMemberRepository = projectMemberRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.projectAccessService = projectAccessService;
        this.workspaceAccessService = workspaceAccessService;
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> findAll(
            Long workspaceId,
            Long projectId,
            Long currentUserId
    ) {
        projectAccessService.requireAccessibleProject(
                workspaceId,
                projectId,
                currentUserId
        );
        return projectMemberRepository
                .findByProject_IdOrderByJoinedAtAsc(projectId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProjectMemberResponse add(
            Long workspaceId,
            Long projectId,
            Long currentUserId,
            Long workspaceMemberId
    ) {
        workspaceAccessService.requireManager(workspaceId, currentUserId);
        AccessibleProject access = projectAccessService.requireAccessibleProject(
                workspaceId,
                projectId,
                currentUserId
        );
        WorkspaceMember workspaceMember = workspaceMemberRepository
                .findByIdAndWorkspace_Id(workspaceMemberId, workspaceId)
                .orElseThrow(() -> new WorkspaceMemberNotFoundException(
                        "Workspace member was not found"
                ));

        if (projectMemberRepository.existsByProject_IdAndWorkspaceMember_Id(
                projectId,
                workspaceMemberId
        )) {
            throw new ProjectMemberAlreadyExistsException(
                    "Workspace member is already in this project"
            );
        }

        try {
            return toResponse(projectMemberRepository.save(
                    new ProjectMember(access.project(), workspaceMember)
            ));
        } catch (DataIntegrityViolationException ex) {
            throw new ProjectMemberAlreadyExistsException(
                    "Workspace member is already in this project"
            );
        }
    }

    @Transactional
    public void remove(
            Long workspaceId,
            Long projectId,
            Long projectMemberId,
            Long currentUserId
    ) {
        workspaceAccessService.requireManager(workspaceId, currentUserId);
        projectAccessService.requireAccessibleProject(
                workspaceId,
                projectId,
                currentUserId
        );
        ProjectMember member = projectMemberRepository
                .findByIdAndProject_Id(projectMemberId, projectId)
                .orElseThrow(() -> new ProjectMemberNotFoundException(
                        "Project member was not found"
                ));
        projectMemberRepository.delete(member);
    }

    private ProjectMemberResponse toResponse(ProjectMember member) {
        WorkspaceMember workspaceMember = member.getWorkspaceMember();
        return new ProjectMemberResponse(
                member.getId(),
                workspaceMember.getId(),
                workspaceMember.getUser().getId(),
                workspaceMember.getUser().getEmail(),
                workspaceMember.getUser().getDisplayName(),
                workspaceMember.getRole(),
                member.getJoinedAt()
        );
    }
}
