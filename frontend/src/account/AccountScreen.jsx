import { useEffect, useMemo, useState } from 'react'
import { getAccount, updateProfile } from '../api/accountApi.js'
import { useI18n } from '../i18n/I18nProvider.jsx'
import AvatarSection from './AvatarSection.jsx'
import { BirthDatePicker } from '../OnboardingScreen.jsx'
import { formatBirthDate, hasPartialBirthDate, parseBirthDate } from './birthDate.js'

function profileDraft(account) {
  return {
    firstName: account.firstName ?? '',
    lastName: account.lastName ?? '',
    birthDate: parseBirthDate(account.birthDate),
  }
}

function GoogleIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path fill="#4285F4" d="M21.6 12.23c0-.71-.06-1.4-.18-2.06H12v3.9h5.38a4.6 4.6 0 0 1-2 3.02v2.53h3.24c1.9-1.75 2.98-4.32 2.98-7.39Z" /><path fill="#34A853" d="M12 22c2.7 0 4.97-.9 6.62-2.38l-3.24-2.53c-.9.6-2.05.96-3.38.96-2.6 0-4.8-1.76-5.6-4.12H3.05v2.61A10 10 0 0 0 12 22Z" /><path fill="#FBBC05" d="M6.4 13.93A6 6 0 0 1 6.08 12c0-.67.12-1.32.32-1.93V7.46H3.05A10 10 0 0 0 2 12c0 1.61.39 3.14 1.05 4.54l3.35-2.61Z" /><path fill="#EA4335" d="M12 5.95c1.47 0 2.79.5 3.82 1.5l2.87-2.87A9.63 9.63 0 0 0 12 2a10 10 0 0 0-8.95 5.46l3.35 2.61c.8-2.36 3-4.12 5.6-4.12Z" /></svg>
}

function GitHubIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 2a10 10 0 0 0-3.16 19.49c.5.09.68-.22.68-.48v-1.87c-2.78.6-3.37-1.18-3.37-1.18-.45-1.16-1.11-1.47-1.11-1.47-.91-.62.07-.61.07-.61 1 .07 1.53 1.03 1.53 1.03.9 1.53 2.35 1.09 2.92.83.09-.65.35-1.09.64-1.34-2.22-.25-4.56-1.11-4.56-4.94 0-1.09.39-1.98 1.03-2.68-.1-.25-.45-1.27.1-2.64 0 0 .84-.27 2.75 1.02A9.56 9.56 0 0 1 12 6.82c.85 0 1.71.12 2.51.34 1.91-1.29 2.75-1.02 2.75-1.02.55 1.37.2 2.39.1 2.64.64.7 1.03 1.59 1.03 2.68 0 3.84-2.34 4.68-4.57 4.93.36.31.68.92.68 1.86v2.76c0 .27.18.58.69.48A10 10 0 0 0 12 2Z" /></svg>
}

function SignInMethods({ providers, t }) {
  const externalProviders = providers.filter((provider) => provider !== 'LOCAL')
  if (externalProviders.length === 0) {
    return <div className="provider-empty">{t('account.providers.empty')}</div>
  }
  return <div className="provider-list">{externalProviders.map((provider) => {
    const normalized = provider.toLowerCase()
    const label = normalized === 'google' ? 'Google' : normalized === 'github' ? 'GitHub' : provider
    return <div className={`provider-card provider-${normalized}`} key={provider}>
      <span className="provider-icon">{normalized === 'google' ? <GoogleIcon /> : normalized === 'github' ? <GitHubIcon /> : label.slice(0, 1)}</span>
      <span><strong>{label}</strong><small>{t('account.providers.connected')}</small></span>
      <span className="provider-status" aria-label={t('account.providers.connected')}>✓</span>
    </div>
  })}</div>
}

