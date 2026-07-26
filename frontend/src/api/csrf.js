import { apiFetch, createApiError } from './apiError.js'

let csrfToken = null

export async function getCsrfToken() {
  if (csrfToken) {
    return csrfToken
  }

  const response = await apiFetch('/csrf', {
    credentials: 'same-origin',
  })

  if (!response.ok) {
    throw await createApiError(
      response,
      'Unable to start a secure request. Please try again.',
    )
  }

  csrfToken = await response.json()
  return csrfToken
}

export function clearCsrfToken() {
  csrfToken = null
}

export async function withCsrf(options = {}) {
  const csrf = await getCsrfToken()

  return {
    ...options,
    credentials: 'same-origin',
    headers: {
      ...options.headers,
      [csrf.headerName]: csrf.token,
    },
  }
}
