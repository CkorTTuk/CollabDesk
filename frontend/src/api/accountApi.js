import { apiFetch, createApiError } from './apiError.js'
import { withCsrf } from './csrf.js'

const ACCOUNT_URL = '/api/v1/account'

async function readAccountResponse(response, fallbackMessage) {
  if (!response.ok) throw await createApiError(response, fallbackMessage)
  return response.json()
}

export async function getAccount() {
  const response = await apiFetch(ACCOUNT_URL, { credentials: 'same-origin' })
  return readAccountResponse(response, 'Unable to load your account.')
}

export async function updateProfile(profile) {
  const response = await apiFetch(
    `${ACCOUNT_URL}/profile`,
    await withCsrf({
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(profile),
    }),
  )
  return readAccountResponse(response, 'Unable to update your profile.')
}

export async function updateLocale(locale) {
  const response = await apiFetch(
    `${ACCOUNT_URL}/locale`,
    await withCsrf({
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ locale }),
    }),
  )
  return readAccountResponse(response, 'Unable to save your language.')
}

export async function uploadAvatar(file) {
  const body = new FormData()
  body.append('file', file)
  const response = await apiFetch(
    `${ACCOUNT_URL}/avatar`,
    await withCsrf({ method: 'POST', body }),
  )
  return readAccountResponse(response, 'Unable to upload your avatar.')
}

export async function removeAvatar() {
  const response = await apiFetch(
    `${ACCOUNT_URL}/avatar`,
    await withCsrf({ method: 'DELETE' }),
  )
  return readAccountResponse(response, 'Unable to remove your avatar.')
}
