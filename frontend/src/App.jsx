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
  changeProjectVisibility,
  createProject,
  getProjects,
} from './api/projectApi.js'
import {
  changeTaskStatus,
  changeTaskVisibility,
  createTask,
  getTasks,
  updateTaskAssignee,
  updateTask,
} from './api/taskApi.js'
import {
  addProjectMember,
  getProjectMembers,
  removeProjectMember,
  replaceProjectMemberRoles,
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
    description: 'Change project settings and visibility',
  },
  {
    value: 'CREATE_TASK',
    label: 'Create tasks',
    description: 'Add new work to the project',
  },
  {
    value: 'EDIT_TASK',
    label: 'Edit tasks',
    description: 'Change task content',
  },
  {
    value: 'CHANGE_TASK_STATUS',
    label: 'Change task status',
    description: 'Move tasks through the workflow',
  },
  {
    value: 'CHANGE_TASK_VISIBILITY',
    label: 'Change task visibility',
    description: 'Restrict tasks to the assigned member',
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

const ALL_PROJECT_PERMISSIONS = PROJECT_PERMISSIONS.map(
  (permission) => permission.value,
)

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

function PageBackButton({ label, context, onClick }) {
  return (
    <button className="page-back-button" type="button" onClick={onClick}>
      <span className="page-back-icon" aria-hidden="true">
        ←
      </span>
      <span>
        <small>{context}</small>
        <strong>{label}</strong>
      </span>
    </button>
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
        className="role-picker-trigger"
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
              className={role.value === value ? 'selected' : ''}
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

function Dashboard({ user, onLogout }) {
  const [isLoggingOut, setIsLoggingOut] = useState(false)
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
        <Brand />
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

        <WorkspaceSection user={user} />
      </main>
    </div>
  )
}

function WorkspaceSection({ user }) {
  const [workspaces, setWorkspaces] = useState([])
  const [selectedWorkspace, setSelectedWorkspace] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isCreating, setIsCreating] = useState(false)
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [moduleUnavailable, setModuleUnavailable] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [form, setForm] = useState({ name: '', description: '' })

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
    setSelectedWorkspace(workspace)
  }

  function closeWorkspace() {
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
          <p className="eyebrow">Workspaces</p>
          <h2>Your teams</h2>
          <p>Choose a workspace to manage its projects, tasks, and members.</p>
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
          {workspaces.map((workspace) => (
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

function ProjectSection({ workspace, user, onBack }) {
  const [projects, setProjects] = useState([])
  const [projectTeams, setProjectTeams] = useState({})
  const [accessRoles, setAccessRoles] = useState([])
  const [selectedProject, setSelectedProject] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isCreating, setIsCreating] = useState(false)
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [form, setForm] = useState({ name: '', description: '' })

  const loadProjects = useCallback(async () => {
    setError('')
    setIsLoading(true)

    try {
      const [loadedProjects, loadedRoles] = await Promise.all([
        getProjects(workspace.id),
        getAccessRoles(workspace.id),
      ])
      const loadedTeams = await Promise.all(
        loadedProjects.map(async (project) => [
          project.id,
          await getProjectMembers(workspace.id, project.id),
        ]),
      )
      setProjects(loadedProjects)
      setAccessRoles(loadedRoles)
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
  }, [workspace.id])

  useEffect(() => {
    loadProjects()
  }, [loadProjects])

  async function handleCreate(event) {
    event.preventDefault()
    setError('')
    setFieldErrors({})
    setIsCreating(true)

    try {
      const project = await createProject(workspace.id, form)
      setProjects((current) => [...current, project])
      try {
        const team = await getProjectMembers(workspace.id, project.id)
        setProjectTeams((current) => ({ ...current, [project.id]: team }))
      } catch {
        setProjectTeams((current) => ({ ...current, [project.id]: [] }))
      }
      setForm({ name: '', description: '' })
      setIsFormOpen(false)
    } catch (createError) {
      setFieldErrors(createError.fieldErrors ?? {})
      setError(createError.message || 'Unable to create the project.')
    } finally {
      setIsCreating(false)
    }
  }

  function openProject(project) {
    setSelectedProject(project)
  }

  function closeProject() {
    setSelectedProject(null)
  }

  function updateSelectedProject(updatedProject) {
    setProjects((current) =>
      current.map((project) =>
        project.id === updatedProject.id ? updatedProject : project,
      ),
    )
    setSelectedProject(updatedProject)
  }

  function updateProjectTeam(projectId, team) {
    setProjectTeams((current) => ({ ...current, [projectId]: team }))
  }

  if (selectedProject) {
    return (
      <TaskBoard
        workspace={workspace}
        user={user}
        project={selectedProject}
        onProjectChange={updateSelectedProject}
        onBack={closeProject}
      />
    )
  }

  return (
    <div className="detail-view">
      <PageBackButton
        context="Back to"
        label="All workspaces"
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
            <p className="eyebrow">Current workspace</p>
            <h2>{workspace.name}</h2>
            <p>
              {workspace.description ||
                'Projects and tasks for this workspace.'}
            </p>
          </div>
        </div>
        <div className="workspace-header-actions">
          <span
            className={roleBadgeClassName(
              workspace.role,
              'context-role-badge',
            )}
          >
            {formatRole(workspace.role)}
          </span>
          {workspace.role !== 'VIEWER' && (
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
          <button className="secondary-button" onClick={loadProjects}>
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
      ) : (
        <div className="project-grid">
          {projects.map((project) => {
            const team = projectTeams[project.id] ?? []
            const assignedRoles = Array.from(
              new Map(
                team
                  .flatMap((member) => member.roles ?? [])
                  .map((role) => [role.id, role]),
              ).values(),
            )
            const directMembers = team.filter(
              (member) => (member.roles ?? []).length === 0,
            )

            return (
            <button
              className="project-card"
              key={project.id}
              type="button"
              style={getItemAccentStyle(project.id + 1)}
              onClick={() => openProject(project)}
            >
              <span className="project-card-accent" aria-hidden="true" />
              <div className="project-card-top">
                <span className="project-status">
                  <span aria-hidden="true" />
                  {project.status === 'ACTIVE'
                    ? 'Active'
                    : project.status}
                </span>
                <span
                  className={`visibility-badge visibility-${project.visibility?.toLowerCase()}`}
                >
                  {project.visibility === 'RESTRICTED'
                    ? 'Restricted'
                    : 'Workspace'}
                </span>
              </div>
              <h3>{project.name}</h3>
              <p>
                {project.description ||
                  'No project description has been added yet.'}
              </p>
              <div className="project-card-access">
                {assignedRoles.length > 0 && (
                  <div className="project-card-access-row">
                    <small>Roles</small>
                    <div>
                      {assignedRoles.map((role) => (
                        <span
                          className="custom-role-chip"
                          style={{ '--role-color': role.color }}
                          key={role.id}
                        >
                          {role.name}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
                {directMembers.length > 0 && (
                  <div className="project-card-access-row">
                    <small>People</small>
                    <div className="project-card-people">
                      {directMembers.slice(0, 4).map((member) => (
                        <span
                          className="project-card-person"
                          title={member.displayName}
                          key={member.id}
                        >
                          {memberInitials(member.displayName)}
                        </span>
                      ))}
                      {directMembers.length > 4 && (
                        <span className="project-card-person more">
                          +{directMembers.length - 4}
                        </span>
                      )}
                    </div>
                  </div>
                )}
                {team.length === 0 && (
                  <span className="project-card-no-access">
                    No people assigned
                  </span>
                )}
              </div>
              <div className="project-card-footer">
                <span>Open tasks →</span>
              </div>
            </button>
            )
          })}
        </div>
      )}
          </div>
        </section>

        <aside
          className="workspace-members-column"
          aria-label="Workspace members"
        >
          <AccessRoleManager workspace={workspace} onChange={loadProjects} />
          <WorkspaceMembers
            workspace={workspace}
            projects={projects}
            projectTeams={projectTeams}
            accessRoles={accessRoles}
            onProjectTeamChange={updateProjectTeam}
          />
        </aside>
      </div>
    </div>
  )
}

function TaskBoard({
  workspace,
  user,
  project,
  onProjectChange,
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
  const isManager = ['OWNER', 'ADMIN'].includes(workspace.role)
  const currentProjectMember = projectMembers.find(
    (member) => member.userId === user.id,
  )
  const currentPermissions = isManager
    ? ALL_PROJECT_PERMISSIONS
    : currentProjectMember?.effectivePermissions ?? []
  const hasPermission = (permission) =>
    currentPermissions.includes(permission)

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
    } catch (updateError) {
      setError(
        updateError.message || 'Unable to update the task assignee.',
      )
      throw updateError
    } finally {
      setUpdatingTaskId(null)
    }
  }

  async function handleProjectVisibilityChange(visibility) {
    setError('')
    try {
      onProjectChange(
        await changeProjectVisibility(
          workspace.id,
          project.id,
          visibility,
        ),
      )
    } catch (updateError) {
      setError(
        updateError.message || 'Unable to update project visibility.',
      )
    }
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
        context={`Back to ${workspace.name}`}
        label="Workspace projects"
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
        </div>
        <div className="workspace-header-actions">
          {hasPermission('EDIT_PROJECT') && (
            <label className="visibility-control project-visibility-control">
              <span>Project access</span>
              <select
                value={project.visibility ?? 'WORKSPACE'}
                onChange={(event) =>
                  handleProjectVisibilityChange(event.target.value)
                }
              >
                <option value="WORKSPACE">Workspace</option>
                <option value="RESTRICTED">Restricted</option>
              </select>
            </label>
          )}
          <span
            className={roleBadgeClassName(
              workspace.role,
              'context-role-badge',
            )}
          >
            {formatRole(workspace.role)}
          </span>
          {hasPermission('CREATE_TASK') && (
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

      {isLoading ? (
        <div className="project-loading">
          <span className="loading-spinner" aria-hidden="true" />
          <p>Loading tasks…</p>
        </div>
      ) : (
        <div className="task-board">
          {TASK_COLUMNS.map((column) => {
            const columnTasks = tasks.filter(
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
                      <article className="task-card" key={task.id}>
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
                          {hasPermission('EDIT_TASK') && (
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
                        <TaskAssigneePicker
                          task={task}
                          members={projectMembers}
                          disabled={
                            !isManager ||
                            updatingTaskId === task.id
                          }
                          onSave={(memberId) =>
                            handleAssigneeChange(task.id, memberId)
                          }
                        />
                        {hasPermission('CHANGE_TASK_VISIBILITY') && (
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
                              !hasPermission('CHANGE_TASK_STATUS') ||
                              updatingTaskId === task.id
                            }
                            onChange={(status) =>
                              handleStatusChange(task.id, status)
                            }
                          />
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

export function ProjectTeam({
  workspace,
  project,
  members,
  onMembersChange,
}) {
  const [workspaceMembers, setWorkspaceMembers] = useState([])
  const [accessRoles, setAccessRoles] = useState([])
  const [selectedMemberIds, setSelectedMemberIds] = useState([])
  const [selectedRoleIds, setSelectedRoleIds] = useState([])
  const [memberQuery, setMemberQuery] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isAdding, setIsAdding] = useState(false)
  const [removingId, setRemovingId] = useState(null)
  const [editingMemberId, setEditingMemberId] = useState(null)
  const [pendingRoleIds, setPendingRoleIds] = useState([])
  const [savingRolesId, setSavingRolesId] = useState(null)
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [error, setError] = useState('')
  const isManager =
    workspace.role === 'OWNER' || workspace.role === 'ADMIN'

  const loadMembers = useCallback(async () => {
    setIsLoading(true)
    setError('')
    try {
      const [projectTeam, workspaceTeam, customRoles] = await Promise.all([
        getProjectMembers(workspace.id, project.id),
        getWorkspaceMembers(workspace.id),
        getAccessRoles(workspace.id),
      ])
      onMembersChange(projectTeam)
      setWorkspaceMembers(workspaceTeam)
      setAccessRoles(customRoles)
    } catch (loadError) {
      setError(loadError.message || 'Unable to load the project team.')
    } finally {
      setIsLoading(false)
    }
  }, [workspace.id, project.id, onMembersChange])

  useEffect(() => {
    loadMembers()
  }, [loadMembers])

  const availableMembers = workspaceMembers.filter(
    (workspaceMember) =>
      !members.some(
        (projectMember) =>
          projectMember.workspaceMemberId === workspaceMember.id,
      ),
  )
  const visibleAvailableMembers = availableMembers.filter((member) => {
    const query = memberQuery.trim().toLowerCase()
    return (
      !query ||
      member.displayName.toLowerCase().includes(query) ||
      (member.email ?? '').toLowerCase().includes(query)
    )
  })

  function toggleSelectedMember(memberId) {
    setSelectedMemberIds((current) =>
      current.includes(memberId)
        ? current.filter((id) => id !== memberId)
        : [...current, memberId],
    )
  }

  function toggleSelectedRole(roleId) {
    setSelectedRoleIds((current) =>
      current.includes(roleId)
        ? current.filter((id) => id !== roleId)
        : [...current, roleId],
    )
  }

  function togglePendingRole(roleId) {
    setPendingRoleIds((current) =>
      current.includes(roleId)
        ? current.filter((id) => id !== roleId)
        : [...current, roleId],
    )
  }

  async function handleAdd(event) {
    event.preventDefault()
    if (selectedMemberIds.length === 0) return
    setIsAdding(true)
    setError('')
    const results = await Promise.allSettled(
      selectedMemberIds.map((workspaceMemberId) =>
        addProjectMember(
          workspace.id,
          project.id,
          workspaceMemberId,
          selectedRoleIds,
        ),
      ),
    )
    const addedMembers = results
      .filter((result) => result.status === 'fulfilled')
      .map((result) => result.value)
    const failedMemberIds = selectedMemberIds.filter(
      (_, index) => results[index].status === 'rejected',
    )

    if (addedMembers.length > 0) {
      onMembersChange((current) => [
        ...current,
        ...addedMembers.filter(
          (added) => !current.some((member) => member.id === added.id),
        ),
      ])
    }

    if (failedMemberIds.length === 0) {
      setSelectedMemberIds([])
      setSelectedRoleIds([])
      setMemberQuery('')
      setIsFormOpen(false)
    } else {
      setSelectedMemberIds(failedMemberIds)
      const firstFailure = results.find(
        (result) => result.status === 'rejected',
      )
      setError(
        firstFailure?.reason?.message ||
          `Unable to add ${failedMemberIds.length} selected member(s).`,
      )
    }
    setIsAdding(false)
  }

  async function handleRolesSave(memberId) {
    setSavingRolesId(memberId)
    setError('')
    try {
      const updated = await replaceProjectMemberRoles(
        workspace.id,
        project.id,
        memberId,
        pendingRoleIds,
      )
      onMembersChange((current) =>
        current.map((member) =>
          member.id === updated.id ? updated : member,
        ),
      )
      setEditingMemberId(null)
      setPendingRoleIds([])
    } catch (saveError) {
      setError(
        saveError.message || 'Unable to update project member roles.',
      )
    } finally {
      setSavingRolesId(null)
    }
  }

  async function handleRemove(memberId) {
    setRemovingId(memberId)
    setError('')
    try {
      await removeProjectMember(workspace.id, project.id, memberId)
      onMembersChange((current) =>
        current.filter((member) => member.id !== memberId),
      )
    } catch (removeError) {
      setError(
        removeError.message || 'Unable to remove the project member.',
      )
    } finally {
      setRemovingId(null)
    }
  }

  return (
    <section className="members-panel">
      <div className="members-panel-heading">
        <div>
          <p className="eyebrow">Project access</p>
          <h3>Project team</h3>
        </div>
        {isManager && (
          <button
            className="member-add-toggle"
            type="button"
            disabled={availableMembers.length === 0}
            onClick={() => {
              setIsFormOpen((current) => !current)
              setSelectedMemberIds([])
              setSelectedRoleIds([])
              setMemberQuery('')
            }}
          >
            {isFormOpen ? 'Cancel' : '+ Add people'}
          </button>
        )}
      </div>

      {isFormOpen && (
        <form className="project-member-form" onSubmit={handleAdd}>
          <fieldset className="assignment-role-fieldset">
            <legend>
              <span>1</span>
              Choose project access
            </legend>
            <p>
              Workspace roles apply to the selected people in this project
              only.
            </p>
            <div className="assignment-role-list">
              <button
                className={`assignment-default-role ${
                  selectedRoleIds.length === 0 ? 'selected' : ''
                }`}
                type="button"
                onClick={() => setSelectedRoleIds([])}
              >
                <span className="assignment-role-icon">D</span>
                <span>
                  <strong>Default project access</strong>
                  <small>Uses the member’s standard permissions</small>
                </span>
                <span className="assignment-role-check" aria-hidden="true">
                  {selectedRoleIds.length === 0 ? '✓' : ''}
                </span>
              </button>
              {accessRoles.length === 0 ? (
                <span className="assignment-empty-note">
                  Create a custom role in workspace settings to use it here.
                </span>
              ) : (
                accessRoles.map((role) => (
                  <label
                    className={`assignment-role-option ${
                      selectedRoleIds.includes(role.id) ? 'selected' : ''
                    }`}
                    key={role.id}
                  >
                    <input
                      type="checkbox"
                      checked={selectedRoleIds.includes(role.id)}
                      onChange={() => toggleSelectedRole(role.id)}
                    />
                    <span
                      className="custom-role-dot"
                      style={{ '--role-color': role.color }}
                      aria-hidden="true"
                    />
                    <span className="assignment-role-copy">
                      <strong>{role.name}</strong>
                      <small>
                        {(role.permissions ?? []).length} permission
                        {(role.permissions ?? []).length === 1 ? '' : 's'}
                      </small>
                    </span>
                    <span className="assignment-role-check" aria-hidden="true">
                      {selectedRoleIds.includes(role.id) ? '✓' : ''}
                    </span>
                  </label>
                ))
              )}
            </div>
          </fieldset>

          <fieldset className="project-people-fieldset">
            <legend>
              <span>2</span>
              Select people
              {selectedMemberIds.length > 0 && (
                <em>{selectedMemberIds.length} selected</em>
              )}
            </legend>
            <input
              className="project-member-search"
              type="search"
              value={memberQuery}
              placeholder="Search workspace members…"
              aria-label="Search available workspace members"
              onChange={(event) => setMemberQuery(event.target.value)}
            />
            <div className="project-member-picker">
              {visibleAvailableMembers.length === 0 ? (
                <p>
                  {memberQuery
                    ? 'No matching workspace members.'
                    : 'Everyone is already in this project.'}
                </p>
              ) : (
                visibleAvailableMembers.map((member) => (
                  <label
                    className={`project-member-option ${
                      selectedMemberIds.includes(member.id) ? 'selected' : ''
                    }`}
                    key={member.id}
                  >
                    <input
                      type="checkbox"
                      checked={selectedMemberIds.includes(member.id)}
                      onChange={() => toggleSelectedMember(member.id)}
                    />
                    <span className="member-avatar" aria-hidden="true">
                      {memberInitials(member.displayName)}
                    </span>
                    <span className="project-member-option-copy">
                      <strong>{member.displayName}</strong>
                      <small>{member.email}</small>
                    </span>
                    <span className="assignment-role-check" aria-hidden="true">
                      {selectedMemberIds.includes(member.id) ? '✓' : ''}
                    </span>
                  </label>
                ))
              )}
            </div>
          </fieldset>
          <button
            className="primary-button project-add-people-button"
            disabled={isAdding || selectedMemberIds.length === 0}
          >
            {isAdding
              ? 'Adding people…'
              : selectedMemberIds.length === 0
                ? 'Select people to add'
                : `Add ${selectedMemberIds.length} ${
                    selectedMemberIds.length === 1 ? 'person' : 'people'
                  }`}
          </button>
        </form>
      )}

      {error && (
        <div className="form-message error" role="alert">
          {error}
        </div>
      )}

      <div className="project-team-table" aria-label="Project members">
        <div className="project-team-table-header">
          <span>Member</span>
          <span>Access</span>
        </div>
        {isLoading ? (
          <p className="project-team-state">Loading team…</p>
        ) : members.length === 0 ? (
          <p className="project-team-state">No project members yet.</p>
        ) : (
          <div className="project-team-list">
            {members.map((member) => (
              <div className="project-team-row" key={member.id}>
                <div className="project-team-member-cell">
                  <span
                    className="member-avatar"
                    aria-hidden="true"
                  >
                    {memberInitials(member.displayName)}
                  </span>
                  <div className="project-team-identity">
                    <strong title={member.displayName}>
                      {member.displayName}
                    </strong>
                    <small title={member.email}>{member.email}</small>
                  </div>
                </div>
                <div className="project-team-access-cell">
                  <span
                    className={roleBadgeClassName(
                      member.workspaceRole,
                      'member-role-badge',
                    )}
                  >
                    {formatRole(member.workspaceRole)}
                  </span>
                  <div className="project-member-custom-roles">
                    {(member.roles ?? []).length === 0 ? (
                      <span className="default-permissions-note">
                        Default permissions
                      </span>
                    ) : (
                      member.roles.map((role) => (
                        <span
                          className="custom-role-chip"
                          style={{ '--role-color': role.color }}
                          key={role.id}
                        >
                          {role.name}
                        </span>
                      ))
                    )}
                  </div>
                  {isManager && (
                    <>
                      <button
                        className="member-action-button"
                        type="button"
                        onClick={() => {
                          setEditingMemberId(member.id)
                          setPendingRoleIds(
                            (member.roles ?? []).map((role) => role.id),
                          )
                        }}
                      >
                        Edit access
                      </button>
                      <button
                        className="project-member-remove"
                        type="button"
                        aria-label={`Remove ${member.displayName} from project`}
                        disabled={removingId === member.id}
                        onClick={() => handleRemove(member.id)}
                      >
                        ×
                      </button>
                    </>
                  )}
                  {editingMemberId === member.id && (
                    <div className="project-member-role-editor">
                      <div className="project-member-role-editor-heading">
                        <strong>Project access</strong>
                        <small>{member.displayName}</small>
                      </div>
                      <button
                        className={`assignment-default-role compact ${
                          pendingRoleIds.length === 0 ? 'selected' : ''
                        }`}
                        type="button"
                        onClick={() => setPendingRoleIds([])}
                      >
                        <span className="assignment-role-icon">D</span>
                        <span>Default project access</span>
                        <span className="assignment-role-check">
                          {pendingRoleIds.length === 0 ? '✓' : ''}
                        </span>
                      </button>
                      {accessRoles.length === 0 ? (
                        <p>No custom roles available.</p>
                      ) : (
                        accessRoles.map((role) => (
                          <label
                            className={
                              pendingRoleIds.includes(role.id)
                                ? 'selected'
                                : ''
                            }
                            key={role.id}
                          >
                            <input
                              type="checkbox"
                              checked={pendingRoleIds.includes(role.id)}
                              onChange={() => togglePendingRole(role.id)}
                            />
                            <span
                              className="custom-role-dot"
                              style={{ '--role-color': role.color }}
                              aria-hidden="true"
                            />
                            <span className="assignment-role-copy">
                              <strong>{role.name}</strong>
                              <small>
                                {(role.permissions ?? []).length} permission
                                {(role.permissions ?? []).length === 1
                                  ? ''
                                  : 's'}
                              </small>
                            </span>
                            <span className="assignment-role-check">
                              {pendingRoleIds.includes(role.id) ? '✓' : ''}
                            </span>
                          </label>
                        ))
                      )}
                      <div>
                        <button
                          className="member-action-button"
                          type="button"
                          disabled={savingRolesId === member.id}
                          onClick={() => handleRolesSave(member.id)}
                        >
                          {savingRolesId === member.id
                            ? 'Saving…'
                            : 'Save'}
                        </button>
                        <button
                          className="member-action-button subtle"
                          type="button"
                          onClick={() => setEditingMemberId(null)}
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  )
}

function TaskAssigneePicker({ task, members, disabled, onSave }) {
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
      <div className="task-assignee-summary">
        <div className="task-assignee-avatars">
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
            <span className="task-unassigned">Unassigned</span>
          )}
        </div>
        {!disabled && (
          <button
            className="task-assign-toggle"
            type="button"
            onClick={() => setIsOpen((current) => !current)}
          >
            {isOpen ? 'Close' : 'Assign'}
          </button>
        )}
      </div>
      {isOpen && (
        <div className="task-assignee-menu">
          <label className="task-assignee-option">
            <input
              type="radio"
              name={`task-${task.id}-assignee`}
              checked={selectedId === null}
              onChange={() => setSelectedId(null)}
            />
            <span className="task-assignee-option-identity">
              <strong>Unassigned</strong>
              <small>Only workspace managers can change status</small>
            </span>
          </label>
          {members.map((member) => (
              <label className="task-assignee-option" key={member.id}>
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
                <span
                  className={roleBadgeClassName(
                    member.workspaceRole,
                    'task-assignee-role',
                  )}
                >
                  {formatRole(member.workspaceRole)}
                </span>
              </label>
            ))}
          <button
            className="task-assignee-save"
            type="button"
            disabled={isSaving}
            onClick={save}
          >
            {isSaving ? 'Saving…' : 'Save'}
          </button>
        </div>
      )}
    </div>
  )
}

function WorkspaceMembers({
  workspace,
  projects,
  projectTeams,
  accessRoles,
  onProjectTeamChange,
}) {
  const [members, setMembers] = useState([])
  const [form, setForm] = useState({ email: '', role: 'MEMBER' })
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isAddFormOpen, setIsAddFormOpen] = useState(false)
  const [editingMemberId, setEditingMemberId] = useState(null)
  const [pendingRole, setPendingRole] = useState('MEMBER')
  const [changingMemberId, setChangingMemberId] = useState(null)
  const [accessEditingMemberId, setAccessEditingMemberId] = useState(null)
  const [projectAccessDraft, setProjectAccessDraft] = useState({})
  const [savingProjectAccess, setSavingProjectAccess] = useState(false)

  const isOwner = workspace.role === 'OWNER'
  const isManager = ['OWNER', 'ADMIN'].includes(workspace.role)

  const loadMembers = useCallback(async () => {
    setError('')
    setIsLoading(true)

    try {
      setMembers(await getWorkspaceMembers(workspace.id))
    } catch (loadError) {
      setError(loadError.message || 'Unable to load members.')
    } finally {
      setIsLoading(false)
    }
  }, [workspace.id])

  useEffect(() => {
    loadMembers()
  }, [loadMembers])

  async function handleAdd(event) {
    event.preventDefault()
    setError('')
    setFieldErrors({})
    setIsSubmitting(true)

    try {
      const member = await addWorkspaceMember(workspace.id, form)
      setMembers((current) => [...current, member])
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
      setMembers((current) =>
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
      setMembers((current) =>
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

  function startProjectAccessEdit(member) {
    const draft = {}
    projects.forEach((project) => {
      const assignment = (projectTeams[project.id] ?? []).find(
        (projectMember) =>
          projectMember.workspaceMemberId === member.id,
      )
      draft[project.id] = {
        selected: Boolean(assignment),
        roleIds: (assignment?.roles ?? []).map((role) => role.id),
      }
    })
    setError('')
    setAccessEditingMemberId(member.id)
    setProjectAccessDraft(draft)
  }

  function toggleDraftProject(projectId) {
    setProjectAccessDraft((current) => ({
      ...current,
      [projectId]: {
        roleIds: current[projectId]?.roleIds ?? [],
        selected: !current[projectId]?.selected,
      },
    }))
  }

  function toggleDraftProjectRole(projectId, roleId) {
    setProjectAccessDraft((current) => {
      const roleIds = current[projectId]?.roleIds ?? []
      return {
        ...current,
        [projectId]: {
          selected: true,
          roleIds: roleIds.includes(roleId)
            ? roleIds.filter((id) => id !== roleId)
            : [...roleIds, roleId],
        },
      }
    })
  }

  async function saveProjectAccess(member) {
    setSavingProjectAccess(true)
    setError('')
    try {
      for (const project of projects) {
        const team = projectTeams[project.id] ?? []
        const existing = team.find(
          (projectMember) =>
            projectMember.workspaceMemberId === member.id,
        )
        const draft = projectAccessDraft[project.id] ?? {
          selected: false,
          roleIds: [],
        }

        if (draft.selected && !existing) {
          const added = await addProjectMember(
            workspace.id,
            project.id,
            member.id,
            draft.roleIds,
          )
          onProjectTeamChange(project.id, [...team, added])
        } else if (draft.selected && existing) {
          const existingRoleIds = (existing.roles ?? [])
            .map((role) => role.id)
            .sort()
          const nextRoleIds = [...draft.roleIds].sort()
          if (existingRoleIds.join(',') !== nextRoleIds.join(',')) {
            const updated = await replaceProjectMemberRoles(
              workspace.id,
              project.id,
              existing.id,
              draft.roleIds,
            )
            onProjectTeamChange(
              project.id,
              team.map((item) =>
                item.id === updated.id ? updated : item,
              ),
            )
          }
        } else if (!draft.selected && existing) {
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

      {isLoading ? (
        <div className="members-loading">
          <span className="loading-spinner" aria-hidden="true" />
          Loading members…
        </div>
      ) : (
        <div className="member-list">
          {members.map((member) => {
            const isWorkspaceOwner = member.role === 'OWNER'
            const isChanging = changingMemberId === member.id
            const isEditing = editingMemberId === member.id
            const isAccessEditing = accessEditingMemberId === member.id
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
                <div className="member-avatar" aria-hidden="true">
                  {member.displayName.slice(0, 1).toUpperCase()}
                </div>
                <div className="member-identity">
                  <strong>{member.displayName}</strong>
                  <span>{member.email}</span>
                </div>
                <span
                  className={roleBadgeClassName(
                    member.role,
                    'member-role-badge',
                  )}
                >
                  {formatRole(member.role)}
                </span>
                <div className="workspace-member-projects">
                  {assignedProjects.length === 0 ? (
                    <span>No project access</span>
                  ) : (
                    assignedProjects.map(({ project, assignment }) => (
                      <span
                        className="workspace-member-project-chip"
                        key={project.id}
                      >
                        <strong>{project.name}</strong>
                        <small>
                          {(assignment.roles ?? []).length === 0
                            ? 'Direct'
                            : assignment.roles
                                .map((role) => role.name)
                                .join(', ')}
                        </small>
                      </span>
                    ))
                  )}
                </div>
                {isManager && !isWorkspaceOwner && (
                  <button
                    className="member-action-button project-access-button"
                    type="button"
                    onClick={() =>
                      isAccessEditing
                        ? setAccessEditingMemberId(null)
                        : startProjectAccessEdit(member)
                    }
                  >
                    {isAccessEditing ? 'Close access' : 'Projects & roles'}
                  </button>
                )}
                {isOwner && !isWorkspaceOwner ? (
                  isEditing ? (
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
                  ) : (
                    <div className="member-controls">
                      <button
                        className="member-action-button"
                        type="button"
                        onClick={() => startRoleEdit(member)}
                      >
                        Edit role
                      </button>
                      <button
                        className="danger-button"
                        type="button"
                        disabled={isChanging}
                        onClick={() => handleRemove(member.id)}
                      >
                        Remove
                      </button>
                    </div>
                  )
                ) : null}
                {isAccessEditing && (
                  <div className="workspace-project-access-editor">
                    <div className="workspace-project-access-heading">
                      <div>
                        <strong>Projects for {member.displayName}</strong>
                        <small>
                          Select a project, then attach workspace roles.
                          Leave roles empty for direct access.
                        </small>
                      </div>
                      <span>{assignedProjects.length} active</span>
                    </div>
                    {projects.length === 0 ? (
                      <p>No projects in this workspace.</p>
                    ) : (
                      <div className="workspace-project-access-list">
                        {projects.map((project) => {
                          const draft = projectAccessDraft[project.id] ?? {
                            selected: false,
                            roleIds: [],
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
                                    {project.visibility === 'RESTRICTED'
                                      ? 'Restricted'
                                      : 'Workspace'}
                                  </small>
                                </span>
                              </label>
                              {draft.selected && (
                                <div className="workspace-project-role-options">
                                  <button
                                    className={`workspace-project-direct ${
                                      draft.roleIds.length === 0
                                        ? 'selected'
                                        : ''
                                    }`}
                                    type="button"
                                    onClick={() =>
                                      setProjectAccessDraft((current) => ({
                                        ...current,
                                        [project.id]: {
                                          selected: true,
                                          roleIds: [],
                                        },
                                      }))
                                    }
                                  >
                                    Direct access
                                  </button>
                                  {accessRoles.map((role) => (
                                    <label
                                      className={
                                        draft.roleIds.includes(role.id)
                                          ? 'selected'
                                          : ''
                                      }
                                      key={role.id}
                                    >
                                      <input
                                        type="checkbox"
                                        checked={draft.roleIds.includes(
                                          role.id,
                                        )}
                                        onChange={() =>
                                          toggleDraftProjectRole(
                                            project.id,
                                            role.id,
                                          )
                                        }
                                      />
                                      <span
                                        className="custom-role-dot"
                                        style={{
                                          '--role-color': role.color,
                                        }}
                                      />
                                      {role.name}
                                    </label>
                                  ))}
                                </div>
                              )}
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
                        onClick={() => setAccessEditingMemberId(null)}
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
                )}
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
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
    content = <Dashboard user={user} onLogout={handleLogout} />
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
      <ThemeToggle
        theme={theme}
        onToggle={() =>
          setTheme((current) => (current === 'dark' ? 'light' : 'dark'))
        }
      />
      {content}
    </>
  )
}

export default App
