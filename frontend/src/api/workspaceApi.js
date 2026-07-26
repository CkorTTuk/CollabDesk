import { apiFetch, createApiError } from './apiError.js'
import { withCsrf } from './csrf.js'

const WORKSPACES_URL = '/api/v1/workspaces'

export async function getWorkspaces() {
  const response = await apiFetch(WORKSPACES_URL, {
    credentials: 'same-origin',
  })

  if (!response.ok) {
    throw await createApiError(
      response,
      'Unable to load workspaces.',
    )
  }

  return response.json()
}

export async function createWorkspace({ name, description }) {
  const response = await apiFetch(
    WORKSPACES_URL,
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
    throw await createApiError(
      response,
      'Unable to create the workspace.',
    )
  }

  return response.json()
}
