import { apiFetch, createApiError } from './apiError.js'
import { withCsrf } from './csrf.js'

const ONBOARDING_URL = '/api/v1/account/onboarding'

export async function getOnboarding() {
  const response = await apiFetch(ONBOARDING_URL, {
    credentials: 'same-origin',
  })

  if (!response.ok) {
    throw await createApiError(
      response,
      'Unable to load your registration details.',
    )
  }

  return response.json()
}

export async function completeOnboarding(profile) {
  const response = await apiFetch(
    `${ONBOARDING_URL}/complete`,
    await withCsrf({
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(profile),
    }),
  )

  if (!response.ok) {
    throw await createApiError(
      response,
      'Unable to finish registration.',
    )
  }

  return response.json()
}
