import { apiFetch, createApiError } from './apiError.js'
import { withCsrf } from './csrf.js'

function rolesUrl(workspaceId) {
  return `/api/v1/workspaces/${workspaceId}/access-roles`
}

export async function getAccessRoles(workspaceId) {
  const response = await apiFetch(rolesUrl(workspaceId), {
    credentials: 'same-origin',
  })
  if (!response.ok) {
    throw await createApiError(response, 'Unable to load custom roles.')
  }
  return response.json()
}

export async function createAccessRole(workspaceId, role) {
  const response = await apiFetch(
    rolesUrl(workspaceId),
    await withCsrf({
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(role),
    }),
  )
  if (!response.ok) {
    throw await createApiError(response, 'Unable to create the role.')
  }
  return response.json()
}

export async function updateAccessRole(workspaceId, roleId, role) {
  const response = await apiFetch(
    `${rolesUrl(workspaceId)}/${roleId}`,
    await withCsrf({
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(role),
    }),
  )
  if (!response.ok) {
    throw await createApiError(response, 'Unable to update the role.')
  }
  return response.json()
}

export async function deleteAccessRole(workspaceId, roleId) {
  const response = await apiFetch(
    `${rolesUrl(workspaceId)}/${roleId}`,
    await withCsrf({ method: 'DELETE' }),
  )
  if (!response.ok) {
    throw await createApiError(response, 'Unable to delete the role.')
  }
}
