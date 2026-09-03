import { useEffect, useRef, useState } from 'react'
import { getCurrentUser, logoutUser } from './api/authApi.js'
import {
  completeOnboarding,
  getOnboarding,
} from './api/onboardingApi.js'

const EMPTY_PROFILE = {
  firstName: '',
  lastName: '',
}

const EMPTY_BIRTH_DATE = {
  month: '',
  day: '',
  year: '',
}

const MONTHS = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
]

function parseBirthDate(value) {
  if (!value) return EMPTY_BIRTH_DATE

  const [year, month, day] = value.split('-')
  return { year, month, day }
}

function formatBirthDate({ year, month, day }) {
  return year && month && day ? `${year}-${month}-${day}` : null
}

function hasPartialBirthDate({ year, month, day }) {
  const selectedParts = [year, month, day].filter(Boolean).length
  return selectedParts > 0 && selectedParts < 3
}

function daysInMonth(year, month) {
  if (!year || !month) return 31
  return new Date(Number(year), Number(month), 0).getDate()
}

function DateDropdown({
  label,
  placeholder,
  value,
  options,
  invalid,
  numeric,
  onChange,
}) {
  const [isOpen, setIsOpen] = useState(false)
  const rootRef = useRef(null)
  const selectedOption = options.find((option) => option.value === value)

  useEffect(() => {
    if (!isOpen) return undefined

    rootRef.current
      ?.querySelector('[role="option"][aria-selected="true"]')
      ?.scrollIntoView({ block: 'nearest' })

    function handlePointerDown(event) {
      if (!rootRef.current?.contains(event.target)) setIsOpen(false)
    }

    function handleEscape(event) {
      if (event.key === 'Escape') setIsOpen(false)
    }

    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [isOpen])

  return (
    <div className="date-dropdown-field">
      <span>{label}</span>
      <div
        ref={rootRef}
        className={`date-dropdown${isOpen ? ' open' : ''}`}
      >
        <button
          type="button"
          className={`date-dropdown-trigger${selectedOption ? '' : ' placeholder'}`}
          aria-label={`Birth ${label.toLowerCase()}`}
          aria-haspopup="listbox"
          aria-expanded={isOpen}
          aria-invalid={invalid}
          onClick={() => setIsOpen((current) => !current)}
        >
          <span>{selectedOption?.label ?? placeholder}</span>
          <span className="date-dropdown-chevron" aria-hidden="true" />
        </button>
        {isOpen && (
          <div
            className={`date-dropdown-menu${numeric ? ' numeric' : ''}`}
            role="listbox"
            aria-label={`${label} options`}
          >
            {options.map((option) => (
              <button
                type="button"
                role="option"
                aria-selected={option.value === value}
                className={option.value === value ? 'selected' : ''}
                key={option.value}
                onClick={() => {
                  onChange(option.value)
                  setIsOpen(false)
                }}
              >
                {option.label}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function BirthDatePicker({ value, error, onChange }) {
  const today = new Date()
  const currentYear = today.getFullYear()
  const selectedYear = Number(value.year)
  const selectedMonth = Number(value.month)
  const maxMonth = selectedYear === currentYear ? today.getMonth() + 1 : 12
  const monthLimit = Math.max(maxMonth, selectedMonth || 0)
  const calendarDayLimit = daysInMonth(value.year, value.month)
  const maxDay = selectedYear === currentYear
    && selectedMonth === today.getMonth() + 1
    ? Math.min(calendarDayLimit, today.getDate())
    : calendarDayLimit
  const years = Array.from(
    { length: currentYear - 1899 },
    (_, index) => currentYear - index,
  )
  const monthOptions = MONTHS.slice(0, monthLimit).map((month, index) => ({
    value: String(index + 1).padStart(2, '0'),
    label: month,
  }))
  const dayOptions = Array.from({ length: maxDay }, (_, index) => ({
    value: String(index + 1).padStart(2, '0'),
    label: String(index + 1).padStart(2, '0'),
  }))
  const yearOptions = years.map((year) => ({
    value: String(year),
    label: String(year),
  }))

  function updatePart(part, nextValue) {
    const updated = { ...value, [part]: nextValue }

    if (Number(updated.year) === currentYear
        && Number(updated.month) > today.getMonth() + 1) {
      updated.month = ''
      updated.day = ''
    }

    const updatedDayLimit = daysInMonth(updated.year, updated.month)

    if (updated.day && Number(updated.day) > updatedDayLimit) {
      updated.day = ''
    }
    if (Number(updated.year) === currentYear
        && Number(updated.month) === today.getMonth() + 1
        && Number(updated.day) > today.getDate()) {
      updated.day = ''
    }
    onChange(updated)
  }

  return (
    <fieldset className="onboarding-date-field">
      <legend>Date of birth <small>Optional</small></legend>
      <div className="onboarding-date-selects">
        <DateDropdown
          label="Month"
          placeholder="Month"
          value={value.month}
          options={monthOptions}
          invalid={Boolean(error)}
          onChange={(nextValue) => updatePart('month', nextValue)}
        />
        <DateDropdown
          label="Day"
          placeholder="Day"
          value={value.day}
          options={dayOptions}
          invalid={Boolean(error)}
          numeric
          onChange={(nextValue) => updatePart('day', nextValue)}
        />
        <DateDropdown
          label="Year"
          placeholder="Year"
          value={value.year}
          options={yearOptions}
          invalid={Boolean(error)}
          numeric
          onChange={(nextValue) => updatePart('year', nextValue)}
        />
      </div>
      <div className="onboarding-date-help">
        <small className="field-hint">
          Open a field and scroll the compact list.
        </small>
        {(value.month || value.day || value.year) && (
          <button type="button" onClick={() => onChange(EMPTY_BIRTH_DATE)}>
            Clear date
          </button>
        )}
      </div>
      {error && <small className="field-error">{error}</small>}
    </fieldset>
  )
}

function OnboardingBrand() {
  return (
    <a className="brand" href="/" aria-label="CollabDesk">
      <span className="brand-mark" aria-hidden="true">
        <span />
        <span />
        <span />
      </span>
      <span>CollabDesk</span>
    </a>
  )
}

export default function OnboardingScreen({ user, onCompleted, onLogout }) {
  const [details, setDetails] = useState(null)
  const [profile, setProfile] = useState(EMPTY_PROFILE)
  const [birthDate, setBirthDate] = useState(EMPTY_BIRTH_DATE)
  const [fieldErrors, setFieldErrors] = useState({})
  const [message, setMessage] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isLoggingOut, setIsLoggingOut] = useState(false)

  useEffect(() => {
    let active = true

    async function loadDetails() {
      try {
        const onboarding = await getOnboarding()
        if (!active) return

        if (onboarding.onboardingCompleted) {
          const currentUser = await getCurrentUser()
          if (active && currentUser?.onboardingCompleted) {
            onCompleted(currentUser)
          }
          return
        }

        setDetails(onboarding)
        setProfile({
          firstName:
            onboarding.firstName ?? onboarding.suggestion?.firstName ?? '',
          lastName:
            onboarding.lastName ?? onboarding.suggestion?.lastName ?? '',
        })
        setBirthDate(parseBirthDate(onboarding.birthDate))
      } catch (error) {
        if (active) {
          setMessage(
            error.message || 'Unable to load your registration details.',
          )
        }
      } finally {
        if (active) setIsLoading(false)
      }
    }

    loadDetails()
    return () => {
      active = false
    }
  }, [onCompleted])

  async function handleSubmit(event) {
    event.preventDefault()
    setFieldErrors({})
    setMessage('')

    if (hasPartialBirthDate(birthDate)) {
      setFieldErrors({
        birthDate: 'Select month, day, and year, or leave all three empty.',
      })
      return
    }

    setIsSubmitting(true)

    try {
      await completeOnboarding({
        firstName: profile.firstName,
        lastName: profile.lastName || null,
        birthDate: formatBirthDate(birthDate),
      })
      const currentUser = await getCurrentUser()

      if (!currentUser?.onboardingCompleted) {
        throw new Error('Your updated session could not be verified.')
      }
      onCompleted(currentUser)
    } catch (error) {
      setFieldErrors(error.fieldErrors ?? {})
      setMessage(error.message || 'Unable to finish registration.')
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleLogout() {
    setMessage('')
    setIsLoggingOut(true)

    try {
      await logoutUser()
      onLogout()
    } catch (error) {
      setMessage(error.message || 'Unable to sign out.')
      setIsLoggingOut(false)
    }
  }

  const suggestion = details?.suggestion
  const avatarUrl = suggestion?.avatarUrl
  const avatarInitial = (profile.firstName || user.email || '?')
    .trim()
    .charAt(0)
    .toUpperCase()

  return (
    <main className="onboarding-page">
      <header className="onboarding-header">
        <OnboardingBrand />
        <button
          className="onboarding-logout"
          type="button"
          disabled={isLoggingOut}
          onClick={handleLogout}
        >
          {isLoggingOut ? 'Signing out…' : 'Log out'}
        </button>
      </header>

      <section className="onboarding-layout">
        <div className="onboarding-copy">
          <p className="eyebrow">One last step</p>
          <h1>Make CollabDesk yours.</h1>
          <p>
            Choose the name teammates will see. You can update these profile
            details later from your account settings.
          </p>
          <ol aria-label="Registration progress">
            <li className="complete"><span>✓</span> Account connected</li>
            <li className="active"><span>2</span> Complete your profile</li>
            <li><span>3</span> Open your workspace</li>
          </ol>
        </div>

        <div className="onboarding-card">
          <div className="onboarding-profile-preview">
            <div className="onboarding-avatar" aria-hidden="true">
              {avatarUrl ? (
                <img src={avatarUrl} alt="" referrerPolicy="no-referrer" />
              ) : avatarInitial}
            </div>
            <div>
              <strong>Profile details</strong>
              <span>{user.email}</span>
            </div>
            {suggestion && <small>Suggested by your sign-in provider</small>}
          </div>

          {message && (
            <div className="form-message error" role="alert">{message}</div>
          )}

          {isLoading ? (
            <div className="onboarding-loading">
              <span className="loading-spinner" aria-hidden="true" />
              Loading your profile…
            </div>
          ) : details ? (
            <form className="onboarding-form" onSubmit={handleSubmit}>
              <label className="form-field" htmlFor="onboarding-email">
                <span>Email</span>
                <input
                  id="onboarding-email"
                  type="email"
                  value={user.email}
                  readOnly
                  aria-readonly="true"
                />
                <small className="field-hint">Connected to this account</small>
              </label>

              <div className="onboarding-name-grid">
                <label className="form-field" htmlFor="onboarding-first-name">
                  <span>First name</span>
                  <input
                    id="onboarding-first-name"
                    type="text"
                    autoComplete="given-name"
                    maxLength={100}
                    required
                    value={profile.firstName}
                    aria-invalid={Boolean(fieldErrors.firstName)}
                    onChange={(event) => setProfile((current) => ({
                      ...current,
                      firstName: event.target.value,
                    }))}
                  />
                  {fieldErrors.firstName && (
                    <small className="field-error">{fieldErrors.firstName}</small>
                  )}
                </label>

                <label className="form-field" htmlFor="onboarding-last-name">
                  <span>Last name <small>Optional</small></span>
                  <input
                    id="onboarding-last-name"
                    type="text"
                    autoComplete="family-name"
                    maxLength={100}
                    value={profile.lastName}
                    aria-invalid={Boolean(fieldErrors.lastName)}
                    onChange={(event) => setProfile((current) => ({
                      ...current,
                      lastName: event.target.value,
                    }))}
                  />
                  {fieldErrors.lastName && (
                    <small className="field-error">{fieldErrors.lastName}</small>
                  )}
                </label>
              </div>

              <BirthDatePicker
                value={birthDate}
                error={fieldErrors.birthDate}
                onChange={(value) => {
                  setBirthDate(value)
                  setFieldErrors((current) => ({
                    ...current,
                    birthDate: undefined,
                  }))
                }}
              />

              <button className="primary-button" disabled={isSubmitting}>
                {isSubmitting ? 'Finishing registration…' : 'Finish registration'}
              </button>
            </form>
          ) : (
            <button className="primary-button" type="button" onClick={() => window.location.reload()}>
              Try again
            </button>
          )}
        </div>
      </section>
    </main>
  )
}
