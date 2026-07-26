package collabdesk.controller;

import collabdesk.auth.security.AuthenticatedUserPrincipal;
import collabdesk.workspacemember.dto.AddWorkspaceMemberRequest;
import collabdesk.workspacemember.dto.UpdateWorkspaceMemberRoleRequest;
import collabdesk.workspacemember.dto.WorkspaceMemberResponse;
import collabdesk.workspacemember.service.WorkspaceMemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/members")
public class WorkspaceMemberController {

    private final WorkspaceMemberService workspaceMemberService;

    public WorkspaceMemberController(
            WorkspaceMemberService workspaceMemberService
    ) {
        this.workspaceMemberService = workspaceMemberService;
    }

    @GetMapping
    public List<WorkspaceMemberResponse> findAll(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return workspaceMemberService.findForWorkspace(
                workspaceId,
                principal.getUserId()
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceMemberResponse add(
            @PathVariable Long workspaceId,
            @Valid @RequestBody AddWorkspaceMemberRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return workspaceMemberService.add(
                workspaceId,
                principal.getUserId(),
                request.email(),
                request.role()
        );
    }

    @PatchMapping("/{memberId}/role")
    public WorkspaceMemberResponse changeRole(
            @PathVariable Long workspaceId,
            @PathVariable Long memberId,
            @Valid @RequestBody UpdateWorkspaceMemberRoleRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return workspaceMemberService.changeRole(
                workspaceId,
                memberId,
                principal.getUserId(),
                request.role()
        );
    }

    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @PathVariable Long workspaceId,
            @PathVariable Long memberId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        workspaceMemberService.remove(
                workspaceId,
                memberId,
                principal.getUserId()
        );
    }
}
