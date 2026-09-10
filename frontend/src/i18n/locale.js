export const SUPPORTED_LOCALES = ['en', 'ru', 'sk']
export const DEFAULT_LOCALE = 'en'
export const LOCALE_STORAGE_KEY = 'collabdesk.locale'
export const LOCALE_EXPLICIT_STORAGE_KEY = 'collabdesk.localeExplicit'

export function normalizeLocale(value) {
  const base = String(value ?? '').trim().toLowerCase().split('-')[0]
  return SUPPORTED_LOCALES.includes(base) ? base : DEFAULT_LOCALE
}

export function initialLocale() {
  return normalizeLocale(
    localStorage.getItem(LOCALE_STORAGE_KEY) || navigator.language,
  )
}
