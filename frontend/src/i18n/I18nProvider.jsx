/* oxlint-disable react/only-export-components */
import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { en } from './messages.en.js'
import { ru } from './messages.ru.js'
import { sk } from './messages.sk.js'
import {
  initialLocale,
  LOCALE_EXPLICIT_STORAGE_KEY,
  LOCALE_STORAGE_KEY,
  normalizeLocale,
} from './locale.js'

const messages = { en, ru, sk }
const I18nContext = createContext(null)

export function I18nProvider({ children }) {
  const [locale, setLocaleState] = useState(initialLocale)

  useEffect(() => {
    document.documentElement.lang = locale
  }, [locale])

  function setLocale(value, { explicit = true } = {}) {
    const next = normalizeLocale(value)
    setLocaleState(next)
    localStorage.setItem(LOCALE_STORAGE_KEY, next)
    if (explicit) localStorage.setItem(LOCALE_EXPLICIT_STORAGE_KEY, 'true')
    else localStorage.removeItem(LOCALE_EXPLICIT_STORAGE_KEY)
  }

  const value = useMemo(() => ({
    locale,
    setLocale,
    t(key, params = {}) {
      let value = messages[locale]?.[key] ?? messages.en[key]
      if (value == null) {
        if (import.meta.env.DEV) console.error(`Missing translation: ${key}`)
        value = key
      }
      return Object.entries(params).reduce(
        (text, [name, replacement]) => text.replaceAll(`{${name}}`, replacement),
        value,
      )
    },
    formatDate(value, options) {
      return new Intl.DateTimeFormat(locale, options).format(new Date(value))
    },
    formatNumber(value, options) {
      return new Intl.NumberFormat(locale, options).format(value)
    },
  }), [locale])

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}

export function useI18n() {
  const context = useContext(I18nContext)
  if (!context) throw new Error('useI18n must be used inside I18nProvider')
  return context
}
