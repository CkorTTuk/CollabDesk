import { apiFetch, createApiError } from './apiError.js'
import { withCsrf } from './csrf.js'

function tasksUrl(workspaceId, projectId) {
  return `/api/v1/workspaces/${workspaceId}/projects/${projectId}/tasks`
}

export async function getTasks(workspaceId, projectId) {
  const response = await apiFetch(tasksUrl(workspaceId, projectId), {
    credentials: 'same-origin',
  })

  if (!response.ok) {
    throw await createApiError(response, 'Unable to load tasks.')
  }

  return response.json()
}

export async function createTask(
  workspaceId,
  projectId,
  { title, description },
) {
  const response = await apiFetch(
    tasksUrl(workspaceId, projectId),
    await withCsrf({
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        title,
        description: description.trim() || null,
      }),
    }),
  )

  if (!response.ok) {
    throw await createApiError(response, 'Unable to create the task.')
  }

  return response.json()
}

export async function changeTaskStatus(
  workspaceId,
  projectId,
  taskId,
  status,
) {
  const response = await apiFetch(
    `${tasksUrl(workspaceId, projectId)}/${taskId}/status`,
    await withCsrf({
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ status }),
    }),
  )

  if (!response.ok) {
    throw await createApiError(response, 'Unable to update the task status.')
  }

  return response.json()
}

export async function updateTask(
  workspaceId,
  projectId,
  taskId,
  { title, description },
) {
  const response = await apiFetch(
    `${tasksUrl(workspaceId, projectId)}/${taskId}`,
    await withCsrf({
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title,
        description: description.trim() || null,
      }),
    }),
  )

  if (!response.ok) {
    throw await createApiError(response, 'Unable to edit the task.')
  }
  return response.json()
}

export async function updateTaskAssignee(
  workspaceId,
  projectId,
  taskId,
  projectMemberId,
) {
  const response = await apiFetch(
    `${tasksUrl(workspaceId, projectId)}/${taskId}/assignee`,
    await withCsrf({
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ projectMemberId }),
    }),
  )

  if (!response.ok) {
    throw await createApiError(
      response,
      'Unable to update the task assignee.',
    )
  }

  return response.json()
}

export async function changeTaskVisibility(
  workspaceId,
  projectId,
  taskId,
  visibility,
) {
  const response = await apiFetch(
    `${tasksUrl(workspaceId, projectId)}/${taskId}/visibility`,
    await withCsrf({
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ visibility }),
    }),
  )

  if (!response.ok) {
    throw await createApiError(
      response,
      'Unable to update task visibility.',
    )
  }
  return response.json()
}
