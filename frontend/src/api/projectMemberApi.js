import { apiFetch, createApiError } from './apiError.js'
import { withCsrf } from './csrf.js'

function projectMembersUrl(workspaceId, projectId) {
  return `/api/v1/workspaces/${workspaceId}/projects/${projectId}/members`
}

export async function getProjectMembers(workspaceId, projectId) {
  const response = await apiFetch(
    projectMembersUrl(workspaceId, projectId),
    { credentials: 'same-origin' },
  )

  if (!response.ok) {
    throw await createApiError(response, 'Unable to load the project team.')
  }
  return response.json()
}

export async function addProjectMember(
  workspaceId,
  projectId,
  workspaceMemberId,
  roleIds = [],
) {
  const response = await apiFetch(
    projectMembersUrl(workspaceId, projectId),
    await withCsrf({
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ workspaceMemberId, roleIds }),
    }),
  )

  if (!response.ok) {
    throw await createApiError(response, 'Unable to add the project member.')
  }
  return response.json()
}

export async function replaceProjectMemberRoles(
  workspaceId,
  projectId,
  projectMemberId,
  roleIds,
) {
  const response = await apiFetch(
    `${projectMembersUrl(
      workspaceId,
      projectId,
    )}/${projectMemberId}/roles`,
    await withCsrf({
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ roleIds }),
    }),
  )

  if (!response.ok) {
    throw await createApiError(
      response,
      'Unable to update the project member roles.',
    )
  }
  return response.json()
}

export async function removeProjectMember(
  workspaceId,
  projectId,
  projectMemberId,
) {
  const response = await apiFetch(
    `${projectMembersUrl(workspaceId, projectId)}/${projectMemberId}`,
    await withCsrf({ method: 'DELETE' }),
  )

  if (!response.ok) {
    throw await createApiError(
      response,
      'Unable to remove the project member.',
    )
  }
}
