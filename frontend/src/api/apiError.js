export class ApiError extends Error {
  constructor(message, status = 0, fieldErrors = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

export async function apiFetch(input, options) {
  try {
    return await fetch(input, options)
  } catch {
    throw new ApiError(
      'The service is temporarily unavailable. Please try again.',
    )
  }
}

export async function createApiError(response, fallbackMessage) {
  let body = null

  try {
    body = await response.json()
  } catch {
    // Security filter responses such as 401 and 403 can have an empty body.
  }

  const message =
    body?.detail ||
    body?.title ||
    fallbackMessage ||
    `Request failed with status ${response.status}`

  return new ApiError(message, response.status, body?.errors ?? {})
}