export default function AccountScreen({ account, onAccountChange, onBack }) {
  const { t } = useI18n()
  const [draft, setDraft] = useState(() => profileDraft(account))
  const [fieldErrors, setFieldErrors] = useState({})
  const [message, setMessage] = useState('')
  const [isSaving, setIsSaving] = useState(false)
  const [hasConflict, setHasConflict] = useState(false)
  const [isEditing, setIsEditing] = useState(false)

  useEffect(() => setDraft(profileDraft(account)), [account])
  const original = useMemo(() => profileDraft(account), [account])
  const isDirty = JSON.stringify(draft) !== JSON.stringify(original)

  function updateDraft(field, value) {
    setDraft((current) => ({ ...current, [field]: value }))
    setFieldErrors((current) => ({ ...current, [field]: undefined }))
    setMessage('')
  }

  async function saveProfile(event) {
    event.preventDefault()
    setIsSaving(true)
    setMessage('')
    setFieldErrors({})
    setHasConflict(false)
    if (hasPartialBirthDate(draft.birthDate)) {
      setFieldErrors({ birthDate: t('onboarding.partialDate') })
      setIsSaving(false)
      return
    }
    try {
      const updated = await updateProfile({
        firstName: draft.firstName,
        lastName: draft.lastName || null,
        birthDate: formatBirthDate(draft.birthDate),
      })
      onAccountChange({ ...updated, onboardingCompleted: true })
      setMessage(t('account.saved'))
      setIsEditing(false)
    } catch (error) {
      setFieldErrors(error.fieldErrors ?? {})
      setHasConflict(error.status === 409)
      setMessage(error.status === 409 ? t('account.conflict') : error.message)
    } finally {
      setIsSaving(false)
    }
  }

  function cancelEditing() {
    setDraft(original)
    setFieldErrors({})
    setMessage('')
    setIsEditing(false)
  }

  async function reloadAccount() {
    const fresh = await getAccount()
    onAccountChange({ ...fresh, onboardingCompleted: true })
    setDraft(profileDraft(fresh))
    setHasConflict(false)
    setMessage('')
  }

  return (
    <main className="account-page">
      <header className="account-topbar">
        <a className="brand" href="/" onClick={(event) => { event.preventDefault(); onBack() }}>
          <span className="brand-mark" aria-hidden="true"><span /><span /><span /></span>
          <span>CollabDesk</span>
        </a>
        <div className="account-topbar-actions">
          <button className="ghost-button" type="button" onClick={onBack}>{t('account.back')}</button>
        </div>
      </header>

      <div className="account-layout">
        <header className="account-heading">
          <div><h1>{t('account.title')}</h1><p>{t('account.subtitle')}</p></div>
        </header>

        {message && <div className={`form-message${hasConflict ? ' error' : ''}`} role="status">
          {message} {hasConflict && <button type="button" onClick={reloadAccount}>{t('common.reload')}</button>}
        </div>}

        <section className="account-section account-profile-section">
          <div className="account-profile-heading"><div><h2>{t('account.profile.title')}</h2><p>{t('account.profile.help')}</p></div>{!isEditing && <button className="secondary-button account-edit-button" type="button" onClick={() => setIsEditing(true)}>{t('account.profile.edit')}</button>}</div>
          <div className="account-profile-content">
            <aside className="account-profile-avatar"><div><h3>{t('account.avatar.title')}</h3><p>{t('account.avatar.help')}</p></div><AvatarSection account={account} onAccountChange={onAccountChange} /></aside>
            <form className="account-form" onSubmit={saveProfile}>
            <div className="account-email-row"><label className="form-field"><span>{t('account.email')}</span><input value={account.email} readOnly aria-readonly="true" /></label><button className="ghost-button" type="button" disabled title={t('account.email.pending')}>{t('account.email.change')}</button></div>
            <small className="account-email-note">{t('account.email.pending')}</small>
            <div className="account-name-grid">
              <label className="form-field"><span>{t('account.firstName')}</span><input disabled={!isEditing} required maxLength={100} value={draft.firstName} onChange={(event) => updateDraft('firstName', event.target.value)} />{fieldErrors.firstName && <small className="field-error">{fieldErrors.firstName}</small>}</label>
              <label className="form-field"><span>{t('account.lastName')} <small>{t('common.optional')}</small></span><input disabled={!isEditing} maxLength={100} value={draft.lastName} onChange={(event) => updateDraft('lastName', event.target.value)} />{fieldErrors.lastName && <small className="field-error">{fieldErrors.lastName}</small>}</label>
            </div>
            <BirthDatePicker disabled={!isEditing} value={draft.birthDate} error={fieldErrors.birthDate} onChange={(value) => updateDraft('birthDate', value)} />
            {isEditing && <div className="account-form-actions">
              <button className="primary-button" disabled={!isDirty || isSaving}>{isSaving ? t('common.saving') : t('common.save')}</button>
              <button className="ghost-button" type="button" disabled={isSaving} onClick={cancelEditing}>{t('common.cancel')}</button>
            </div>}
            </form>
          </div>
        </section>

        <section className="account-section"><div><h2>{t('account.providers.title')}</h2><p>{t('account.providers.help')}</p></div><SignInMethods providers={account.providers} t={t} /></section>
        <section className="account-section"><div><h2>{t('account.security.title')}</h2><p>{t('account.security.pending')}</p></div></section>
        <section className="account-section danger"><div><h2>{t('account.danger.title')}</h2><p>{t('account.danger.pending')}</p></div></section>
      </div>
    </main>
  )
}
