import { apiFetch, createApiError } from './apiError.js'
import { withCsrf } from './csrf.js'

function membersUrl(workspaceId) {
  return `/api/v1/workspaces/${workspaceId}/members`
}

export async function getWorkspaceMembers(workspaceId) {
  const response = await apiFetch(membersUrl(workspaceId), {
    credentials: 'same-origin',
  })

  if (!response.ok) {
    throw await createApiError(response, 'Unable to load members.')
  }

  return response.json()
}

export async function addWorkspaceMember(workspaceId, form) {
  const response = await apiFetch(
    membersUrl(workspaceId),
    await withCsrf({
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(form),
    }),
  )

  if (!response.ok) {
    throw await createApiError(response, 'Unable to add the member.')
  }

  return response.json()
}

export async function changeWorkspaceMemberRole(
  workspaceId,
  memberId,
  role,
) {
  const response = await apiFetch(
    `${membersUrl(workspaceId)}/${memberId}/role`,
    await withCsrf({
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ role }),
    }),
  )

  if (!response.ok) {
    throw await createApiError(response, 'Unable to update the role.')
  }

  return response.json()
}

export async function removeWorkspaceMember(workspaceId, memberId) {
  const response = await apiFetch(
    `${membersUrl(workspaceId)}/${memberId}`,
    await withCsrf({ method: 'DELETE' }),
  )

  if (!response.ok) {
    throw await createApiError(response, 'Unable to remove the member.')
  }
}

export async function replaceWorkspaceMemberAccessRoles(
  workspaceId,
  memberId,
  roleIds,
) {
  const response = await apiFetch(
    `${membersUrl(workspaceId)}/${memberId}/access-roles`,
    await withCsrf({
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ roleIds }),
    }),
  )
  if (!response.ok) {
    throw await createApiError(response, 'Unable to update custom roles.')
  }
  return response.json()
}
