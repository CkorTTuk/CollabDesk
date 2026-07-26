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
import { createProject, getProjects } from './api/projectApi.js'
import {
  changeTaskStatus,
  createTask,
  getTasks,
} from './api/taskApi.js'
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
        <div className="dashboard-title">
          <div>
            <p className="eyebrow">Overview</p>
            <h1>Welcome, {user.displayName}.</h1>
            <p>Your teams and active work are ready in one place.</p>
          </div>
        </div>

        {error && (
          <div className="form-message error" role="alert">
            {error}
          </div>
        )}

        <WorkspaceSection />
      </main>
    </div>
  )
}

function WorkspaceSection() {
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
        onBack={closeWorkspace}
      />
    )
  }

  return (
    <section className="workspace-panel">
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
              onClick={() => openWorkspace(workspace)}
            >
              <div className="workspace-card-top">
                <div className="workspace-letter" aria-hidden="true">
                  {workspace.name.trim().charAt(0).toUpperCase()}
                </div>
                <span className="role-badge">
                  {workspace.role === 'OWNER'
                    ? 'Owner'
                    : workspace.role}
                </span>
              </div>
              <h3>{workspace.name}</h3>
              <p>
                {workspace.description ||
                  'No workspace description has been added yet.'}
              </p>
              <div className="workspace-card-footer">
                <span>Open workspace →</span>
              </div>
            </button>
          ))}
        </div>
      )}
    </section>
  )
}

function ProjectSection({ workspace, onBack }) {
  const [projects, setProjects] = useState([])
  const [selectedProject, setSelectedProject] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isCreating, setIsCreating] = useState(false)
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [isMembersOpen, setIsMembersOpen] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [form, setForm] = useState({ name: '', description: '' })

  const loadProjects = useCallback(async () => {
    setError('')
    setIsLoading(true)

    try {
      const loadedProjects = await getProjects(workspace.id)
      setProjects(loadedProjects)
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

  if (selectedProject) {
    return (
      <TaskBoard
        workspace={workspace}
        project={selectedProject}
        onBack={closeProject}
      />
    )
  }

  return (
    <section className="workspace-panel project-panel">
      <button className="project-back" type="button" onClick={onBack}>
        ← All workspaces
      </button>

      <div className="workspace-panel-header project-panel-header">
        <div>
          <p className="eyebrow">Workspace</p>
          <h2>{workspace.name}</h2>
          <p>
            {workspace.description ||
              'Projects and tasks for this workspace.'}
          </p>
        </div>
        <div className="workspace-header-actions">
          {workspace.role === 'VIEWER' && (
            <span className="read-only-badge">View only</span>
          )}
          <button
            className="secondary-button"
            type="button"
            onClick={() => setIsMembersOpen((current) => !current)}
          >
            {isMembersOpen ? 'Hide members' : 'Members'}
          </button>
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

      {isMembersOpen && (
        <WorkspaceMembers workspace={workspace} />
      )}

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
          {projects.map((project) => (
            <button
              className="project-card"
              key={project.id}
              type="button"
              onClick={() => openProject(project)}
            >
              <div className="project-card-top">
                <span className="project-status">
                  <span aria-hidden="true" />
                  {project.status === 'ACTIVE'
                    ? 'Active'
                    : project.status}
                </span>
                <span>#{project.id}</span>
              </div>
              <h3>{project.name}</h3>
              <p>
                {project.description ||
                  'No project description has been added yet.'}
              </p>
              <div className="project-card-footer">
                <span>Open tasks →</span>
              </div>
            </button>
          ))}
        </div>
      )}
    </section>
  )
}

function TaskBoard({ workspace, project, onBack }) {
  const [tasks, setTasks] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [isCreating, setIsCreating] = useState(false)
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [updatingTaskId, setUpdatingTaskId] = useState(null)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [form, setForm] = useState({ title: '', description: '' })

  const loadTasks = useCallback(async () => {
    setError('')
    setIsLoading(true)

    try {
      setTasks(await getTasks(workspace.id, project.id))
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

  return (
    <section className="workspace-panel task-panel">
      <button className="project-back" type="button" onClick={onBack}>
        ← Workspace projects
      </button>

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
          {workspace.role === 'VIEWER' && (
            <span className="read-only-badge">View only</span>
          )}
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
                        </div>
                        <h4>{task.title}</h4>
                        <p>
                          {task.description ||
                            'No task description has been added yet.'}
                        </p>
                        <label className="task-status-control">
                          <span>Status</span>
                          <select
                            value={task.status}
                            disabled={
                              workspace.role === 'VIEWER' ||
                              updatingTaskId === task.id
                            }
                            onChange={(event) =>
                              handleStatusChange(
                                task.id,
                                event.target.value,
                              )
                            }
                          >
                            {TASK_COLUMNS.map((option) => (
                              <option
                                key={option.status}
                                value={option.status}
                              >
                                {option.title}
                              </option>
                            ))}
                          </select>
                        </label>
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
  )
}

function WorkspaceMembers({ workspace }) {
  const [members, setMembers] = useState([])
  const [form, setForm] = useState({ email: '', role: 'MEMBER' })
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [changingMemberId, setChangingMemberId] = useState(null)

  const isOwner = workspace.role === 'OWNER'

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
    } catch (removeError) {
      setError(removeError.message || 'Unable to remove the member.')
    } finally {
      setChangingMemberId(null)
    }
  }

  return (
    <section className="members-panel">
      <div className="members-panel-heading">
        <div>
          <p className="eyebrow">Workspace team</p>
          <h3>Members</h3>
        </div>
        {!isOwner && <span>Only the owner can manage members</span>}
      </div>

      {isOwner && (
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

            return (
              <article className="member-row" key={member.id}>
                <div className="member-avatar" aria-hidden="true">
                  {member.displayName.slice(0, 1).toUpperCase()}
                </div>
                <div className="member-identity">
                  <strong>{member.displayName}</strong>
                  <span>{member.email}</span>
                </div>
                <time dateTime={member.joinedAt}>
                  Joined {formatProjectDate(member.joinedAt)}
                </time>
                {isOwner && !isWorkspaceOwner ? (
                  <div className="member-controls">
                    <RolePicker
                      compact
                      label={`Role for ${member.displayName}`}
                      value={member.role}
                      disabled={isChanging}
                      onChange={(role) =>
                        handleRoleChange(member.id, role)
                      }
                    />
                    <button
                      className="danger-button"
                      type="button"
                      disabled={isChanging}
                      onClick={() => handleRemove(member.id)}
                    >
                      Remove
                    </button>
                  </div>
                ) : (
                  <span className="role-badge">{member.role}</span>
                )}
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
}

function formatProjectDate(createdAt) {
  const date = new Date(createdAt)

  if (Number.isNaN(date.getTime())) {
    return ''
  }

  return new Intl.DateTimeFormat('en-US', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(date)
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
