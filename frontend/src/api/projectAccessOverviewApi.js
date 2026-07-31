import { apiFetch, createApiError } from './apiError.js'

export async function getProjectAccessOverview(workspaceId) {
  const response = await apiFetch(
    `/api/v1/workspaces/${workspaceId}/project-access-overview`,
    { credentials: 'same-origin' },
  )

  if (!response.ok) {
    throw await createApiError(
      response,
      'Unable to load workspace project access.',
    )
  }
  return response.json()
}
