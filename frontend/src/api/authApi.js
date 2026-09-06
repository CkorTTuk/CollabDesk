import { apiFetch, createApiError } from './apiError.js'
import { clearCsrfToken, withCsrf } from './csrf.js'

const AUTH_URL = '/api/v1/auth'

export async function getCurrentUser() {
  const response = await apiFetch(`${AUTH_URL}/me`, {
    credentials: 'same-origin',
  })

  if (response.status === 401) {
    return null
  }

  if (!response.ok) {
    throw await createApiError(
      response,
      'Unable to check the current session.',
    )
  }

  return response.json()
}

export async function registerUser({ email, password, passwordConfirmation }) {
  const response = await apiFetch(
    `${AUTH_URL}/register`,
    await withCsrf({
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email, password, passwordConfirmation }),
    }),
  )

  if (!response.ok) {
    throw await createApiError(
      response,
      'Unable to create the account.',
    )
  }

  return response.json()
}

export async function confirmEmailVerification({ email, code }) {
  const response = await apiFetch(
    `${AUTH_URL}/email-verification/confirm`,
    await withCsrf({
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email, code }),
    }),
  )

  if (!response.ok) {
    throw await createApiError(
      response,
      'Unable to verify the email address.',
    )
  }

  clearCsrfToken()
  return response.json()
}

export async function resendEmailVerification({ email }) {
  const response = await apiFetch(
    `${AUTH_URL}/email-verification/resend`,
    await withCsrf({
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email }),
    }),
  )

  if (!response.ok) {
    throw await createApiError(
      response,
      'Unable to send another verification code.',
    )
  }

  return response.json()
}

export async function loginUser({ email, password }) {
  const form = new URLSearchParams()
  form.set('email', email)
  form.set('password', password)

  const response = await apiFetch(
    `${AUTH_URL}/login`,
    await withCsrf({
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: form,
    }),
  )

  if (response.status === 401) {
    throw await createApiError(
      response,
      'Incorrect email or password.',
    )
  }

  if (!response.ok) {
    throw await createApiError(
      response,
      'Unable to sign in.',
    )
  }

  clearCsrfToken()
  return getCurrentUser()
}

export async function logoutUser() {
  const response = await apiFetch(
    `${AUTH_URL}/logout`,
    await withCsrf({
      method: 'POST',
    }),
  )

  if (!response.ok) {
    throw await createApiError(
      response,
      'Unable to end the session.',
    )
  }

  clearCsrfToken()
}
