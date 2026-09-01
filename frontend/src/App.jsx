import { useCallback, useEffect, useRef, useState } from 'react'
import {
  getCurrentUser,
  loginUser,
  logoutUser,
  registerUser,
} from './api/authApi.js'
import {
  createWorkspace,
  getWorkspaces,
} from './api/workspaceApi.js'
import {
  createProject,
  replaceProjectAllowedRoles,
} from './api/projectApi.js'
import { getProjectAccessOverview } from './api/projectAccessOverviewApi.js'
import {
  changeTaskStatus,
  changeTaskVisibility,
  claimTask,
  createTask,
  getTaskActivities,
  getTasks,
  releaseTask,
  updateTaskAssignee,
  updateTask,
} from './api/taskApi.js'
import {
  addProjectMember,
  getProjectMembers,
  removeProjectMember,
} from './api/projectMemberApi.js'
import {
  createAccessRole,
  deleteAccessRole,
  getAccessRoles,
  updateAccessRole,
} from './api/accessRoleApi.js'
import {
  addWorkspaceMember,
  changeWorkspaceMemberRole,
  getWorkspaceMembers,
  removeWorkspaceMember,
  replaceWorkspaceMemberAccessRoles,
} from './api/memberApi.js'
import './App.css'

const EMPTY_LOGIN = {
  email: '',
  password: '',
}

const EMPTY_REGISTRATION = {
  email: '',
  displayName: '',
  password: '',
}

const TASK_COLUMNS = [
  { status: 'TODO', title: 'To do' },
  { status: 'IN_PROGRESS', title: 'In progress' },
  { status: 'DONE', title: 'Done' },
]

const MEMBER_ROLES = [
  {
    value: 'ADMIN',
    label: 'Admin',
    description: 'Manage workspace content',
  },
  {
    value: 'MEMBER',
    label: 'Member',
    description: 'Create and update work',
  },
  {
    value: 'VIEWER',
    label: 'Viewer',
    description: 'Read-only access',
  },
]

const ITEM_ACCENT_HUES = [218, 168, 28, 276, 344, 194]

const PROJECT_PERMISSIONS = [
  {
    value: 'EDIT_PROJECT',
    label: 'Edit project',
    description: 'Change project settings',
  },
]

const ACCESS_ROLE_COLORS = [
  '#4F7DF3',
  '#2AA876',
  '#E09F3E',
  '#D95D8A',
  '#8B5CF6',
  '#2D9CDB',
  '#E76F51',
  '#64748B',
]

function getItemAccentStyle(id) {
  const numericId = Number(id) || 0
  const hue = ITEM_ACCENT_HUES[
    Math.abs(numericId) % ITEM_ACCENT_HUES.length
  ]

  return { '--item-accent-hue': String(hue) }
}

function roleBadgeClassName(role, extraClass = '') {
  return `role-badge role-${role.toLowerCase()} ${extraClass}`.trim()
}

function formatRole(role) {
  return `${role.charAt(0)}${role.slice(1).toLowerCase()}`
}

function memberInitials(displayName = '') {
  return displayName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('') || '?'
}

function readPinnedIds(storageKey) {
  try {
    const value = JSON.parse(localStorage.getItem(storageKey) ?? '[]')
    return new Set(Array.isArray(value) ? value.map(String) : [])
  } catch {
    return new Set()
  }
}

function usePinnedIds(storageKey) {
  const [pinnedIds, setPinnedIds] = useState(() => readPinnedIds(storageKey))

  useEffect(() => {
    setPinnedIds(readPinnedIds(storageKey))
  }, [storageKey])

  function togglePinned(id) {
    const normalizedId = String(id)
    setPinnedIds((current) => {
      const next = new Set(current)
      if (next.has(normalizedId)) next.delete(normalizedId)
      else next.add(normalizedId)
      localStorage.setItem(storageKey, JSON.stringify([...next]))
      return next
    })
  }

  return [pinnedIds, togglePinned]
}

const THEME_STORAGE_KEY = 'collabdesk.theme'

function getInitialTheme() {
  const savedTheme = localStorage.getItem(THEME_STORAGE_KEY)

  if (savedTheme === 'light' || savedTheme === 'dark') {
    return savedTheme
  }

  return window.matchMedia('(prefers-color-scheme: dark)').matches
    ? 'dark'
    : 'light'
}

function clearSavedNavigation() {
  localStorage.removeItem('collabdesk.selectedWorkspaceId')
  localStorage.removeItem('collabdesk.selectedProjectId')
}

function Brand() {
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

function GoogleIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path fill="#4285F4" d="M21.6 12.23c0-.71-.06-1.4-.18-2.07H12v3.92h5.38a4.6 4.6 0 0 1-2 3.02v2.54h3.24c1.9-1.75 2.98-4.33 2.98-7.41Z" />
      <path fill="#34A853" d="M12 22c2.7 0 4.97-.9 6.62-2.36l-3.24-2.54c-.9.6-2.05.96-3.38.96-2.61 0-4.82-1.76-5.61-4.13H3.04v2.62A10 10 0 0 0 12 22Z" />
      <path fill="#FBBC05" d="M6.39 13.93A6 6 0 0 1 6.07 12c0-.67.11-1.32.32-1.93V7.45H3.04A10 10 0 0 0 2 12c0 1.64.39 3.19 1.04 4.55l3.35-2.62Z" />
      <path fill="#EA4335" d="M12 5.94c1.47 0 2.79.5 3.83 1.5l2.87-2.88A9.62 9.62 0 0 0 12 2a10 10 0 0 0-8.96 5.45l3.35 2.62C7.18 7.7 9.39 5.94 12 5.94Z" />
    </svg>
  )
}

function ThemeToggle({ theme, onToggle }) {
  const nextTheme = theme === 'dark' ? 'light' : 'dark'

  return (
    <button
      className="theme-toggle"
      type="button"
      onClick={onToggle}
      aria-label={`Switch to ${nextTheme} theme`}
      title={`Switch to ${nextTheme} theme`}
    >
      <span aria-hidden="true">{theme === 'dark' ? '☀' : '☾'}</span>
      <span>{theme === 'dark' ? 'Light' : 'Dark'}</span>
    </button>
  )
}

function PageBackButton({ label, current, onClick }) {
  return (
    <nav className="page-breadcrumb" aria-label="Breadcrumb">
      <button type="button" onClick={onClick}>{label}</button>
      <span aria-hidden="true">/</span>
      <strong aria-current="page">{current}</strong>
    </nav>
  )
}

