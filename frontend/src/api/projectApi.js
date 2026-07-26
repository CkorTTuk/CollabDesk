import { apiFetch, createApiError } from './apiError.js'
import { withCsrf } from './csrf.js'

function projectsUrl(workspaceId) {
  return `/api/v1/workspaces/${workspaceId}/projects`
}

export async function getProjects(workspaceId) {
  const response = await apiFetch(projectsUrl(workspaceId), {
    credentials: 'same-origin',
  })

  if (!response.ok) {
    throw await createApiError(
      response,
      'Unable to load workspace projects.',
    )
  }

  return response.json()
}

export async function createProject(workspaceId, { name, description }) {
  const response = await apiFetch(
    projectsUrl(workspaceId),
    await withCsrf({
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        name,
        description: description.trim() || null,
      }),
    }),
  )

  if (!response.ok) {
    throw await createApiError(response, 'Unable to create the project.')
  }

  return response.json()
}
