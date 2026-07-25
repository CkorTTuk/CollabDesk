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
      'Не удалось проверить текущую сессию.',
    )
  }

  return response.json()
}

export async function registerUser({ email, displayName, password }) {
  const response = await apiFetch(
    `${AUTH_URL}/register`,
    await withCsrf({
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email, displayName, password }),
    }),
  )

  if (!response.ok) {
    throw await createApiError(
      response,
      'Не удалось создать аккаунт.',
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
      'Неверный email или пароль.',
    )
  }

  if (!response.ok) {
    throw await createApiError(
      response,
      'Не удалось выполнить вход.',
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
      'Не удалось завершить сессию.',
    )
  }

  clearCsrfToken()
}