function RolePicker({
  value,
  onChange,
  label = 'Role',
  disabled = false,
  compact = false,
}) {
  const [isOpen, setIsOpen] = useState(false)
  const pickerRef = useRef(null)
  const selectedRole =
    MEMBER_ROLES.find((role) => role.value === value) ?? MEMBER_ROLES[1]

  useEffect(() => {
    if (!isOpen) {
      return undefined
    }

    function handlePointerDown(event) {
      if (!pickerRef.current?.contains(event.target)) {
        setIsOpen(false)
      }
    }

    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        setIsOpen(false)
      }
    }

    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)

    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isOpen])

  function selectRole(role) {
    setIsOpen(false)

    if (role.value !== value) {
      onChange(role.value)
    }
  }

  return (
    <div
      className={`role-picker${compact ? ' role-picker-compact' : ''}`}
      ref={pickerRef}
    >
      {!compact && <span className="role-picker-label">{label}</span>}
      <button
        className={`role-picker-trigger role-picker-trigger-${selectedRole.value.toLowerCase()}`}
        type="button"
        disabled={disabled}
        aria-label={compact ? label : undefined}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        onClick={() => setIsOpen((current) => !current)}
      >
        <span
          className={`role-dot role-dot-${selectedRole.value.toLowerCase()}`}
          aria-hidden="true"
        />
        <span>{selectedRole.label}</span>
        <span className="role-picker-chevron" aria-hidden="true">
          {isOpen ? '↑' : '↓'}
        </span>
      </button>
      {isOpen && (
        <div className="role-picker-menu" role="listbox" aria-label={label}>
          {MEMBER_ROLES.map((role) => (
            <button
              className={`role-option role-option-${role.value.toLowerCase()} ${role.value === value ? 'selected' : ''}`}
              type="button"
              role="option"
              aria-selected={role.value === value}
              key={role.value}
              onClick={() => selectRole(role)}
            >
              <span
                className={`role-dot role-dot-${role.value.toLowerCase()}`}
                aria-hidden="true"
              />
              <span>
                <strong>{role.label}</strong>
                <small>{role.description}</small>
              </span>
              {role.value === value && (
                <span className="role-picker-check" aria-hidden="true">
                  ✓
                </span>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function TaskStatusPicker({ value, onChange, disabled = false }) {
  const [isOpen, setIsOpen] = useState(false)
  const pickerRef = useRef(null)
  const selectedStatus =
    TASK_COLUMNS.find((status) => status.status === value) ?? TASK_COLUMNS[0]

  useEffect(() => {
    if (!isOpen) {
      return undefined
    }

    function handlePointerDown(event) {
      if (!pickerRef.current?.contains(event.target)) {
        setIsOpen(false)
      }
    }

    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        setIsOpen(false)
      }
    }

    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)

    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isOpen])

  function selectStatus(status) {
    setIsOpen(false)

    if (status.status !== value) {
      onChange(status.status)
    }
  }

  const selectedClass = selectedStatus.status.toLowerCase()

  return (
    <div className="task-status-picker" ref={pickerRef}>
      <button
        className={`task-status-trigger task-status-trigger-${selectedClass}`}
        type="button"
        disabled={disabled}
        aria-label={`Task status: ${selectedStatus.title}`}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        onClick={() => setIsOpen((current) => !current)}
      >
        <span
          className={`task-status-dot task-status-dot-${selectedClass}`}
          aria-hidden="true"
        />
        <span>{selectedStatus.title}</span>
        <span className="task-status-chevron" aria-hidden="true">
          {isOpen ? '↑' : '↓'}
        </span>
      </button>

      {isOpen && (
        <div className="task-status-menu" role="listbox" aria-label="Task status">
          {TASK_COLUMNS.map((status) => {
            const statusClass = status.status.toLowerCase()
            const isSelected = status.status === value

            return (
              <button
                className={isSelected ? 'selected' : ''}
                type="button"
                role="option"
                aria-selected={isSelected}
                key={status.status}
                onClick={() => selectStatus(status)}
              >
                <span
                  className={`task-status-dot task-status-dot-${statusClass}`}
                  aria-hidden="true"
                />
                <span>{status.title}</span>
                {isSelected && (
                  <span className="task-status-check" aria-hidden="true">
                    ✓
                  </span>
                )}
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}

function AuthShell({ mode, onModeChange, onAuthenticated }) {
  const [loginForm, setLoginForm] = useState(EMPTY_LOGIN)
  const [registrationForm, setRegistrationForm] =
    useState(EMPTY_REGISTRATION)
  const [fieldErrors, setFieldErrors] = useState({})
  const [message, setMessage] = useState('')
  const [messageType, setMessageType] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  const isLogin = mode === 'login'

  useEffect(() => {
    const url = new URL(window.location.href)
    if (url.searchParams.get('oauth') !== 'failed') return

    setMessage('Google sign-in could not be completed. Please try again.')
    setMessageType('error')
    url.searchParams.delete('oauth')
    window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`)
  }, [])

  function changeMode(nextMode) {
    setFieldErrors({})
    setMessage('')
    setMessageType('')
    onModeChange(nextMode)
  }

  async function handleLogin(event) {
    event.preventDefault()
    setFieldErrors({})
    setMessage('')
    setMessageType('')
    setIsSubmitting(true)

    try {
      const user = await loginUser(loginForm)
      setLoginForm(EMPTY_LOGIN)
      onAuthenticated(user)
    } catch (error) {
      setFieldErrors(error.fieldErrors ?? {})
      setMessage(error.message || 'Unable to sign in.')
      setMessageType('error')
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleRegistration(event) {
    event.preventDefault()
    setFieldErrors({})
    setMessage('')
    setMessageType('')
    setIsSubmitting(true)

    try {
      const account = await registerUser(registrationForm)
      setRegistrationForm(EMPTY_REGISTRATION)
      setLoginForm((current) => ({ ...current, email: account.email }))
      onModeChange('login')
      setMessage('Account created. Sign in with your new credentials.')
      setMessageType('success')
    } catch (error) {
      setFieldErrors(error.fieldErrors ?? {})
      setMessage(error.message || 'Unable to create the account.')
      setMessageType('error')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-intro" aria-label="About CollabDesk">
        <Brand />

        <div className="intro-copy">
          <p className="eyebrow">Focused teamwork, without the noise</p>
          <h1>Projects, people, and progress in one clear workspace.</h1>
          <p className="intro-text">
            CollabDesk keeps your teams, projects, and tasks connected. Plan
            work, track progress, and manage access without losing context.
          </p>
        </div>

        <div className="feature-list" aria-label="Highlights">
          <div className="feature">
            <span className="feature-icon" aria-hidden="true">
              01
            </span>
            <div>
              <strong>Private by default</strong>
              <span>Controlled access for every workspace</span>
            </div>
          </div>
          <div className="feature">
            <span className="feature-icon" aria-hidden="true">
              02
            </span>
            <div>
              <strong>Built for teams</strong>
              <span>Workspaces, projects, tasks, and roles</span>
            </div>
          </div>
        </div>

        <p className="intro-footer">Organize · Collaborate · Deliver</p>
      </section>

      <section className="auth-panel">
        <div className="auth-card">
          <div className="mobile-brand">
            <Brand />
          </div>

          <div className="auth-heading">
            <p className="eyebrow">{isLogin ? 'Welcome back' : 'New account'}</p>
            <h2>{isLogin ? 'Sign in to CollabDesk' : 'Create your account'}</h2>
            <p>
              {isLogin
                ? 'Enter the credentials you used when registering.'
                : 'Create your profile and start organizing team work.'}
            </p>
          </div>

          {message && (
            <div
              className={
                messageType === 'error'
                  ? 'form-message error'
                  : 'form-message'
              }
              role="status"
            >
              {message}
            </div>
          )}

          <a className="google-auth-button" href="/oauth2/authorization/google">
            <GoogleIcon />
            Continue with Google
          </a>

          <div className="auth-divider" aria-hidden="true">
            <span />
            <small>or continue with email</small>
            <span />
          </div>

          {isLogin ? (
            <form className="auth-form" onSubmit={handleLogin}>
              <FormField
                id="login-email"
                label="Email"
                type="email"
                autoComplete="email"
                placeholder="you@example.com"
                value={loginForm.email}
                onChange={(value) =>
                  setLoginForm((current) => ({ ...current, email: value }))
                }
                error={fieldErrors.email}
              />
              <FormField
                id="login-password"
                label="Password"
                type="password"
                autoComplete="current-password"
                placeholder="At least 8 characters"
                value={loginForm.password}
                onChange={(value) =>
                  setLoginForm((current) => ({ ...current, password: value }))
                }
                error={fieldErrors.password}
              />
              <button className="primary-button" disabled={isSubmitting}>
                {isSubmitting ? 'Signing in…' : 'Sign in'}
              </button>
            </form>
          ) : (
            <form className="auth-form" onSubmit={handleRegistration}>
              <FormField
                id="register-name"
                label="Display name"
                type="text"
                autoComplete="name"
                placeholder="Alex Morgan"
                maxLength={100}
                value={registrationForm.displayName}
                onChange={(value) =>
                  setRegistrationForm((current) => ({
                    ...current,
                    displayName: value,
                  }))
                }
                error={fieldErrors.displayName}
              />
              <FormField
                id="register-email"
                label="Email"
                type="email"
                autoComplete="email"
                placeholder="you@example.com"
                maxLength={320}
                value={registrationForm.email}
                onChange={(value) =>
                  setRegistrationForm((current) => ({
                    ...current,
                    email: value,
                  }))
                }
                error={fieldErrors.email}
              />
              <FormField
                id="register-password"
                label="Password"
                type="password"
                autoComplete="new-password"
                placeholder="8 to 64 characters"
                minLength={8}
                maxLength={64}
                value={registrationForm.password}
                onChange={(value) =>
                  setRegistrationForm((current) => ({
                    ...current,
                    password: value,
                  }))
                }
                error={fieldErrors.password}
              />
              <button className="primary-button" disabled={isSubmitting}>
                {isSubmitting ? 'Creating account…' : 'Create account'}
              </button>
            </form>
          )}

          <p className="mode-switch">
            {isLogin ? 'New to CollabDesk?' : 'Already have an account?'}
            <button
              type="button"
              onClick={() => changeMode(isLogin ? 'register' : 'login')}
            >
              {isLogin ? 'Create account' : 'Sign in'}
            </button>
          </p>
        </div>
      </section>
    </main>
  )
}

function FormField({
  id,
  label,
  error,
  onChange,
  minLength,
  maxLength,
  ...inputProps
}) {
  const errorId = `${id}-error`

  return (
    <label className="form-field" htmlFor={id}>
      <span>{label}</span>
      <input
        id={id}
        required
        minLength={minLength}
        maxLength={maxLength}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? errorId : undefined}
        onChange={(event) => onChange(event.target.value)}
        {...inputProps}
      />
      {error && (
        <small id={errorId} className="field-error">
          {error}
        </small>
      )}
    </label>
  )
}

function Dashboard({ user, onLogout, theme, onToggleTheme }) {
  const [isLoggingOut, setIsLoggingOut] = useState(false)
  const [isSettingsOpen, setIsSettingsOpen] = useState(false)
  const [workspaceHomeRequest, setWorkspaceHomeRequest] = useState(0)
  const [error, setError] = useState('')

  async function handleLogout() {
    setError('')
    setIsLoggingOut(true)

    try {
      await logoutUser()
      onLogout()
    } catch (logoutError) {
      setError(logoutError.message || 'Unable to sign out.')
      setIsLoggingOut(false)
    }
  }

  const initials = user.displayName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toUpperCase()

  return (
    <div className="dashboard-page">
      <header className="dashboard-header">
        <div className="dashboard-sidebar-top">
          <Brand />
        </div>
        <nav className="dashboard-nav" aria-label="Main navigation">
          <button className="active" type="button" onClick={() => setWorkspaceHomeRequest((current) => current + 1)}>
            <span aria-hidden="true">◇</span>
            Workspaces
          </button>
          <button type="button" onClick={() => document.querySelector('.content-search input')?.focus()}>
            <span aria-hidden="true">⌕</span>
            Quick search
          </button>
          <button type="button" onClick={onToggleTheme}>
            <span aria-hidden="true">{theme === 'dark' ? '☀' : '☾'}</span>
            {theme === 'dark' ? 'Light theme' : 'Dark theme'}
          </button>
          <button className={isSettingsOpen ? 'active' : ''} type="button" onClick={() => setIsSettingsOpen((current) => !current)}>
            <span aria-hidden="true">⚙</span>
            Preferences
          </button>
        </nav>
        {isSettingsOpen && (
          <section className="sidebar-settings" aria-label="Interface preferences">
            <div>
              <strong>Preferences</strong>
              <span>There are no settings here yet.</span>
            </div>
          </section>
        )}
        <div className="user-menu">
          <div className="avatar" aria-hidden="true">
            {initials}
          </div>
          <div className="user-summary">
            <strong>{user.displayName}</strong>
            <span>{user.email}</span>
          </div>
          <button
            className="ghost-button"
            type="button"
            onClick={handleLogout}
            disabled={isLoggingOut}
          >
            {isLoggingOut ? 'Signing out…' : 'Sign out'}
          </button>
        </div>
      </header>

      <main className="dashboard-main">
        {error && (
          <div className="form-message error" role="alert">
            {error}
          </div>
        )}

        <WorkspaceSection user={user} homeRequest={workspaceHomeRequest} />
      </main>
    </div>
  )
}

function WorkspaceSection({ user, homeRequest }) {
  const [workspaces, setWorkspaces] = useState([])
  const [selectedWorkspace, setSelectedWorkspace] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isCreating, setIsCreating] = useState(false)
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [moduleUnavailable, setModuleUnavailable] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [form, setForm] = useState({
    name: '',
    description: '',
    allowedRoleIds: [],
    allowedWorkspaceMemberIds: [],
  })
  const [query, setQuery] = useState('')

  async function loadWorkspaces() {
    setError('')
    setModuleUnavailable(false)
    setIsLoading(true)

    try {
      const loadedWorkspaces = await getWorkspaces()
      setWorkspaces(loadedWorkspaces)
      setSelectedWorkspace(null)
    } catch (loadError) {
      if (loadError.status === 404) {
        setModuleUnavailable(true)
      } else {
        setError(loadError.message || 'Unable to load workspaces.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    loadWorkspaces()
  }, [])

  useEffect(() => {
    setSelectedWorkspace(null)
    window.scrollTo({ top: 0, behavior: 'auto' })
  }, [homeRequest])

  async function handleCreate(event) {
    event.preventDefault()
    setError('')
    setFieldErrors({})
    setIsCreating(true)

    try {
      const workspace = await createWorkspace(form)
      setWorkspaces((current) => [...current, workspace])
      setForm({ name: '', description: '' })
      setIsFormOpen(false)
    } catch (createError) {
      setFieldErrors(createError.fieldErrors ?? {})
      setError(
        createError.message || 'Unable to create the workspace.',
      )
    } finally {
      setIsCreating(false)
    }
  }

  function openWorkspace(workspace) {
    window.scrollTo({ top: 0, behavior: 'auto' })
    setSelectedWorkspace(workspace)
  }

  function closeWorkspace() {
    window.scrollTo({ top: 0, behavior: 'auto' })
    setSelectedWorkspace(null)
  }

  if (isLoading) {
    return (
      <section className="workspace-panel workspace-loading">
        <span className="loading-spinner" aria-hidden="true" />
        <p>Loading workspaces…</p>
      </section>
    )
  }

  if (moduleUnavailable) {
    return (
      <section className="workspace-placeholder">
        <div className="placeholder-icon" aria-hidden="true">
          <span />
          <span />
          <span />
        </div>
        <div>
          <p className="eyebrow">Workspace API</p>
          <h2>The workspace service is not available</h2>
          <p>
            CollabDesk expects GET and POST requests at{' '}
            <code>/api/v1/workspaces</code>. Start the latest backend version
            and try again.
          </p>
        </div>
        <button className="secondary-button" onClick={loadWorkspaces}>
          Try again
        </button>
      </section>
    )
  }

  if (selectedWorkspace) {
    return (
      <ProjectSection
        workspace={selectedWorkspace}
        user={user}
        onBack={closeWorkspace}
      />
    )
  }

  return (
    <section className="workspace-panel workspace-selector-panel">
      <div className="workspace-panel-header">
        <div>
          <p className="eyebrow">Workspace hub</p>
          <h2>Good {new Date().getHours() < 12 ? 'morning' : new Date().getHours() < 18 ? 'afternoon' : 'evening'}, {user.displayName.split(' ')[0]}</h2>
          <p>Pick up where your team left off or create a focused space for new work.</p>
        </div>
        <button
          className="secondary-button"
          type="button"
          onClick={() => {
            setError('')
            setFieldErrors({})
            setIsFormOpen((current) => !current)
          }}
        >
          {isFormOpen ? 'Cancel' : '+ New workspace'}
        </button>
      </div>

      <div className="workspace-overview-strip">
        <div className="workspace-list-title"><strong>All workspaces</strong><span>{workspaces.length}</span></div>
        <label className="content-search">
          <span aria-hidden="true">⌕</span>
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search workspaces…" />
        </label>
      </div>

      {isFormOpen && (
        <form
          className="workspace-form"
          autoComplete="off"
          onSubmit={handleCreate}
        >
          <div className="workspace-form-grid">
            <FormField
              id="workspace-name"
              label="Workspace name"
              type="text"
              name="new-workspace-name"
              autoComplete="off"
              placeholder="For example, Product Team"
              minLength={2}
              maxLength={100}
              value={form.name}
              onChange={(value) =>
                setForm((current) => ({ ...current, name: value }))
              }
              error={fieldErrors.name}
            />
            <label className="form-field" htmlFor="workspace-description">
              <span>Description <em>optional</em></span>
              <textarea
                id="workspace-description"
                name="new-workspace-description"
                autoComplete="off"
                maxLength={500}
                placeholder="What does this team work on?"
                value={form.description}
                aria-invalid={Boolean(fieldErrors.description)}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    description: event.target.value,
                  }))
                }
              />
              {fieldErrors.description && (
                <small className="field-error">
                  {fieldErrors.description}
                </small>
              )}
            </label>
          </div>
          {error && (
            <div className="form-message error" role="alert">
              {error}
            </div>
          )}
          <div className="workspace-form-actions">
            <span>You will automatically become the workspace owner.</span>
            <button className="primary-button" disabled={isCreating}>
              {isCreating ? 'Creating…' : 'Create workspace'}
            </button>
          </div>
        </form>
      )}

      {!isFormOpen && error && (
        <div className="form-message error" role="alert">
          {error}
        </div>
      )}

      {workspaces.length === 0 ? (
        <div className="workspace-empty">
          <div className="placeholder-icon" aria-hidden="true">
            <span />
            <span />
            <span />
          </div>
          <h3>No workspaces yet</h3>
          <p>Create a workspace to bring your first team together.</p>
        </div>
      ) : (
        <div className="workspace-grid">
          {workspaces.filter((workspace) => `${workspace.name} ${workspace.description ?? ''}`.toLowerCase().includes(query.trim().toLowerCase())).map((workspace) => (
            <button
              className="workspace-card"
              key={workspace.id}
              type="button"
              style={getItemAccentStyle(workspace.id)}
              onClick={() => openWorkspace(workspace)}
            >
              <div className="workspace-card-cover">
                <div className="workspace-letter" aria-hidden="true">
                  {workspace.name.trim().charAt(0).toUpperCase()}
                </div>
                <span className={roleBadgeClassName(workspace.role)}>
                  {formatRole(workspace.role)}
                </span>
              </div>
              <div className="workspace-card-body">
                <h3>{workspace.name}</h3>
                <p>
                  {workspace.description ||
                    'No workspace description has been added yet.'}
                </p>
                <div className="workspace-card-footer">
                  <span>Open workspace →</span>
                </div>
              </div>
            </button>
          ))}
        </div>
      )}
    </section>
  )
}

function AccessRoleManager({ workspace, onChange }) {
  const emptyForm = {
    name: '',
    color: ACCESS_ROLE_COLORS[0],
    permissions: [],
  }
  const [roles, setRoles] = useState([])
  const [form, setForm] = useState(emptyForm)
  const [editingRoleId, setEditingRoleId] = useState(null)
  const [isOpen, setIsOpen] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [deletingRoleId, setDeletingRoleId] = useState(null)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const isManager = ['OWNER', 'ADMIN'].includes(workspace.role)

  const loadRoles = useCallback(async () => {
    setIsLoading(true)
    setError('')
    try {
      setRoles(await getAccessRoles(workspace.id))
    } catch (loadError) {
      setError(loadError.message || 'Unable to load custom roles.')
    } finally {
      setIsLoading(false)
    }
  }, [workspace.id])

  useEffect(() => {
    loadRoles()
  }, [loadRoles])

  function resetForm() {
    setForm(emptyForm)
    setEditingRoleId(null)
    setFieldErrors({})
    setIsOpen(false)
  }

  function startEdit(role) {
    setForm({
      name: role.name,
      color: role.color,
      permissions: role.permissions ?? [],
    })
    setEditingRoleId(role.id)
    setFieldErrors({})
    setError('')
    setIsOpen(true)
  }

  function togglePermission(permission) {
    setForm((current) => ({
      ...current,
      permissions: current.permissions.includes(permission)
        ? current.permissions.filter((item) => item !== permission)
        : [...current.permissions, permission],
    }))
  }

  async function handleSave(event) {
    event.preventDefault()
    setIsSaving(true)
    setError('')
    setFieldErrors({})
    try {
      const saved = editingRoleId
        ? await updateAccessRole(
            workspace.id,
            editingRoleId,
            form,
          )
        : await createAccessRole(workspace.id, form)
      setRoles((current) =>
        editingRoleId
          ? current
              .map((role) => (role.id === saved.id ? saved : role))
              .sort((left, right) => left.name.localeCompare(right.name))
          : [...current, saved].sort((left, right) =>
              left.name.localeCompare(right.name),
            ),
      )
      onChange?.()
      resetForm()
    } catch (saveError) {
      setFieldErrors(saveError.fieldErrors ?? {})
      setError(saveError.message || 'Unable to save the role.')
    } finally {
      setIsSaving(false)
    }
  }

  async function handleDelete(role) {
    if (!window.confirm(`Delete the “${role.name}” role?`)) {
      return
    }
    setDeletingRoleId(role.id)
    setError('')
    try {
      await deleteAccessRole(workspace.id, role.id)
      setRoles((current) =>
        current.filter((item) => item.id !== role.id),
      )
      onChange?.()
    } catch (deleteError) {
      setError(deleteError.message || 'Unable to delete the role.')
    } finally {
      setDeletingRoleId(null)
    }
  }

  return (
    <section className="members-panel access-role-panel">
      <div className="members-panel-heading">
        <div>
          <p className="eyebrow">Project permissions</p>
          <h3>Custom roles</h3>
        </div>
        {isManager && (
          <button
            className="member-add-toggle"
            type="button"
            onClick={() => {
              if (isOpen) {
                resetForm()
              } else {
                setIsOpen(true)
              }
            }}
          >
            {isOpen ? 'Cancel' : '+ New role'}
          </button>
        )}
      </div>

      {isOpen && (
        <form className="access-role-form" onSubmit={handleSave}>
          <FormField
            id="access-role-name"
            label="Role name"
            type="text"
            minLength={2}
            maxLength={60}
            placeholder="For example, Developer"
            value={form.name}
            onChange={(name) =>
              setForm((current) => ({ ...current, name }))
            }
            error={fieldErrors.name}
          />

          <fieldset className="role-color-fieldset">
            <legend>Color</legend>
            <div className="role-color-palette">
              {ACCESS_ROLE_COLORS.map((color) => (
                <label key={color} title={color}>
                  <input
                    type="radio"
                    name="access-role-color"
                    value={color}
                    checked={form.color === color}
                    onChange={() =>
                      setForm((current) => ({ ...current, color }))
                    }
                  />
                  <span style={{ '--role-color': color }} />
                </label>
              ))}
            </div>
          </fieldset>

          <fieldset className="permission-fieldset">
            <legend>Permissions</legend>
            {PROJECT_PERMISSIONS.map((permission) => (
              <label className="permission-option" key={permission.value}>
                <input
                  type="checkbox"
                  checked={form.permissions.includes(permission.value)}
                  onChange={() => togglePermission(permission.value)}
                />
                <span>
                  <strong>{permission.label}</strong>
                  <small>{permission.description}</small>
                </span>
              </label>
            ))}
          </fieldset>

          <button className="primary-button" disabled={isSaving}>
            {isSaving
              ? 'Saving…'
              : editingRoleId
                ? 'Save role'
                : 'Create role'}
          </button>
        </form>
      )}

      {error && (
        <div className="form-message error" role="alert">
          {error}
        </div>
      )}

      {isLoading ? (
        <p className="project-team-state">Loading roles…</p>
      ) : roles.length === 0 ? (
        <p className="project-team-state">
          No custom roles. Project members use default permissions.
        </p>
      ) : (
        <div className="access-role-list">
          {roles.map((role) => (
            <article className="access-role-row" key={role.id}>
              <div>
                <span
                  className="custom-role-dot"
                  style={{ '--role-color': role.color }}
                  aria-hidden="true"
                />
                <strong>{role.name}</strong>
              </div>
              <small>
                {role.permissions.length === 0
                  ? 'No write permissions'
                  : `${role.permissions.length} permission${
                      role.permissions.length === 1 ? '' : 's'
                    }`}
              </small>
              {isManager && (
                <div className="access-role-actions">
                  <button
                    className="member-action-button"
                    type="button"
                    onClick={() => startEdit(role)}
                  >
                    Edit
                  </button>
                  <button
                    className="danger-button"
                    type="button"
                    disabled={deletingRoleId === role.id}
                    onClick={() => handleDelete(role)}
                  >
                    {deletingRoleId === role.id ? 'Deleting…' : 'Delete'}
                  </button>
                </div>
              )}
            </article>
          ))}
        </div>
      )}
    </section>
  )
}

function ProjectAccessDialog({
  project,
  roles,
  members,
  draft,
  saving,
  error,
  onToggleRole,
  onToggleMember,
  onSave,
  onClose,
}) {
  const dialogRef = useRef(null)

  useEffect(() => {
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialogRef.current?.focus()

    function handleKeyDown(event) {
      if (event.key === 'Escape' && !saving) onClose()
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = previousOverflow
    }
  }, [onClose, saving])

  const isOpen = draft.roleIds.length > 0 || draft.memberIds.length > 0

  return (
    <div className="access-dialog-layer" role="presentation" onMouseDown={(event) => {
      if (event.target === event.currentTarget && !saving) onClose()
    }}>
      <section
        className="access-dialog"
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="project-access-title"
        tabIndex={-1}
      >
        <header className="access-dialog-header">
          <div>
            <span className="access-dialog-kicker">Project access</span>
            <h3 id="project-access-title">Who can open {project.name}?</h3>
            <p>Leave both lists empty to keep this project open to the whole workspace.</p>
          </div>
          <button type="button" aria-label="Close project access" onClick={onClose} disabled={saving}>×</button>
        </header>

        <div className={`access-mode-summary ${isOpen ? 'restricted' : 'open'}`}>
          <span aria-hidden="true">{isOpen ? '●' : '○'}</span>
          <div>
            <strong>{isOpen ? 'Restricted project' : 'Open to workspace'}</strong>
            <small>{isOpen ? 'Owners, admins and selected people or roles can open it.' : 'Every workspace member can open this project.'}</small>
          </div>
        </div>

        <div className="access-dialog-body">
          <fieldset className="access-dialog-section">
            <legend>Roles</legend>
            <p>Anyone with a selected custom role receives access.</p>
            <div className="access-choice-grid">
              {roles.map((role) => (
                <label className={draft.roleIds.includes(role.id) ? 'selected' : ''} key={role.id}>
                  <input type="checkbox" checked={draft.roleIds.includes(role.id)} onChange={() => onToggleRole(role.id)} />
                  <span className="access-choice-check" aria-hidden="true">{draft.roleIds.includes(role.id) ? '✓' : ''}</span>
                  <span className="custom-role-chip" style={{ '--role-color': role.color }}>{role.name}</span>
                </label>
              ))}
              {roles.length === 0 && <small className="access-dialog-empty">No custom roles have been created yet.</small>}
            </div>
          </fieldset>

          <fieldset className="access-dialog-section">
            <legend>People</legend>
            <p>Give access directly, without changing a person’s workspace role.</p>
            <div className="access-people-list">
              {members.map((member) => {
                const selected = draft.memberIds.includes(member.id)
                return (
                  <label className={selected ? 'selected' : ''} key={member.id}>
                    <input type="checkbox" checked={selected} onChange={() => onToggleMember(member.id)} />
                    <span className="access-person-avatar" aria-hidden="true">{memberInitials(member.displayName)}</span>
                    <span><strong>{member.displayName}</strong><small>{member.email}</small></span>
                    <span className="access-choice-check" aria-hidden="true">{selected ? '✓' : ''}</span>
                  </label>
                )
              })}
              {members.length === 0 && <small className="access-dialog-empty">There are no other members to select.</small>}
            </div>
          </fieldset>
        </div>

        {error && <div className="form-message error" role="alert">{error}</div>}

        <footer className="access-dialog-footer">
          <small>Changes take effect as soon as you save.</small>
          <div>
            <button className="secondary-button" type="button" onClick={onClose} disabled={saving}>Cancel</button>
            <button className="primary-button" type="button" onClick={onSave} disabled={saving}>{saving ? 'Saving…' : 'Save access'}</button>
          </div>
        </footer>
      </section>
    </div>
  )
}

function ProjectSection({ workspace, user, onBack }) {
  const [projects, setProjects] = useState([])
  const [projectTeams, setProjectTeams] = useState({})
  const [accessRoles, setAccessRoles] = useState([])
  const [workspaceMembers, setWorkspaceMembers] = useState([])
  const [selectedProject, setSelectedProject] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isCreating, setIsCreating] = useState(false)
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [form, setForm] = useState({
    name: '',
    description: '',
    allowedRoleIds: [],
    allowedWorkspaceMemberIds: [],
  })
  const [projectQuery, setProjectQuery] = useState('')
  const [accessProject, setAccessProject] = useState(null)
  const [accessDraft, setAccessDraft] = useState({ roleIds: [], memberIds: [] })
  const [isSavingAccess, setIsSavingAccess] = useState(false)
  const [accessError, setAccessError] = useState('')
  const [projectDateFilter, setProjectDateFilter] = useState('ALL')
  const [projectCreatorFilter, setProjectCreatorFilter] = useState('ALL')
  const [projectAccessFilter, setProjectAccessFilter] = useState('ALL')
  const [projectSortOrder, setProjectSortOrder] = useState('RECENT')
  const [isProjectFilterOpen, setIsProjectFilterOpen] = useState(false)
  const projectFilterRef = useRef(null)
  const [pinnedProjectIds, togglePinnedProject] = usePinnedIds(
    `collabdesk.pinned-projects.${user.id}.${workspace.id}`,
  )
  const canManageRoles = ['OWNER', 'ADMIN'].includes(workspace.role)
  const projectCreators = Array.from(
    new Map(projects.map((project) => [project.createdById, {
      id: project.createdById,
      name: project.createdByDisplayName,
    }])).values(),
  )
  const activeProjectFilterCount =
    Number(projectDateFilter !== 'ALL') +
    Number(projectCreatorFilter !== 'ALL') +
    Number(projectAccessFilter !== 'ALL')
  const filteredProjects = projects.filter((project) => {
    const matchesQuery = `${project.name} ${project.description ?? ''}`
      .toLowerCase()
      .includes(projectQuery.trim().toLowerCase())
    const matchesCreator =
      projectCreatorFilter === 'ALL' ||
      String(project.createdById) === projectCreatorFilter
    const matchesAccess =
      projectAccessFilter === 'ALL' ||
      (projectAccessFilter === 'RESTRICTED' && project.restricted) ||
      (projectAccessFilter === 'WORKSPACE' && !project.restricted)
    const createdAt = new Date(project.createdAt).getTime()
    const now = new Date()
    const todayStart = new Date(
      now.getFullYear(),
      now.getMonth(),
      now.getDate(),
    ).getTime()
    const threshold = {
      TODAY: todayStart,
      LAST_7_DAYS: now.getTime() - 7 * 86_400_000,
      LAST_30_DAYS: now.getTime() - 30 * 86_400_000,
    }[projectDateFilter]
    const matchesDate =
      projectDateFilter === 'ALL' ||
      (!Number.isNaN(createdAt) && createdAt >= threshold)
    return matchesQuery && matchesCreator && matchesAccess && matchesDate
  }).sort((left, right) => {
    const pinDifference = Number(pinnedProjectIds.has(String(right.id))) -
      Number(pinnedProjectIds.has(String(left.id)))
    if (pinDifference !== 0) return pinDifference
    if (projectSortOrder === 'NAME') return left.name.localeCompare(right.name)
    const direction = projectSortOrder === 'RECENT' ? -1 : 1
    return direction * (new Date(left.createdAt) - new Date(right.createdAt))
  })

  const loadWorkspaceAccess = useCallback(async () => {
    setError('')
    setIsLoading(true)

    try {
      const [overview, loadedRoles, loadedMembers] = await Promise.all([
        getProjectAccessOverview(workspace.id),
        canManageRoles ? getAccessRoles(workspace.id) : Promise.resolve([]),
        getWorkspaceMembers(workspace.id),
      ])
      const loadedProjects = overview.projects.map((project) => ({
        id: project.projectId,
        name: project.name,
        description: project.description,
        status: project.status,
        restricted: project.restricted,
        createdById: project.createdById,
        createdByDisplayName: project.createdByDisplayName,
        createdAt: project.createdAt,
        allowedRoles: project.allowedRoles ?? [],
      }))
      const loadedTeams = overview.projects.map((project) => [
        project.projectId,
        project.members.map((member) => ({
          ...member,
          id: member.projectMemberId,
        })),
      ])
      setProjects(loadedProjects)
      setAccessRoles(loadedRoles)
      setWorkspaceMembers(loadedMembers)
      setProjectTeams(Object.fromEntries(loadedTeams))
      setSelectedProject(null)
    } catch (loadError) {
      setError(
        loadError.message ||
          'Unable to load workspace projects.',
      )
    } finally {
      setIsLoading(false)
    }
  }, [workspace.id, canManageRoles])

  useEffect(() => {
    loadWorkspaceAccess()
  }, [loadWorkspaceAccess])

  useEffect(() => {
    if (!isProjectFilterOpen) return undefined
    function closeProjectFilters(event) {
      if (event.key === 'Escape') {
        setIsProjectFilterOpen(false)
      } else if (
        event.type === 'pointerdown' &&
        !projectFilterRef.current?.contains(event.target)
      ) {
        setIsProjectFilterOpen(false)
      }
    }
    document.addEventListener('keydown', closeProjectFilters)
    document.addEventListener('pointerdown', closeProjectFilters)
    return () => {
      document.removeEventListener('keydown', closeProjectFilters)
      document.removeEventListener('pointerdown', closeProjectFilters)
    }
  }, [isProjectFilterOpen])

  async function handleCreate(event) {
    event.preventDefault()
    setError('')
    setFieldErrors({})
    setIsCreating(true)

    try {
      await createProject(workspace.id, form)
      await loadWorkspaceAccess()
      setForm({
        name: '',
        description: '',
        allowedRoleIds: [],
        allowedWorkspaceMemberIds: [],
      })
      setIsFormOpen(false)
    } catch (createError) {
      setFieldErrors(createError.fieldErrors ?? {})
      setError(createError.message || 'Unable to create the project.')
    } finally {
      setIsCreating(false)
    }
  }

  function openProject(project) {
    window.scrollTo({ top: 0, behavior: 'auto' })
    setSelectedProject(project)
  }

  function closeProject() {
    window.scrollTo({ top: 0, behavior: 'auto' })
    setSelectedProject(null)
  }

  function updateProjectTeam(projectId, team) {
    setProjectTeams((current) => ({ ...current, [projectId]: team }))
  }

  function openProjectAccess(project) {
    const directMemberIds = (projectTeams[project.id] ?? [])
      .filter((member) => member.grantsAccess)
      .map((member) => member.workspaceMemberId)
    setAccessError('')
    setAccessDraft({
      roleIds: (project.allowedRoles ?? []).map((role) => role.id),
      memberIds: directMemberIds,
    })
    setAccessProject(project)
  }

  function toggleAccessDraft(key, id) {
    setAccessDraft((current) => ({
      ...current,
      [key]: current[key].includes(id)
        ? current[key].filter((value) => value !== id)
        : [...current[key], id],
    }))
  }

  async function saveProjectAccess() {
    setIsSavingAccess(true)
    setAccessError('')
    try {
      await replaceProjectAllowedRoles(
        workspace.id,
        accessProject.id,
        accessDraft.roleIds,
      )

      const team = projectTeams[accessProject.id] ?? []
      const selectableMembers = workspaceMembers.filter(
        (member) => member.role !== 'OWNER' && member.userId !== user.id,
      )
      for (const member of selectableMembers) {
        const existing = team.find(
          (projectMember) => projectMember.workspaceMemberId === member.id,
        )
        const selected = accessDraft.memberIds.includes(member.id)
        if (selected && !existing?.grantsAccess) {
          await addProjectMember(workspace.id, accessProject.id, member.id, [])
        } else if (!selected && existing?.grantsAccess) {
          await removeProjectMember(workspace.id, accessProject.id, existing.id)
        }
      }

      setAccessProject(null)
      await loadWorkspaceAccess()
    } catch (saveError) {
      setAccessError(saveError.message || 'Unable to update project access.')
    } finally {
      setIsSavingAccess(false)
    }
  }

  if (selectedProject) {
    return (
      <TaskBoard
        workspace={workspace}
        user={user}
        project={selectedProject}
        onBack={closeProject}
      />
    )
  }

  return (
    <div className="detail-view">
      <PageBackButton
        label="Workspaces"
        current={workspace.name}
        onClick={onBack}
      />

      <div className="project-page-layout">
        <section className="workspace-panel project-panel">
          <div className="workspace-panel-header project-panel-header">
        <div
          className="project-workspace-heading"
          style={getItemAccentStyle(workspace.id)}
        >
          <span className="workspace-context-mark" aria-hidden="true">
            {workspace.name.trim().charAt(0).toUpperCase()}
          </span>
          <div>
            <h2>{workspace.name}</h2>
            <p>
              {workspace.description ||
                'Projects and tasks for this workspace.'}
            </p>
          </div>
        </div>
        <div className="workspace-header-actions">
          {['OWNER', 'ADMIN'].includes(workspace.role) && (
            <span
              className={roleBadgeClassName(
                workspace.role,
                'context-role-badge',
              )}
            >
              {formatRole(workspace.role)}
            </span>
          )}
          {canManageRoles && (
            <button
              className="secondary-button"
              type="button"
              onClick={() => {
                setError('')
                setFieldErrors({})
                setIsFormOpen((current) => !current)
              }}
            >
              {isFormOpen ? 'Cancel' : '+ New project'}
            </button>
          )}
        </div>
          </div>

          <div className="project-main-column">

      <div className="project-toolbar">
        <div className="project-view-tabs" aria-label="Project views">
          <button className="active" type="button">Active projects <span>{filteredProjects.length}</span></button>
          <button type="button" disabled>Archived</button>
        </div>
        <div className="project-toolbar-tools">
          <div className="content-search compact project-search">
            <span aria-hidden="true">⌕</span>
            <input aria-label="Search projects" value={projectQuery} onChange={(event) => setProjectQuery(event.target.value)} placeholder="Search projects…" />
            <div className="task-filter-wrap" ref={projectFilterRef}>
            <button
              className={`task-filter-trigger ${isProjectFilterOpen || activeProjectFilterCount > 0 ? 'active' : ''}`}
              type="button"
              aria-label="Filter and sort projects"
              aria-expanded={isProjectFilterOpen}
              onClick={() => setIsProjectFilterOpen((current) => !current)}
            >
              <svg viewBox="0 0 20 20" aria-hidden="true">
                <path d="M3 5h14M6 10h8M8.5 15h3" />
              </svg>
              {activeProjectFilterCount > 0 && <span>{activeProjectFilterCount}</span>}
            </button>
            {isProjectFilterOpen && (
              <div className="task-filter-popover project-filter-popover">
                <div className="task-filter-heading">
                  <div><strong>Filter projects</strong><span>Refine this workspace</span></div>
                  {activeProjectFilterCount > 0 && (
                    <button type="button" onClick={() => {
                      setProjectDateFilter('ALL')
                      setProjectCreatorFilter('ALL')
                      setProjectAccessFilter('ALL')
                    }}>Clear</button>
                  )}
                </div>
                <fieldset>
                  <legend>Created</legend>
                  <div className="task-filter-options compact-options">
                    {[
                      ['ALL', 'Any time'],
                      ['TODAY', 'Today'],
                      ['LAST_7_DAYS', '7 days'],
                      ['LAST_30_DAYS', '30 days'],
                    ].map(([value, label]) => (
                      <button className={projectDateFilter === value ? 'selected' : ''} type="button" key={value} onClick={() => setProjectDateFilter(value)}>{label}</button>
                    ))}
                  </div>
                </fieldset>
                <fieldset>
                  <legend>Created by</legend>
                  <div className="task-filter-options project-creator-options">
                    <button className={projectCreatorFilter === 'ALL' ? 'selected' : ''} type="button" onClick={() => setProjectCreatorFilter('ALL')}>Everyone</button>
                    {projectCreators.map((creator) => (
                      <button className={projectCreatorFilter === String(creator.id) ? 'selected' : ''} type="button" key={creator.id} onClick={() => setProjectCreatorFilter(String(creator.id))}>
                        <span className="filter-avatar">{memberInitials(creator.name)}</span>{creator.name}
                      </button>
                    ))}
                  </div>
                </fieldset>
                <fieldset>
                  <legend>Access</legend>
                  <div className="task-filter-options compact-options">
                    <button className={projectAccessFilter === 'ALL' ? 'selected' : ''} type="button" onClick={() => setProjectAccessFilter('ALL')}>Any access</button>
                    <button className={projectAccessFilter === 'WORKSPACE' ? 'selected' : ''} type="button" onClick={() => setProjectAccessFilter('WORKSPACE')}>Workspace</button>
                    <button className={projectAccessFilter === 'RESTRICTED' ? 'selected' : ''} type="button" onClick={() => setProjectAccessFilter('RESTRICTED')}>Restricted</button>
                  </div>
                </fieldset>
                <fieldset>
                  <legend>Order by</legend>
                  <div className="task-filter-options compact-options">
                    <button className={projectSortOrder === 'RECENT' ? 'selected' : ''} type="button" onClick={() => setProjectSortOrder('RECENT')}>Newest first</button>
                    <button className={projectSortOrder === 'OLDEST' ? 'selected' : ''} type="button" onClick={() => setProjectSortOrder('OLDEST')}>Oldest first</button>
                    <button className={projectSortOrder === 'NAME' ? 'selected' : ''} type="button" onClick={() => setProjectSortOrder('NAME')}>Name A–Z</button>
                  </div>
                </fieldset>
              </div>
            )}
            </div>
          </div>
        </div>
      </div>

      {isFormOpen && (
        <form
          className="workspace-form"
          autoComplete="off"
          onSubmit={handleCreate}
        >
          <div className="workspace-form-grid">
            <FormField
              id="project-name"
              label="Project name"
              type="text"
              name="new-project-name"
              autoComplete="off"
              placeholder="For example, CollabDesk MVP"
              minLength={2}
              maxLength={100}
              value={form.name}
              onChange={(value) =>
                setForm((current) => ({ ...current, name: value }))
              }
              error={fieldErrors.name}
            />
            <label className="form-field" htmlFor="project-description">
              <span>
                Description <em>optional</em>
              </span>
              <textarea
                id="project-description"
                name="new-project-description"
                autoComplete="off"
                maxLength={500}
                placeholder="What outcome should this project deliver?"
                value={form.description}
                aria-invalid={Boolean(fieldErrors.description)}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    description: event.target.value,
                  }))
                }
              />
              {fieldErrors.description && (
                <small className="field-error">
                  {fieldErrors.description}
                </small>
              )}
            </label>
          </div>

          <fieldset className="project-create-access">
            <legend>Allowed roles</legend>
            <p>
              Choose roles only when this project should be restricted. With no
              selection, every workspace member can open it.
            </p>
            <div className="project-create-access-options">
              {accessRoles.map((role) => (
                <label key={role.id}>
                  <input
                    type="checkbox"
                    checked={form.allowedRoleIds.includes(role.id)}
                    onChange={() => setForm((current) => ({
                      ...current,
                      allowedRoleIds: current.allowedRoleIds.includes(role.id)
                        ? current.allowedRoleIds.filter((id) => id !== role.id)
                        : [...current.allowedRoleIds, role.id],
                    }))}
                  />
                  <span className="custom-role-chip" style={{ '--role-color': role.color }}>
                    {role.name}
                  </span>
                </label>
              ))}
              {accessRoles.length === 0 && <small>No custom roles yet.</small>}
            </div>
          </fieldset>

          <fieldset className="project-create-access">
            <legend>Allowed people</legend>
            <div className="project-create-access-options">
              {workspaceMembers
                .filter((member) => member.role !== 'OWNER' && member.userId !== user.id)
                .map((member) => (
                  <label key={member.id}>
                    <input
                      type="checkbox"
                      checked={form.allowedWorkspaceMemberIds.includes(member.id)}
                      onChange={() => setForm((current) => ({
                        ...current,
                        allowedWorkspaceMemberIds: current.allowedWorkspaceMemberIds.includes(member.id)
                          ? current.allowedWorkspaceMemberIds.filter((id) => id !== member.id)
                          : [...current.allowedWorkspaceMemberIds, member.id],
                      }))}
                    />
                    <span>{member.displayName}</span>
                    <small>{formatRole(member.role)}</small>
                  </label>
                ))}
            </div>
          </fieldset>

          {error && (
            <div className="form-message error" role="alert">
              {error}
            </div>
          )}

          <div className="workspace-form-actions">
            <span>The project will be created in {workspace.name}.</span>
            <button className="primary-button" disabled={isCreating}>
              {isCreating ? 'Creating…' : 'Create project'}
            </button>
          </div>
        </form>
      )}

      {!isFormOpen && error && (
        <div className="project-load-error">
          <div className="form-message error" role="alert">
            {error}
          </div>
          <button className="secondary-button" onClick={loadWorkspaceAccess}>
            Try again
          </button>
        </div>
      )}

      {isLoading ? (
        <div className="project-loading">
          <span className="loading-spinner" aria-hidden="true" />
          <p>Loading projects…</p>
        </div>
      ) : projects.length === 0 && !error ? (
        <div className="workspace-empty project-empty">
          <div className="project-empty-mark" aria-hidden="true">
            P
          </div>
          <h3>No projects in this workspace</h3>
          <p>Create the first project to start organizing work.</p>
        </div>
      ) : filteredProjects.length === 0 ? (
        <div className="workspace-empty project-empty project-filter-empty">
          <div className="project-empty-mark" aria-hidden="true">⌕</div>
          <h3>No matching projects</h3>
          <p>Change the search text or clear one of the active filters.</p>
          <button className="secondary-button" type="button" onClick={() => {
            setProjectQuery('')
            setProjectDateFilter('ALL')
            setProjectCreatorFilter('ALL')
            setProjectAccessFilter('ALL')
          }}>Clear filters</button>
        </div>
      ) : (
        <div className="project-grid">
          {filteredProjects.map((project) => (
            <article
              className="project-card"
              key={project.id}
              style={getItemAccentStyle(project.id + 1)}
            >
              <span className="project-card-accent" aria-hidden="true" />
              <div className="project-card-top">
                <span className="project-status">
                  <span aria-hidden="true" />
                  {project.status === 'ACTIVE' ? 'Active' : project.status}
                </span>
                <div className="project-card-access-controls">
                  <button
                    className={`pin-button ${pinnedProjectIds.has(String(project.id)) ? 'active' : ''}`}
                    type="button"
                    aria-label={pinnedProjectIds.has(String(project.id)) ? `Unpin ${project.name}` : `Pin ${project.name}`}
                    aria-pressed={pinnedProjectIds.has(String(project.id))}
                    title={pinnedProjectIds.has(String(project.id)) ? 'Unpin project' : 'Pin project'}
                    onClick={() => togglePinnedProject(project.id)}
                  >
                    <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m7 3 6 0-.8 4 2.3 2.3v1.2h-3.7L10 17l-.8-6.5H5.5V9.3L7.8 7 7 3Z" /></svg>
                  </button>
                  <span className={`visibility-badge visibility-${project.restricted ? 'restricted' : 'workspace'}`}>
                    {project.restricted ? 'Restricted' : 'Workspace'}
                  </span>
                  {canManageRoles && (
                    <button
                      className="project-access-button"
                      type="button"
                      aria-label={`Edit access to ${project.name}`}
                      title="Edit project access"
                      onClick={() => openProjectAccess(project)}
                    >
                      <svg viewBox="0 0 20 20" aria-hidden="true">
                        <path d="M3.5 6.25h8.5M15.5 6.25h1M3.5 13.75h1M8 13.75h8.5" />
                        <circle cx="13.75" cy="6.25" r="1.75" />
                        <circle cx="6.25" cy="13.75" r="1.75" />
                      </svg>
                    </button>
                  )}
                </div>
              </div>
              <div
                className="project-card-open"
                role="button"
                tabIndex={0}
                onClick={() => openProject(project)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    openProject(project)
                  }
                }}
              >
                <button className="project-card-copy" type="button" onClick={() => openProject(project)}>
                  <h3>{project.name}</h3>
                  <p>{project.description || 'No project description has been added yet.'}</p>
                </button>
                <span className="project-card-footer">
                  <CreatorProfile
                    creator={{
                      displayName: project.createdByDisplayName,
                      email: workspaceMembers.find((member) => member.userId === project.createdById)?.email,
                      role: workspaceMembers.find((member) => member.userId === project.createdById)?.role,
                    }}
                    createdAt={project.createdAt}
                    entityLabel="Project creator"
                  />
                  <button className="project-open-link" type="button" onClick={() => openProject(project)}>Open project <span aria-hidden="true">→</span></button>
                </span>
              </div>
            </article>
          ))}
        </div>
      )}
          </div>
        </section>

        <aside
          className="workspace-members-column"
          aria-label="Workspace members"
        >
          {canManageRoles && (
            <AccessRoleManager
              workspace={workspace}
              onChange={loadWorkspaceAccess}
            />
          )}
          <WorkspaceMembers
            workspace={workspace}
            projects={projects}
            projectTeams={projectTeams}
            accessRoles={accessRoles}
            members={workspaceMembers}
            onMembersChange={setWorkspaceMembers}
            onProjectTeamChange={updateProjectTeam}
          />
        </aside>
      </div>
      {accessProject && (
        <ProjectAccessDialog
          project={accessProject}
          roles={accessRoles}
          members={workspaceMembers.filter(
            (member) => member.role !== 'OWNER' && member.userId !== user.id,
          )}
          draft={accessDraft}
          saving={isSavingAccess}
          error={accessError}
          onToggleRole={(id) => toggleAccessDraft('roleIds', id)}
          onToggleMember={(id) => toggleAccessDraft('memberIds', id)}
          onSave={saveProjectAccess}
          onClose={() => {
            if (!isSavingAccess) setAccessProject(null)
          }}
        />
      )}
    </div>
  )
}

function CreatorProfile({ creator, createdAt, entityLabel, compact = false }) {
  const [isOpen, setIsOpen] = useState(false)
  const rootRef = useRef(null)

  useEffect(() => {
    if (!isOpen) return undefined
    function closeProfile(event) {
      if (event.key === 'Escape') setIsOpen(false)
      if (
        event.type === 'pointerdown' &&
        !rootRef.current?.contains(event.target)
      ) setIsOpen(false)
    }
    document.addEventListener('keydown', closeProfile)
    document.addEventListener('pointerdown', closeProfile)
    return () => {
      document.removeEventListener('keydown', closeProfile)
      document.removeEventListener('pointerdown', closeProfile)
    }
  }, [isOpen])

  return (
    <div
      className={`task-creator-profile ${compact ? 'compact' : ''}`}
      ref={rootRef}
      onClick={(event) => event.stopPropagation()}
      onKeyDown={(event) => event.stopPropagation()}
    >
      <button
        className="task-creator-trigger"
        type="button"
        aria-label={`Open ${creator.displayName} profile`}
        aria-expanded={isOpen}
        onClick={() => setIsOpen((current) => !current)}
      >
        <span aria-hidden="true">{memberInitials(creator.displayName)}</span>
        {!compact && (
          <span><small>Created by</small><strong>{creator.displayName}</strong></span>
        )}
      </button>
      {isOpen && (
        <section className="task-creator-popover" role="dialog" aria-label={`${creator.displayName} profile`}>
          <span className="task-creator-popover-avatar" aria-hidden="true">{memberInitials(creator.displayName)}</span>
          <div>
            <strong>{creator.displayName}</strong>
            <span>{creator.email || 'Workspace member'}</span>
          </div>
          <button type="button" aria-label="Close profile" onClick={() => setIsOpen(false)}>×</button>
          <footer>
            <span>{creator.role ? formatRole(creator.role) : entityLabel}</span>
            <time dateTime={createdAt}>{new Date(createdAt).toLocaleDateString()}</time>
          </footer>
        </section>
      )}
    </div>
  )
}

function TaskCreatorProfile({ task, compact = false }) {
  return (
    <CreatorProfile
      creator={{ displayName: task.createdByDisplayName, email: task.createdByEmail }}
      createdAt={task.createdAt}
      entityLabel="Task creator"
      compact={compact}
    />
  )
}

function TaskBoard({
  workspace,
  user,
  project,
  onBack,
}) {
  const [tasks, setTasks] = useState([])
  const [projectMembers, setProjectMembers] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [isCreating, setIsCreating] = useState(false)
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [updatingTaskId, setUpdatingTaskId] = useState(null)
  const [editingTaskId, setEditingTaskId] = useState(null)
  const [editTaskForm, setEditTaskForm] = useState({
    title: '',
    description: '',
  })
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [form, setForm] = useState({ title: '', description: '' })
  const [taskQuery, setTaskQuery] = useState('')
  const [assigneeFilter, setAssigneeFilter] = useState('ALL')
  const [dateFilter, setDateFilter] = useState('ALL')
  const [sortOrder, setSortOrder] = useState('RECENT')
  const [isFilterOpen, setIsFilterOpen] = useState(false)
  const [taskView, setTaskView] = useState('board')
  const [openActivities, setOpenActivities] = useState({})
  const [activitiesByTask, setActivitiesByTask] = useState({})
  const [loadingActivityId, setLoadingActivityId] = useState(null)
  const [expandedTaskId, setExpandedTaskId] = useState(null)
  const taskFilterRef = useRef(null)
  const [pinnedTaskIds, togglePinnedTask] = usePinnedIds(
    `collabdesk.pinned-tasks.${user.id}.${workspace.id}.${project.id}`,
  )
  const isManager = ['OWNER', 'ADMIN'].includes(workspace.role)
  const canCreateTask = workspace.role !== 'VIEWER'
  const isAssignee = (task) => task.assignee?.userId === user.id
  const canManageTask = (task) =>
    isManager || task.createdById === user.id || isAssignee(task)
  const activeFilterCount =
    Number(assigneeFilter !== 'ALL') + Number(dateFilter !== 'ALL')
  const visibleTasks = tasks.filter((task) => {
    const matchesQuery = `${task.title} ${task.description ?? ''}`
      .toLowerCase()
      .includes(taskQuery.trim().toLowerCase())
    const matchesAssignee =
      assigneeFilter === 'ALL' ||
      (assigneeFilter === 'UNASSIGNED' && !task.assignee) ||
      String(task.assignee?.projectMemberId) === assigneeFilter
    const createdAt = new Date(task.createdAt).getTime()
    const now = new Date()
    const todayStart = new Date(
      now.getFullYear(),
      now.getMonth(),
      now.getDate(),
    ).getTime()
    const dateThreshold = {
      TODAY: todayStart,
      LAST_7_DAYS: now.getTime() - 7 * 86_400_000,
      LAST_30_DAYS: now.getTime() - 30 * 86_400_000,
    }[dateFilter]
    const matchesDate =
      dateFilter === 'ALL' ||
      (!Number.isNaN(createdAt) && createdAt >= dateThreshold)
    return matchesQuery && matchesAssignee && matchesDate
  }).sort((left, right) => {
    const pinDifference = Number(pinnedTaskIds.has(String(right.id))) -
      Number(pinnedTaskIds.has(String(left.id)))
    if (pinDifference !== 0) return pinDifference
    const direction = sortOrder === 'RECENT' ? -1 : 1
    return direction * (new Date(left.createdAt) - new Date(right.createdAt))
  })

  const loadTasks = useCallback(async () => {
    setError('')
    setIsLoading(true)

    try {
      const [loadedTasks, loadedMembers] = await Promise.all([
        getTasks(workspace.id, project.id),
        getProjectMembers(workspace.id, project.id),
      ])
      setTasks(loadedTasks)
      setProjectMembers(loadedMembers)
    } catch (loadError) {
      setError(loadError.message || 'Unable to load tasks.')
    } finally {
      setIsLoading(false)
    }
  }, [workspace.id, project.id])

  useEffect(() => {
    loadTasks()
  }, [loadTasks])

  useEffect(() => {
    if (!isFilterOpen) return undefined
    function closeFilters(event) {
      if (event.key === 'Escape') {
        setIsFilterOpen(false)
      } else if (
        event.type === 'pointerdown' &&
        !taskFilterRef.current?.contains(event.target)
      ) {
        setIsFilterOpen(false)
      }
    }
    document.addEventListener('keydown', closeFilters)
    document.addEventListener('pointerdown', closeFilters)
    return () => {
      document.removeEventListener('keydown', closeFilters)
      document.removeEventListener('pointerdown', closeFilters)
    }
  }, [isFilterOpen])

  async function handleCreate(event) {
    event.preventDefault()
    setError('')
    setFieldErrors({})
    setIsCreating(true)

    try {
      const task = await createTask(workspace.id, project.id, form)
      setTasks((current) => [...current, task])
      setForm({ title: '', description: '' })
      setIsFormOpen(false)
    } catch (createError) {
      setFieldErrors(createError.fieldErrors ?? {})
      setError(createError.message || 'Unable to create the task.')
    } finally {
      setIsCreating(false)
    }
  }

  async function handleStatusChange(taskId, status) {
    setError('')
    setUpdatingTaskId(taskId)

    try {
      const updatedTask = await changeTaskStatus(
        workspace.id,
        project.id,
        taskId,
        status,
      )
      setTasks((current) =>
        current.map((task) =>
          task.id === updatedTask.id ? updatedTask : task,
        ),
      )
      invalidateActivity(taskId)
    } catch (updateError) {
      setError(
        updateError.message || 'Unable to update the task status.',
      )
    } finally {
      setUpdatingTaskId(null)
    }
  }

  function startTaskEdit(task) {
    setEditingTaskId(task.id)
    setEditTaskForm({
      title: task.title,
      description: task.description ?? '',
    })
    setError('')
  }

  async function handleTaskEdit(event, taskId) {
    event.preventDefault()
    setUpdatingTaskId(taskId)
    setError('')
    try {
      const updatedTask = await updateTask(
        workspace.id,
        project.id,
        taskId,
        editTaskForm,
      )
      setTasks((current) =>
        current.map((task) =>
          task.id === updatedTask.id ? updatedTask : task,
        ),
      )
      invalidateActivity(taskId)
      setEditingTaskId(null)
    } catch (updateError) {
      setError(updateError.message || 'Unable to edit the task.')
    } finally {
      setUpdatingTaskId(null)
    }
  }

  async function handleAssigneeChange(taskId, projectMemberId) {
    setError('')
    setUpdatingTaskId(taskId)
    try {
      const updatedTask = await updateTaskAssignee(
        workspace.id,
        project.id,
        taskId,
        projectMemberId,
      )
      setTasks((current) =>
        current.map((task) =>
          task.id === updatedTask.id ? updatedTask : task,
        ),
      )
      invalidateActivity(taskId)
    } catch (updateError) {
      setError(
        updateError.message || 'Unable to update the task assignee.',
      )
      throw updateError
    } finally {
      setUpdatingTaskId(null)
    }
  }

  async function handleClaimChange(task, release = false) {
    setError('')
    setUpdatingTaskId(task.id)
    try {
      const updatedTask = release
        ? await releaseTask(workspace.id, project.id, task.id)
        : await claimTask(workspace.id, project.id, task.id)
      setTasks((current) => current.map((item) =>
        item.id === updatedTask.id ? updatedTask : item,
      ))
      setActivitiesByTask((current) => {
        const next = { ...current }
        delete next[task.id]
        return next
      })
    } catch (updateError) {
      setError(updateError.message || 'Unable to update the task assignee.')
    } finally {
      setUpdatingTaskId(null)
    }
  }

  async function toggleActivity(taskId) {
    if (openActivities[taskId]) {
      setOpenActivities((current) => ({ ...current, [taskId]: false }))
      return
    }
    setOpenActivities((current) => ({ ...current, [taskId]: true }))
    if (activitiesByTask[taskId]) return
    setLoadingActivityId(taskId)
    try {
      const activities = await getTaskActivities(
          workspace.id,
          project.id,
          taskId,
        )
      setActivitiesByTask((current) => ({ ...current, [taskId]: activities }))
    } catch (loadError) {
      setError(loadError.message || 'Unable to load task activity.')
    } finally {
      setLoadingActivityId(null)
    }
  }

  function invalidateActivity(taskId) {
    setActivitiesByTask((current) => {
      const next = { ...current }
      delete next[taskId]
      return next
    })
    setOpenActivities((current) => ({ ...current, [taskId]: false }))
  }

  async function handleTaskVisibilityChange(taskId, visibility) {
    setError('')
    setUpdatingTaskId(taskId)
    try {
      const updatedTask = await changeTaskVisibility(
        workspace.id,
        project.id,
        taskId,
        visibility,
      )
      setTasks((current) =>
        current.map((task) =>
          task.id === updatedTask.id ? updatedTask : task,
        ),
      )
      invalidateActivity(taskId)
    } catch (updateError) {
      setError(
        updateError.message || 'Unable to update task visibility.',
      )
    } finally {
      setUpdatingTaskId(null)
    }
  }

  return (
    <div className="detail-view">
      <PageBackButton
        label={workspace.name}
        current={project.name}
        onClick={onBack}
      />

      <div className="task-page-layout task-page-layout-single">
      <section className="workspace-panel task-panel">
        <div className="workspace-panel-header task-panel-header">
        <div>
          <p className="eyebrow">
            {workspace.name} · Project #{project.id}
          </p>
          <h2>{project.name}</h2>
          <p>
            {project.description ||
              'Manage project tasks and keep their status up to date.'}
          </p>
          <div className="project-header-meta">
            <CreatorProfile
              creator={{
                displayName: project.createdByDisplayName,
                email: projectMembers.find((member) => member.userId === project.createdById)?.email,
                role: projectMembers.find((member) => member.userId === project.createdById)?.workspaceRole,
              }}
              createdAt={project.createdAt}
              entityLabel="Project creator"
            />
            <span className={`visibility-badge visibility-${project.restricted ? 'restricted' : 'workspace'}`}>
              {project.restricted ? 'Restricted access' : 'Workspace access'}
            </span>
          </div>
        </div>
        <div className="workspace-header-actions">
          {['OWNER', 'ADMIN'].includes(workspace.role) && (
            <span
              className={roleBadgeClassName(
                workspace.role,
                'context-role-badge',
              )}
            >
              {formatRole(workspace.role)}
            </span>
          )}
          {canCreateTask && (
            <button
              className="secondary-button"
              type="button"
              onClick={() => {
                setError('')
                setFieldErrors({})
                setIsFormOpen((current) => !current)
              }}
            >
              {isFormOpen ? 'Cancel' : '+ New task'}
            </button>
          )}
        </div>
        </div>

      {isFormOpen && (
        <form
          className="workspace-form task-form"
          autoComplete="off"
          onSubmit={handleCreate}
        >
          <div className="workspace-form-grid">
            <FormField
              id="task-title"
              label="Task title"
              type="text"
              name="new-task-title"
              autoComplete="off"
              placeholder="For example, add the API client"
              minLength={2}
              maxLength={150}
              value={form.title}
              onChange={(value) =>
                setForm((current) => ({ ...current, title: value }))
              }
              error={fieldErrors.title}
            />
            <label className="form-field" htmlFor="task-description">
              <span>
                Description <em>optional</em>
              </span>
              <textarea
                id="task-description"
                name="new-task-description"
                autoComplete="off"
                maxLength={1000}
                placeholder="What needs to be done?"
                value={form.description}
                aria-invalid={Boolean(fieldErrors.description)}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    description: event.target.value,
                  }))
                }
              />
              {fieldErrors.description && (
                <small className="field-error">
                  {fieldErrors.description}
                </small>
              )}
            </label>
          </div>

          {error && (
            <div className="form-message error" role="alert">
              {error}
            </div>
          )}

          <div className="workspace-form-actions">
            <span>New tasks start in the “To do” column.</span>
            <button className="primary-button" disabled={isCreating}>
              {isCreating ? 'Creating…' : 'Create task'}
            </button>
          </div>
        </form>
      )}

      {!isFormOpen && error && (
        <div className="project-load-error">
          <div className="form-message error" role="alert">
            {error}
          </div>
          <button className="secondary-button" onClick={loadTasks}>
            Refresh board
          </button>
        </div>
      )}

      <div className="task-toolbar">
        <div className="task-view-switcher" aria-label="Task view">
          <button className={taskView === 'board' ? 'active' : ''} type="button" onClick={() => setTaskView('board')}>Board</button>
          <button className={taskView === 'list' ? 'active' : ''} type="button" onClick={() => setTaskView('list')}>List</button>
        </div>
        <div className="content-search compact task-search">
          <span aria-hidden="true">⌕</span>
          <input aria-label="Search tasks" value={taskQuery} onChange={(event) => setTaskQuery(event.target.value)} placeholder="Search tasks…" />
          <div className="task-filter-wrap" ref={taskFilterRef}>
            <button
              className={`task-filter-trigger ${isFilterOpen || activeFilterCount > 0 ? 'active' : ''}`}
              type="button"
              aria-label="Filter and sort tasks"
              aria-expanded={isFilterOpen}
              onClick={() => setIsFilterOpen((current) => !current)}
            >
              <svg viewBox="0 0 20 20" aria-hidden="true">
                <path d="M3 5h14M6 10h8M8.5 15h3" />
              </svg>
              {activeFilterCount > 0 && <span>{activeFilterCount}</span>}
            </button>
            {isFilterOpen && (
              <div className="task-filter-popover">
                <div className="task-filter-heading">
                  <div><strong>Filter tasks</strong><span>Refine this view</span></div>
                  {activeFilterCount > 0 && (
                    <button type="button" onClick={() => { setAssigneeFilter('ALL'); setDateFilter('ALL') }}>Clear</button>
                  )}
                </div>
                <fieldset>
                  <legend>Created</legend>
                  <div className="task-filter-options compact-options">
                    {[
                      ['ALL', 'Any time'],
                      ['TODAY', 'Today'],
                      ['LAST_7_DAYS', '7 days'],
                      ['LAST_30_DAYS', '30 days'],
                    ].map(([value, label]) => (
                      <button className={dateFilter === value ? 'selected' : ''} type="button" key={value} onClick={() => setDateFilter(value)}>{label}</button>
                    ))}
                  </div>
                </fieldset>
                <fieldset>
                  <legend>Assigned to</legend>
                  <div className="task-filter-options assignee-options">
                    <button className={assigneeFilter === 'ALL' ? 'selected' : ''} type="button" onClick={() => setAssigneeFilter('ALL')}><span className="filter-avatar all">∞</span>Everyone</button>
                    <button className={assigneeFilter === 'UNASSIGNED' ? 'selected' : ''} type="button" onClick={() => setAssigneeFilter('UNASSIGNED')}><span className="filter-avatar empty">—</span>Unassigned</button>
                    {projectMembers.map((member) => (
                      <button className={assigneeFilter === String(member.id) ? 'selected' : ''} type="button" key={member.id} onClick={() => setAssigneeFilter(String(member.id))}>
                        <span className="filter-avatar">{memberInitials(member.displayName)}</span>{member.displayName}
                      </button>
                    ))}
                  </div>
                </fieldset>
                <fieldset>
                  <legend>Order</legend>
                  <div className="task-filter-options compact-options">
                    <button className={sortOrder === 'RECENT' ? 'selected' : ''} type="button" onClick={() => setSortOrder('RECENT')}>Newest first</button>
                    <button className={sortOrder === 'OLDEST' ? 'selected' : ''} type="button" onClick={() => setSortOrder('OLDEST')}>Oldest first</button>
                  </div>
                </fieldset>
              </div>
            )}
          </div>
        </div>
        <span className="task-result-count">{visibleTasks.length} {visibleTasks.length === 1 ? 'task' : 'tasks'}</span>
      </div>

      {isLoading ? (
        <div className="project-loading">
          <span className="loading-spinner" aria-hidden="true" />
          <p>Loading tasks…</p>
        </div>
      ) : (
        <div className={`task-board task-board-${taskView}`}>
          {TASK_COLUMNS.map((column) => {
            const columnTasks = visibleTasks.filter(
              (task) => task.status === column.status,
            )

            return (
              <section
                className={`task-column task-column-${column.status.toLowerCase()}`}
                key={column.status}
              >
                <div className="task-column-header">
                  <div>
                    <span aria-hidden="true" />
                    <h3>{column.title}</h3>
                  </div>
                  <strong>{columnTasks.length}</strong>
                </div>

                <div className="task-list">
                  {columnTasks.length === 0 ? (
                    <p className="task-column-empty">No tasks here</p>
                  ) : (
                    columnTasks.map((task) => (
                      <article className={`task-card ${taskView === 'list' ? 'task-list-row' : ''} ${expandedTaskId === task.id ? 'expanded' : ''}`} key={task.id}>
                        {taskView === 'list' && (
                          <div className="task-list-summary">
                            <span className={`task-list-status status-${task.status.toLowerCase()}`} aria-label={formatRole(task.status)} />
                            <button className="task-list-title" type="button" onClick={() => setExpandedTaskId((current) => current === task.id ? null : task.id)}>
                              <strong>{task.title}</strong>
                              <small>{task.description || 'No description'}</small>
                            </button>
                            <div className="task-list-creator">
                              <TaskCreatorProfile task={task} />
                            </div>
                            <div className="task-list-assignee">
                              {task.assignee ? (
                                <><span aria-hidden="true">{memberInitials(task.assignee.displayName)}</span><strong>{task.assignee.displayName}</strong></>
                              ) : (
                                <><span className="empty" aria-hidden="true">—</span><strong>Unassigned</strong></>
                              )}
                            </div>
                            <time className="task-list-date" dateTime={task.updatedAt}>{formatTaskDate(task.updatedAt)}</time>
                            <button
                              className={`pin-button task-pin-button ${pinnedTaskIds.has(String(task.id)) ? 'active' : ''}`}
                              type="button"
                              aria-label={pinnedTaskIds.has(String(task.id)) ? `Unpin ${task.title}` : `Pin ${task.title}`}
                              aria-pressed={pinnedTaskIds.has(String(task.id))}
                              title={pinnedTaskIds.has(String(task.id)) ? 'Unpin task' : 'Pin task'}
                              onClick={() => togglePinnedTask(task.id)}
                            >
                              <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m7 3 6 0-.8 4 2.3 2.3v1.2h-3.7L10 17l-.8-6.5H5.5V9.3L7.8 7 7 3Z" /></svg>
                            </button>
                            <button
                              className="task-list-expand"
                              type="button"
                              aria-label={expandedTaskId === task.id ? 'Collapse task details' : 'Expand task details'}
                              aria-expanded={expandedTaskId === task.id}
                              onClick={() => setExpandedTaskId((current) => current === task.id ? null : task.id)}
                            >
                              <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m6 8 4 4 4-4" /></svg>
                            </button>
                          </div>
                        )}
                        {taskView === 'board' && (
                          <div className="task-board-card-summary">
                            <button className="task-board-card-title" type="button" onClick={() => setExpandedTaskId((current) => current === task.id ? null : task.id)}>
                              <strong>{task.title}</strong>
                              <small>{task.description || 'No description'}</small>
                            </button>
                            <div className="task-card-quick-actions">
                              <button
                                className={`pin-button task-pin-button ${pinnedTaskIds.has(String(task.id)) ? 'active' : ''}`}
                                type="button"
                                aria-label={pinnedTaskIds.has(String(task.id)) ? `Unpin ${task.title}` : `Pin ${task.title}`}
                                aria-pressed={pinnedTaskIds.has(String(task.id))}
                                title={pinnedTaskIds.has(String(task.id)) ? 'Unpin task' : 'Pin task'}
                                onClick={() => togglePinnedTask(task.id)}
                              >
                                <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m7 3 6 0-.8 4 2.3 2.3v1.2h-3.7L10 17l-.8-6.5H5.5V9.3L7.8 7 7 3Z" /></svg>
                              </button>
                              <button
                                className="task-list-expand"
                                type="button"
                                aria-label={expandedTaskId === task.id ? 'Collapse task details' : 'Expand task details'}
                                aria-expanded={expandedTaskId === task.id}
                                onClick={() => setExpandedTaskId((current) => current === task.id ? null : task.id)}
                              >
                                <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m6 8 4 4 4-4" /></svg>
                              </button>
                            </div>
                            <div className="task-board-card-footer">
                              <TaskCreatorProfile task={task} compact />
                              <span className="task-board-card-assignee">
                                {task.assignee ? (
                                  <><span aria-hidden="true">{memberInitials(task.assignee.displayName)}</span><strong>{task.assignee.displayName}</strong></>
                                ) : (
                                  <><span className="empty" aria-hidden="true">—</span><strong>No assignee</strong></>
                                )}
                              </span>
                              <time dateTime={task.updatedAt}>{formatTaskDate(task.updatedAt)}</time>
                            </div>
                          </div>
                        )}
                        <div className="task-card-details">
                        <div className="task-card-meta">
                          <time dateTime={task.createdAt}>
                            {formatTaskDate(task.createdAt)}
                          </time>
                          <span
                            className={`visibility-badge visibility-${task.visibility?.toLowerCase()}`}
                          >
                            {task.visibility === 'ASSIGNEES'
                              ? 'Assignee only'
                              : 'Project team'}
                          </span>
                          {canManageTask(task) && (
                            <button
                              className="task-edit-toggle"
                              type="button"
                              onClick={() => startTaskEdit(task)}
                            >
                              Edit
                            </button>
                          )}
                        </div>
                        {editingTaskId === task.id ? (
                          <form
                            className="task-inline-edit"
                            onSubmit={(event) =>
                              handleTaskEdit(event, task.id)
                            }
                          >
                            <input
                              aria-label="Task title"
                              minLength={2}
                              maxLength={150}
                              required
                              value={editTaskForm.title}
                              onChange={(event) =>
                                setEditTaskForm((current) => ({
                                  ...current,
                                  title: event.target.value,
                                }))
                              }
                            />
                            <textarea
                              aria-label="Task description"
                              maxLength={1000}
                              value={editTaskForm.description}
                              onChange={(event) =>
                                setEditTaskForm((current) => ({
                                  ...current,
                                  description: event.target.value,
                                }))
                              }
                            />
                            <div>
                              <button
                                className="member-action-button"
                                disabled={updatingTaskId === task.id}
                              >
                                {updatingTaskId === task.id
                                  ? 'Saving…'
                                  : 'Save'}
                              </button>
                              <button
                                className="member-action-button subtle"
                                type="button"
                                onClick={() => setEditingTaskId(null)}
                              >
                                Cancel
                              </button>
                            </div>
                          </form>
                        ) : (
                          <>
                            <h4>{task.title}</h4>
                            <p>
                              {task.description ||
                                'No task description has been added yet.'}
                            </p>
                          </>
                        )}
                        <TaskCreatorProfile task={task} />
                        <TaskAssigneePicker
                          task={task}
                          members={projectMembers}
                          canAssign={isManager}
                          busy={updatingTaskId === task.id}
                          onSave={(memberId) =>
                            handleAssigneeChange(task.id, memberId)
                          }
                          canClaim={!isManager && !task.assignee && workspace.role !== 'VIEWER'}
                          canRelease={!isManager && isAssignee(task)}
                          onClaim={() => handleClaimChange(task)}
                          onRelease={() => handleClaimChange(task, true)}
                        />
                        {canManageTask(task) && (
                          <label className="visibility-control task-visibility-control">
                            <span>Visibility</span>
                            <select
                              value={task.visibility ?? 'PROJECT'}
                              disabled={updatingTaskId === task.id}
                              onChange={(event) =>
                                handleTaskVisibilityChange(
                                  task.id,
                                  event.target.value,
                                )
                              }
                            >
                              <option value="PROJECT">Project team</option>
                              <option value="ASSIGNEES">Assignee only</option>
                            </select>
                          </label>
                        )}
                        <div className="task-status-control">
                          <span>Status</span>
                          <TaskStatusPicker
                            value={task.status}
                            disabled={
                              !canManageTask(task) ||
                              updatingTaskId === task.id
                            }
                            onChange={(status) =>
                              handleStatusChange(task.id, status)
                            }
                          />
                        </div>
                        {canManageTask(task) && (
                          <>
                            <button
                              className="task-activity-toggle"
                              type="button"
                              onClick={() => toggleActivity(task.id)}
                            >
                              <svg viewBox="0 0 20 20" aria-hidden="true"><path d="M10 5v5l3 2M4.8 4.8A7.4 7.4 0 1 1 2.6 10H1m0 0 2.2-2.2M1 10l2.2 2.2" /></svg>
                              {openActivities[task.id] ? 'Hide history' : 'Show history'}
                            </button>
                            {openActivities[task.id] && (
                              <div className="task-activity-list">
                                {loadingActivityId === task.id ? (
                                  <small>Loading history…</small>
                                ) : (activitiesByTask[task.id] ?? []).length === 0 ? (
                                  <small>No recorded changes yet.</small>
                                ) : (
                                  activitiesByTask[task.id].map((activity) => (
                                    <div className="task-activity-item" key={activity.id}>
                                      <span className="task-activity-dot" aria-hidden="true" />
                                      <span>{formatTaskActivity(activity)}</span>
                                      <time dateTime={activity.createdAt}>
                                        {new Date(activity.createdAt).toLocaleString()}
                                      </time>
                                    </div>
                                  ))
                                )}
                              </div>
                            )}
                          </>
                        )}
                        </div>
                      </article>
                    ))
                  )}
                </div>
              </section>
            )
          })}
        </div>
      )}
      </section>
      </div>
    </div>
  )
}

function TaskAssigneePicker({
  task,
  members,
  canAssign,
  busy,
  onSave,
  canClaim,
  canRelease,
  onClaim,
  onRelease,
}) {
  const [isOpen, setIsOpen] = useState(false)
  const [selectedId, setSelectedId] = useState(null)
  const [isSaving, setIsSaving] = useState(false)

  useEffect(() => {
    setSelectedId(task.assignee?.projectMemberId ?? null)
  }, [task.assignee])

  async function save() {
    setIsSaving(true)
    try {
      await onSave(selectedId)
      setIsOpen(false)
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <div className="task-assignees">
      <span className="task-assignee-label">Assignee</span>
      <div className="task-assignee-summary">
        <div className={`task-assignee-identity ${task.assignee ? '' : 'unassigned'}`}>
          {task.assignee ? (
            <>
            <span
              className="task-assignee-avatar"
              title={task.assignee.displayName}
            >
              {memberInitials(task.assignee.displayName)}
            </span>
            <span className="task-assignee-name">
              {task.assignee.displayName}
            </span>
            </>
          ) : (
            <>
              <span className="task-assignee-avatar empty" aria-hidden="true">—</span>
              <span className="task-unassigned"><strong>No assignee</strong><small>Available for the project team</small></span>
            </>
          )}
        </div>
        {canAssign && (
          <button
            className="task-assign-toggle"
            type="button"
            disabled={busy}
            onClick={() => setIsOpen((current) => !current)}
          >
            {isOpen ? 'Close' : task.assignee ? 'Change' : 'Choose person'}
          </button>
        )}
        {canClaim && (
          <button className="task-claim-button" type="button" disabled={busy} onClick={onClaim}>
            {busy ? 'Assigning…' : 'Assign to me'}
          </button>
        )}
        {canRelease && (
          <button className="task-claim-button subtle" type="button" disabled={busy} onClick={onRelease}>
            {busy ? 'Leaving…' : 'Leave task'}
          </button>
        )}
      </div>
      {isOpen && (
        <div className="task-assignee-menu">
          <div className="task-assignee-menu-header">
            <div><strong>Assign task</strong><span>Choose one project member</span></div>
          </div>
          <label className={`task-assignee-option ${selectedId === null ? 'selected' : ''}`}>
            <input
              type="radio"
              name={`task-${task.id}-assignee`}
              checked={selectedId === null}
              onChange={() => setSelectedId(null)}
            />
            <span className="task-assignee-option-avatar empty" aria-hidden="true">—</span>
            <span className="task-assignee-option-identity">
              <strong>Unassigned</strong>
              <small>Leave this task without an owner</small>
            </span>
          </label>
          {members.map((member) => (
              <label className={`task-assignee-option ${selectedId === member.id ? 'selected' : ''}`} key={member.id}>
                <input
                  type="radio"
                  name={`task-${task.id}-assignee`}
                  checked={selectedId === member.id}
                  onChange={() => setSelectedId(member.id)}
                />
                <span className="task-assignee-option-avatar" aria-hidden="true">
                  {memberInitials(member.displayName)}
                </span>
                <span className="task-assignee-option-identity">
                  <strong title={member.displayName}>
                    {member.displayName}
                  </strong>
                  <small title={member.email}>{member.email}</small>
                </span>
              </label>
            ))}
          <button
            className="task-assignee-save"
            type="button"
            disabled={isSaving || selectedId === (task.assignee?.projectMemberId ?? null)}
            onClick={save}
          >
            {isSaving ? 'Saving…' : 'Confirm assignee'}
          </button>
        </div>
      )}
    </div>
  )
}

function MemberProfilePopover({
  member,
  canManageMember,
  isChanging,
  triggerRef,
  onEditRole,
  onEditAccess,
  onRemove,
  onClose,
}) {
  const dialogRef = useRef(null)
  const closeButtonRef = useRef(null)
  const onCloseRef = useRef(onClose)
  const [isMobileDialog, setIsMobileDialog] = useState(false)
  const titleId = `member-profile-${member.id}-title`
  const descriptionId = `member-profile-${member.id}-description`

  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  useEffect(() => {
    const trigger = triggerRef.current
    closeButtonRef.current?.focus()

    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        event.preventDefault()
        onCloseRef.current()
      }
    }

    function handlePointerDown(event) {
      if (
        !dialogRef.current?.contains(event.target) &&
        !triggerRef.current?.contains(event.target)
      ) {
        onCloseRef.current()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    document.addEventListener('pointerdown', handlePointerDown)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.removeEventListener('pointerdown', handlePointerDown)
      trigger?.focus()
    }
  }, [member.id, triggerRef])

  useEffect(() => {
    const media = window.matchMedia('(max-width: 700px)')
    const updateMode = () => setIsMobileDialog(media.matches)
    updateMode()
    media.addEventListener('change', updateMode)
    return () => media.removeEventListener('change', updateMode)
  }, [])

  useEffect(() => {
    if (!isMobileDialog) return undefined
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = previousOverflow
    }
  }, [isMobileDialog])

  return (
    <div className="member-profile-layer">
      <section
        className="member-profile-popover"
        ref={dialogRef}
        role="dialog"
        aria-modal={isMobileDialog}
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
      >
        <div className="member-profile-header">
          <span className="member-profile-avatar" aria-hidden="true">
            {memberInitials(member.displayName)}
          </span>
          <div>
            <strong id={titleId}>{member.displayName}</strong>
            <span>{member.email}</span>
          </div>
          <button
            className="member-profile-close"
            ref={closeButtonRef}
            type="button"
            aria-label={`Close ${member.displayName} profile`}
            onClick={onClose}
          >
            ×
          </button>
        </div>

        <div className="member-profile-meta" id={descriptionId}>
          <span>Workspace role</span>
          <strong>{formatRole(member.role)}</strong>
        </div>

        {canManageMember && (
          <div className="member-profile-member-actions">
            <button
              className="member-action-button member-profile-action"
              type="button"
              disabled={isChanging}
              onClick={onEditRole}
            >
              <span className="member-profile-action-icon" aria-hidden="true">R</span>
              <span><strong>Workspace role</strong><small>Change member, admin or viewer access</small></span>
              <span aria-hidden="true">→</span>
            </button>
            <button
              className="member-action-button member-profile-action"
              type="button"
              disabled={isChanging}
              onClick={onEditAccess}
            >
              <span className="member-profile-action-icon" aria-hidden="true">P</span>
              <span><strong>Project access</strong><small>Choose roles and projects this person can open</small></span>
              <span aria-hidden="true">→</span>
            </button>
            <button
              className="danger-button member-profile-action danger"
              type="button"
              disabled={isChanging}
              onClick={onRemove}
            >
              <span className="member-profile-action-icon" aria-hidden="true">×</span>
              <span><strong>Remove member</strong><small>Revoke access to this workspace</small></span>
            </button>
          </div>
        )}
      </section>
    </div>
  )
}

function WorkspaceMembers({
  workspace,
  projects,
  projectTeams,
  accessRoles,
  members,
  onMembersChange,
  onProjectTeamChange,
}) {
  const [form, setForm] = useState({ email: '', role: 'MEMBER' })
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isAddFormOpen, setIsAddFormOpen] = useState(false)
  const [editingMemberId, setEditingMemberId] = useState(null)
  const [pendingRole, setPendingRole] = useState('MEMBER')
  const [changingMemberId, setChangingMemberId] = useState(null)
  const [accessEditingMemberId, setAccessEditingMemberId] = useState(null)
  const [projectAccessDraft, setProjectAccessDraft] = useState({})
  const [memberRoleDraft, setMemberRoleDraft] = useState([])
  const [savingProjectAccess, setSavingProjectAccess] = useState(false)
  const [profileMemberId, setProfileMemberId] = useState(null)
  const profileTriggerRef = useRef(null)

  const isOwner = ['OWNER', 'ADMIN'].includes(workspace.role)

  async function handleAdd(event) {
    event.preventDefault()
    setError('')
    setFieldErrors({})
    setIsSubmitting(true)

    try {
      const member = await addWorkspaceMember(workspace.id, form)
      onMembersChange((current) => [...current, member])
      setForm({ email: '', role: 'MEMBER' })
      setIsAddFormOpen(false)
    } catch (addError) {
      setFieldErrors(addError.fieldErrors ?? {})
      setError(addError.message || 'Unable to add the member.')
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleRoleChange(memberId, role) {
    setError('')
    setChangingMemberId(memberId)

    try {
      const updated = await changeWorkspaceMemberRole(
        workspace.id,
        memberId,
        role,
      )
      onMembersChange((current) =>
        current.map((member) =>
          member.id === updated.id ? updated : member,
        ),
      )
      projects.forEach((project) => {
        const updatedTeam = (projectTeams[project.id] ?? []).map(
          (projectMember) =>
            projectMember.workspaceMemberId === updated.id
              ? { ...projectMember, workspaceRole: updated.role }
              : projectMember,
        )
        onProjectTeamChange(project.id, updatedTeam)
      })
      setEditingMemberId(null)
    } catch (changeError) {
      setError(changeError.message || 'Unable to update the role.')
    } finally {
      setChangingMemberId(null)
    }
  }

  async function handleRemove(memberId) {
    const member = members.find((item) => item.id === memberId)
    const confirmed = window.confirm(
      `Remove ${member?.displayName ?? 'this member'} from the workspace?`,
    )

    if (!confirmed) {
      return
    }

    setError('')
    setChangingMemberId(memberId)

    try {
      await removeWorkspaceMember(workspace.id, memberId)
      onMembersChange((current) =>
        current.filter((member) => member.id !== memberId),
      )
      projects.forEach((project) => {
        onProjectTeamChange(
          project.id,
          (projectTeams[project.id] ?? []).filter(
            (projectMember) =>
              projectMember.workspaceMemberId !== memberId,
          ),
        )
      })
    } catch (removeError) {
      setError(removeError.message || 'Unable to remove the member.')
    } finally {
      setChangingMemberId(null)
    }
  }

  function startRoleEdit(member) {
    setError('')
    setEditingMemberId(member.id)
    setPendingRole(member.role)
  }

  function cancelRoleEdit() {
    setEditingMemberId(null)
    setPendingRole('MEMBER')
  }

  function startAccessEdit(member) {
    setError('')
    setEditingMemberId(null)
    setAccessEditingMemberId(member.id)
    setMemberRoleDraft((member.accessRoles ?? []).map((role) => role.id))
    setProjectAccessDraft(Object.fromEntries(projects.map((project) => {
      const existing = (projectTeams[project.id] ?? []).some(
        (projectMember) =>
          projectMember.workspaceMemberId === member.id &&
          projectMember.grantsAccess,
      )
      return [project.id, { selected: existing }]
    })))
  }

  function toggleDraftProject(projectId) {
    setProjectAccessDraft((current) => ({
      ...current,
      [projectId]: {
        selected: !current[projectId]?.selected,
      },
    }))
  }

  function toggleMemberRole(roleId) {
    setMemberRoleDraft((current) => current.includes(roleId)
      ? current.filter((id) => id !== roleId)
      : [...current, roleId])
  }

  async function saveProjectAccess(member) {
    setSavingProjectAccess(true)
    setError('')
    try {
      const updatedRoles = await replaceWorkspaceMemberAccessRoles(
        workspace.id,
        member.id,
        memberRoleDraft,
      )
      onMembersChange((current) => current.map((item) => item.id === member.id
        ? { ...item, accessRoles: updatedRoles }
        : item))
      projects.forEach((project) => {
        onProjectTeamChange(
          project.id,
          (projectTeams[project.id] ?? []).map((item) =>
            item.workspaceMemberId === member.id
              ? { ...item, roles: updatedRoles }
              : item,
          ),
        )
      })
      for (const project of projects) {
        const team = projectTeams[project.id] ?? []
        const existing = team.find(
          (projectMember) =>
            projectMember.workspaceMemberId === member.id,
        )
        const draft = projectAccessDraft[project.id] ?? {
          selected: false,
        }

        if (draft.selected && !existing?.grantsAccess) {
          const added = await addProjectMember(
            workspace.id,
            project.id,
            member.id,
            [],
          )
          onProjectTeamChange(
            project.id,
            existing
              ? team.map((item) => item.id === existing.id ? added : item)
              : [...team, added],
          )
        } else if (!draft.selected && existing?.grantsAccess) {
          await removeProjectMember(
            workspace.id,
            project.id,
            existing.id,
          )
          onProjectTeamChange(
            project.id,
            team.filter((item) => item.id !== existing.id),
          )
        }
      }
      setAccessEditingMemberId(null)
      setProjectAccessDraft({})
      setMemberRoleDraft([])
    } catch (saveError) {
      setError(
        saveError.message || 'Unable to update project access.',
      )
    } finally {
      setSavingProjectAccess(false)
    }
  }

  return (
    <section className="members-panel">
      <div className="members-panel-heading">
        <div>
          <h3>Members</h3>
        </div>
        {isOwner && (
          <button
            className="member-add-toggle"
            type="button"
            onClick={() => {
              setError('')
              setFieldErrors({})
              setIsAddFormOpen((current) => !current)
            }}
          >
            {isAddFormOpen ? 'Cancel' : '+ Add member'}
          </button>
        )}
      </div>

      {isOwner && isAddFormOpen && (
        <form
          className="member-add-form"
          autoComplete="off"
          onSubmit={handleAdd}
        >
          <FormField
            id="member-email"
            label="Registered user email"
            type="email"
            name="workspace-member-lookup"
            autoComplete="off"
            data-1p-ignore
            data-lpignore="true"
            placeholder="member@example.com"
            maxLength={320}
            value={form.email}
            onChange={(email) =>
              setForm((current) => ({ ...current, email }))
            }
            error={fieldErrors.email}
          />
          <RolePicker
            value={form.role}
            onChange={(role) =>
              setForm((current) => ({
                ...current,
                role,
              }))
            }
          />
          <button className="primary-button" disabled={isSubmitting}>
            {isSubmitting ? 'Adding…' : 'Add member'}
          </button>
        </form>
      )}

      {error && (
        <div className="form-message error" role="alert">
          {error}
        </div>
      )}

      <div className="member-list">
          {members.map((member) => {
            const isWorkspaceOwner = member.role === 'OWNER'
            const isChanging = changingMemberId === member.id
            const isEditing = editingMemberId === member.id
            const isAccessEditing = accessEditingMemberId === member.id
            const isProfileOpen = profileMemberId === member.id
            const assignedProjects = projects
              .map((project) => ({
                project,
                assignment: (projectTeams[project.id] ?? []).find(
                  (projectMember) =>
                    projectMember.workspaceMemberId === member.id,
                ),
              }))
              .filter(({ assignment }) => Boolean(assignment))

            return (
              <article className="member-row" key={member.id}>
                <button
                  className="member-avatar member-profile-trigger"
                  type="button"
                  aria-label={`Open ${member.displayName} profile`}
                  aria-expanded={isProfileOpen}
                  onClick={(event) => {
                    profileTriggerRef.current = event.currentTarget
                    setProfileMemberId(isProfileOpen ? null : member.id)
                  }}
                >
                  {member.displayName.slice(0, 1).toUpperCase()}
                </button>
                <button
                  className="member-identity member-profile-trigger"
                  type="button"
                  aria-expanded={isProfileOpen}
                  onClick={(event) => {
                    profileTriggerRef.current = event.currentTarget
                    setProfileMemberId(isProfileOpen ? null : member.id)
                  }}
                >
                  <strong>{member.displayName}</strong>
                  <span>{member.email}</span>
                </button>
                <span
                  className={roleBadgeClassName(
                    member.role,
                    'member-role-badge',
                  )}
                >
                  {formatRole(member.role)}
                </span>
                {isProfileOpen && (
                  <MemberProfilePopover
                    member={member}
                    canManageMember={isOwner && !isWorkspaceOwner}
                    isChanging={isChanging}
                    triggerRef={profileTriggerRef}
                    onClose={() => setProfileMemberId(null)}
                    onEditRole={() => {
                      setProfileMemberId(null)
                      startRoleEdit(member)
                    }}
                    onEditAccess={() => {
                      setProfileMemberId(null)
                      startAccessEdit(member)
                    }}
                    onRemove={() => {
                      setProfileMemberId(null)
                      handleRemove(member.id)
                    }}
                  />
                )}
                {isOwner && !isWorkspaceOwner && isEditing && (
                  <div className="member-role-editor">
                    <RolePicker
                      compact
                      label={`Role for ${member.displayName}`}
                      value={pendingRole}
                      disabled={isChanging}
                      onChange={setPendingRole}
                    />
                    <div className="member-role-editor-actions">
                      <button
                        className="member-action-button"
                        type="button"
                        disabled={
                          isChanging || pendingRole === member.role
                        }
                        onClick={() =>
                          handleRoleChange(member.id, pendingRole)
                        }
                      >
                        {isChanging ? 'Saving…' : 'Save role'}
                      </button>
                      <button
                        className="member-action-button subtle"
                        type="button"
                        disabled={isChanging}
                        onClick={cancelRoleEdit}
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                )}
                {isAccessEditing && (
                  <div className="member-access-dialog-layer">
                    <div className="workspace-project-access-editor" role="dialog" aria-modal="true" aria-label={`Access settings for ${member.displayName}`}>
                    <div className="workspace-project-access-heading">
                      <div>
                        <strong>Projects for {member.displayName}</strong>
                        <small>
                          Custom roles belong to the member across the workspace.
                          Project selection below grants direct access.
                        </small>
                      </div>
                      <span>{assignedProjects.length} active</span>
                    </div>
                    <fieldset className="project-create-access">
                      <legend>Custom roles</legend>
                      <div className="project-create-access-options">
                        {accessRoles.map((role) => (
                          <label key={role.id}>
                            <input
                              type="checkbox"
                              checked={memberRoleDraft.includes(role.id)}
                              onChange={() => toggleMemberRole(role.id)}
                            />
                            <span
                              className="custom-role-chip"
                              style={{ '--role-color': role.color }}
                            >
                              {role.name}
                            </span>
                          </label>
                        ))}
                        {accessRoles.length === 0 && (
                          <small>No custom roles yet.</small>
                        )}
                      </div>
                    </fieldset>
                    {projects.length === 0 ? (
                      <p>No projects in this workspace.</p>
                    ) : (
                      <div className="workspace-project-access-list">
                        {projects.map((project) => {
                           const draft = projectAccessDraft[project.id] ?? {
                             selected: false,
                           }
                          return (
                            <section
                              className={`workspace-project-access-item ${
                                draft.selected ? 'selected' : ''
                              }`}
                              key={project.id}
                            >
                              <label className="workspace-project-toggle">
                                <input
                                  type="checkbox"
                                  checked={draft.selected}
                                  onChange={() =>
                                    toggleDraftProject(project.id)
                                  }
                                />
                                <span className="assignment-role-check">
                                  {draft.selected ? '✓' : ''}
                                </span>
                                <span>
                                  <strong>{project.name}</strong>
                                  <small>
                                    {project.status === 'ACTIVE'
                                      ? 'Active'
                                      : project.status}{' '}
                                    ·{' '}
                                    {project.restricted
                                      ? 'Restricted'
                                      : 'Workspace'}
                                  </small>
                                </span>
                              </label>
                            </section>
                          )
                        })}
                      </div>
                    )}
                    <div className="workspace-project-access-actions">
                      <button
                        className="member-action-button subtle"
                        type="button"
                        disabled={savingProjectAccess}
                        onClick={() => {
                          setAccessEditingMemberId(null)
                          setProjectAccessDraft({})
                          setMemberRoleDraft([])
                        }}
                      >
                        Cancel
                      </button>
                      <button
                        className="primary-button"
                        type="button"
                        disabled={savingProjectAccess}
                        onClick={() => saveProjectAccess(member)}
                      >
                        {savingProjectAccess
                          ? 'Saving access…'
                          : 'Save project access'}
                      </button>
                    </div>
                    </div>
                  </div>
                )}
              </article>
            )
          })}
      </div>
    </section>
  )
}

function formatTaskActivity(activity) {
  const actor = activity.actorDisplayName
  switch (activity.type) {
    case 'CREATED':
      return `${actor} created the task`
    case 'EDITED':
      return `${actor} edited the task`
    case 'STATUS_CHANGED':
      return `${actor} changed status from ${formatRole(activity.oldValue)} to ${formatRole(activity.newValue)}`
    case 'CLAIMED':
      return `${actor} took the task`
    case 'RELEASED':
      return `${actor} released the task`
    case 'ASSIGNEE_CHANGED':
      return activity.newValue
        ? `${actor} assigned ${activity.newValue}`
        : `${actor} removed ${activity.oldValue ?? 'the assignee'}`
    case 'VISIBILITY_CHANGED':
      return `${actor} changed visibility from ${formatRole(activity.oldValue)} to ${formatRole(activity.newValue)}`
    default:
      return `${actor} updated the task`
  }
}

function formatTaskDate(createdAt, now = new Date()) {
  const date = new Date(createdAt)

  if (Number.isNaN(date.getTime())) {
    return ''
  }

  const dateDay = Date.UTC(
    date.getFullYear(),
    date.getMonth(),
    date.getDate(),
  )
  const currentDay = Date.UTC(
    now.getFullYear(),
    now.getMonth(),
    now.getDate(),
  )
  const dayDifference = Math.round((dateDay - currentDay) / 86_400_000)
  const time = new Intl.DateTimeFormat('en-GB', {
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(date)

  if (dayDifference === 0) {
    return `Today at ${time}`
  }

  if (dayDifference === -1) {
    return `Yesterday at ${time}`
  }

  if (dayDifference === 1) {
    return `Tomorrow at ${time}`
  }

  const numericDate = [
    String(date.getDate()).padStart(2, '0'),
    String(date.getMonth() + 1).padStart(2, '0'),
    date.getFullYear(),
  ].join('.')

  return `${numericDate} at ${time}`
}

function LoadingScreen() {
  return (
    <main className="loading-screen">
      <Brand />
      <span className="loading-spinner" aria-label="Checking session" />
      <p>Checking your session…</p>
    </main>
  )
}

function App() {
  const [user, setUser] = useState(null)
  const [mode, setMode] = useState('login')
  const [isLoading, setIsLoading] = useState(true)
  const [startupError, setStartupError] = useState('')
  const [theme, setTheme] = useState(getInitialTheme)

  async function restoreSession() {
    setStartupError('')
    setIsLoading(true)

    try {
      clearSavedNavigation()
      setUser(await getCurrentUser())
    } catch (error) {
      setStartupError(
        error.message ||
          'We could not reach CollabDesk. Please try again in a moment.',
      )
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    restoreSession()
  }, [])

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    document.documentElement.style.colorScheme = theme
    localStorage.setItem(THEME_STORAGE_KEY, theme)
  }, [theme])

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'auto' })
  }, [user])

  function handleAuthenticated(nextUser) {
    clearSavedNavigation()
    setUser(nextUser)
  }

  function handleLogout() {
    clearSavedNavigation()
    setUser(null)
    setMode('login')
  }

  let content

  if (isLoading) {
    content = <LoadingScreen />
  } else if (startupError) {
    content = (
      <main className="connection-page">
        <Brand />
        <div className="connection-card">
          <span className="connection-code">503</span>
          <p className="eyebrow">Connection error</p>
          <h1>CollabDesk is temporarily unavailable</h1>
          <p>{startupError}</p>
          <button className="primary-button" onClick={restoreSession}>
            Try again
          </button>
        </div>
      </main>
    )
  } else if (user) {
    content = (
      <Dashboard
        user={user}
        onLogout={handleLogout}
        theme={theme}
        onToggleTheme={() =>
          setTheme((current) => (current === 'dark' ? 'light' : 'dark'))
        }
      />
    )
  } else {
    content = (
      <AuthShell
        mode={mode}
        onModeChange={setMode}
        onAuthenticated={handleAuthenticated}
      />
    )
  }

  return (
    <>
      {!user && (
        <ThemeToggle
          theme={theme}
          onToggle={() =>
            setTheme((current) => (current === 'dark' ? 'light' : 'dark'))
          }
        />
      )}
      {content}
    </>
  )
}

export default App
