import { useCallback, useEffect, useState } from 'react'
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
  { status: 'TODO', title: 'К выполнению' },
  { status: 'IN_PROGRESS', title: 'В работе' },
  { status: 'DONE', title: 'Готово' },
]

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
      setMessage(error.message || 'Не удалось выполнить вход.')
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
      setMessage('Аккаунт создан. Теперь войдите с указанным паролем.')
      setMessageType('success')
    } catch (error) {
      setFieldErrors(error.fieldErrors ?? {})
      setMessage(error.message || 'Не удалось создать аккаунт.')
      setMessageType('error')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-intro" aria-label="О продукте">
        <Brand />

        <div className="intro-copy">
          <p className="eyebrow">Командная работа без лишнего шума</p>
          <h1>Все рабочие задачи в одном понятном пространстве.</h1>
          <p className="intro-text">
            CollabDesk объединяет проекты, задачи и обсуждения команды. Сейчас
            доступна безопасная регистрация и вход — рабочие пространства
            появятся следующим этапом.
          </p>
        </div>

        <div className="feature-list" aria-label="Возможности">
          <div className="feature">
            <span className="feature-icon" aria-hidden="true">
              01
            </span>
            <div>
              <strong>Надёжная сессия</strong>
              <span>Spring Security и CSRF-защита</span>
            </div>
          </div>
          <div className="feature">
            <span className="feature-icon" aria-hidden="true">
              02
            </span>
            <div>
              <strong>Готово к росту</strong>
              <span>Workspace, проекты и задачи дальше</span>
            </div>
          </div>
        </div>

        <p className="intro-footer">Java 21 · Spring Boot · React</p>
      </section>

      <section className="auth-panel">
        <div className="auth-card">
          <div className="mobile-brand">
            <Brand />
          </div>

          <div className="auth-heading">
            <p className="eyebrow">{isLogin ? 'С возвращением' : 'Новый аккаунт'}</p>
            <h2>{isLogin ? 'Войти в CollabDesk' : 'Создать аккаунт'}</h2>
            <p>
              {isLogin
                ? 'Введите данные, указанные при регистрации.'
                : 'Начните с личного профиля — команда подключится позже.'}
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
                label="Пароль"
                type="password"
                autoComplete="current-password"
                placeholder="Минимум 8 символов"
                value={loginForm.password}
                onChange={(value) =>
                  setLoginForm((current) => ({ ...current, password: value }))
                }
                error={fieldErrors.password}
              />
              <button className="primary-button" disabled={isSubmitting}>
                {isSubmitting ? 'Входим…' : 'Войти'}
              </button>
            </form>
          ) : (
            <form className="auth-form" onSubmit={handleRegistration}>
              <FormField
                id="register-name"
                label="Как вас зовут"
                type="text"
                autoComplete="name"
                placeholder="Алексей"
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
                label="Пароль"
                type="password"
                autoComplete="new-password"
                placeholder="От 8 до 64 символов"
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
                {isSubmitting ? 'Создаём аккаунт…' : 'Зарегистрироваться'}
              </button>
            </form>
          )}

          <p className="mode-switch">
            {isLogin ? 'Ещё нет аккаунта?' : 'Уже зарегистрированы?'}
            <button
              type="button"
              onClick={() => changeMode(isLogin ? 'register' : 'login')}
            >
              {isLogin ? 'Создать' : 'Войти'}
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
      setError(logoutError.message || 'Не удалось выйти из аккаунта.')
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
            {isLoggingOut ? 'Выходим…' : 'Выйти'}
          </button>
        </div>
      </header>

      <main className="dashboard-main">
        <div className="dashboard-title">
          <div>
            <p className="eyebrow">Личный кабинет</p>
            <h1>Добро пожаловать, {user.displayName}.</h1>
            <p>Ваша сессия активна и восстановится после обновления страницы.</p>
          </div>
          <span className="status-badge">
            <span aria-hidden="true" />
            {user.status === 'ACTIVE' ? 'Аккаунт активен' : user.status}
          </span>
        </div>

        {error && (
          <div className="form-message error" role="alert">
            {error}
          </div>
        )}

        <WorkspaceSection />

        <section className="account-grid">
          <article>
            <span className="card-label">Профиль</span>
            <strong>{user.displayName}</strong>
            <p>{user.email}</p>
          </article>
          <article>
            <span className="card-label">Идентификатор</span>
            <strong>#{user.id}</strong>
            <p>Внутренний ID пользователя</p>
          </article>
          <article>
            <span className="card-label">Безопасность</span>
            <strong>Session-based</strong>
            <p>Защищено Spring Security</p>
          </article>
        </section>
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

      const storedWorkspaceId = Number(
        localStorage.getItem('collabdesk.selectedWorkspaceId'),
      )
      const storedWorkspace = loadedWorkspaces.find(
        (workspace) => workspace.id === storedWorkspaceId,
      )
      setSelectedWorkspace(storedWorkspace ?? null)

      if (!storedWorkspace) {
        localStorage.removeItem('collabdesk.selectedWorkspaceId')
        localStorage.removeItem('collabdesk.selectedProjectId')
      }
    } catch (loadError) {
      if (loadError.status === 404) {
        setModuleUnavailable(true)
      } else {
        setError(loadError.message || 'Не удалось загрузить workspaces.')
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
        createError.message || 'Не удалось создать рабочее пространство.',
      )
    } finally {
      setIsCreating(false)
    }
  }

  function openWorkspace(workspace) {
    localStorage.setItem(
      'collabdesk.selectedWorkspaceId',
      String(workspace.id),
    )
    setSelectedWorkspace(workspace)
  }

  function closeWorkspace() {
    localStorage.removeItem('collabdesk.selectedWorkspaceId')
    localStorage.removeItem('collabdesk.selectedProjectId')
    setSelectedWorkspace(null)
  }

  if (isLoading) {
    return (
      <section className="workspace-panel workspace-loading">
        <span className="loading-spinner" aria-hidden="true" />
        <p>Загружаем рабочие пространства…</p>
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
          <p className="eyebrow">Backend этап 7</p>
          <h2>Workspace API ещё не подключён</h2>
          <p>
            Интерфейс уже ожидает GET и POST на{' '}
            <code>/api/v1/workspaces</code>. Реализуйте backend по новой
            инструкции и повторите проверку.
          </p>
        </div>
        <button className="secondary-button" onClick={loadWorkspaces}>
          Проверить снова
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
          <p className="eyebrow">Рабочие пространства</p>
          <h2>Ваши команды</h2>
          <p>Проекты и задачи будут организованы внутри workspace.</p>
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
          {isFormOpen ? 'Закрыть' : '+ Создать workspace'}
        </button>
      </div>

      {isFormOpen && (
        <form className="workspace-form" onSubmit={handleCreate}>
          <div className="workspace-form-grid">
            <FormField
              id="workspace-name"
              label="Название"
              type="text"
              placeholder="Например, Product Team"
              minLength={2}
              maxLength={100}
              value={form.name}
              onChange={(value) =>
                setForm((current) => ({ ...current, name: value }))
              }
              error={fieldErrors.name}
            />
            <label className="form-field" htmlFor="workspace-description">
              <span>Описание <em>необязательно</em></span>
              <textarea
                id="workspace-description"
                maxLength={500}
                placeholder="Чем занимается эта команда?"
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
            <span>Вы автоматически станете владельцем workspace.</span>
            <button className="primary-button" disabled={isCreating}>
              {isCreating ? 'Создаём…' : 'Создать'}
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
          <h3>Пока нет ни одного workspace</h3>
          <p>Создайте первое пространство для своей команды.</p>
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
                    ? 'Владелец'
                    : workspace.role}
                </span>
              </div>
              <h3>{workspace.name}</h3>
              <p>
                {workspace.description ||
                  'Описание рабочего пространства пока не добавлено.'}
              </p>
              <div className="workspace-card-footer">
                <span>ID #{workspace.id}</span>
                <span>Открыть →</span>
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

      const storedProjectId = Number(
        localStorage.getItem('collabdesk.selectedProjectId'),
      )
      const storedProject = loadedProjects.find(
        (project) => project.id === storedProjectId,
      )
      setSelectedProject(storedProject ?? null)

      if (!storedProject) {
        localStorage.removeItem('collabdesk.selectedProjectId')
      }
    } catch (loadError) {
      setError(
        loadError.message ||
          'Не удалось загрузить проекты рабочего пространства.',
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
      setError(createError.message || 'Не удалось создать проект.')
    } finally {
      setIsCreating(false)
    }
  }

  function openProject(project) {
    localStorage.setItem(
      'collabdesk.selectedProjectId',
      String(project.id),
    )
    setSelectedProject(project)
  }

  function closeProject() {
    localStorage.removeItem('collabdesk.selectedProjectId')
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
        ← Все workspace
      </button>

      <div className="workspace-panel-header project-panel-header">
        <div>
          <p className="eyebrow">Workspace #{workspace.id}</p>
          <h2>{workspace.name}</h2>
          <p>
            {workspace.description ||
              'Проекты и будущие задачи этого рабочего пространства.'}
          </p>
        </div>
        <div className="workspace-header-actions">
          {workspace.role === 'VIEWER' && (
            <span className="read-only-badge">Только просмотр</span>
          )}
          <button
            className="secondary-button"
            type="button"
            onClick={() => setIsMembersOpen((current) => !current)}
          >
            {isMembersOpen ? 'Скрыть участников' : 'Участники'}
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
              {isFormOpen ? 'Закрыть' : '+ Создать project'}
            </button>
          )}
        </div>
      </div>

      {isMembersOpen && (
        <WorkspaceMembers workspace={workspace} />
      )}

      {isFormOpen && (
        <form className="workspace-form" onSubmit={handleCreate}>
          <div className="workspace-form-grid">
            <FormField
              id="project-name"
              label="Название проекта"
              type="text"
              placeholder="Например, CollabDesk MVP"
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
                Описание <em>необязательно</em>
              </span>
              <textarea
                id="project-description"
                maxLength={500}
                placeholder="Какой результат должен дать этот проект?"
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
            <span>Project будет создан внутри {workspace.name}.</span>
            <button className="primary-button" disabled={isCreating}>
              {isCreating ? 'Создаём…' : 'Создать project'}
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
            Повторить
          </button>
        </div>
      )}

      {isLoading ? (
        <div className="project-loading">
          <span className="loading-spinner" aria-hidden="true" />
          <p>Загружаем проекты…</p>
        </div>
      ) : projects.length === 0 && !error ? (
        <div className="workspace-empty project-empty">
          <div className="project-empty-mark" aria-hidden="true">
            P
          </div>
          <h3>В этом workspace пока нет проектов</h3>
          <p>Создайте первый project и он сохранится в MySQL.</p>
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
                    ? 'Активен'
                    : project.status}
                </span>
                <span>#{project.id}</span>
              </div>
              <h3>{project.name}</h3>
              <p>
                {project.description ||
                  'Описание проекта пока не добавлено.'}
              </p>
              <div className="project-card-footer">
                <span>Workspace #{project.workspaceId}</span>
                <span>Открыть задачи →</span>
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
      setError(loadError.message || 'Не удалось загрузить задачи.')
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
      setError(createError.message || 'Не удалось создать задачу.')
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
        updateError.message || 'Не удалось изменить статус задачи.',
      )
    } finally {
      setUpdatingTaskId(null)
    }
  }

  return (
    <section className="workspace-panel task-panel">
      <button className="project-back" type="button" onClick={onBack}>
        ← Проекты workspace
      </button>

      <div className="workspace-panel-header task-panel-header">
        <div>
          <p className="eyebrow">
            {workspace.name} · Project #{project.id}
          </p>
          <h2>{project.name}</h2>
          <p>
            {project.description ||
              'Управляйте задачами и их текущим статусом.'}
          </p>
        </div>
        <div className="workspace-header-actions">
          {workspace.role === 'VIEWER' && (
            <span className="read-only-badge">Только просмотр</span>
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
              {isFormOpen ? 'Закрыть' : '+ Создать task'}
            </button>
          )}
        </div>
      </div>

      {isFormOpen && (
        <form className="workspace-form task-form" onSubmit={handleCreate}>
          <div className="workspace-form-grid">
            <FormField
              id="task-title"
              label="Название задачи"
              type="text"
              placeholder="Например, добавить API-клиент"
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
                Описание <em>необязательно</em>
              </span>
              <textarea
                id="task-description"
                maxLength={1000}
                placeholder="Что именно нужно сделать?"
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
            <span>Новая задача появится в колонке «К выполнению».</span>
            <button className="primary-button" disabled={isCreating}>
              {isCreating ? 'Создаём…' : 'Создать task'}
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
            Обновить доску
          </button>
        </div>
      )}

      {isLoading ? (
        <div className="project-loading">
          <span className="loading-spinner" aria-hidden="true" />
          <p>Загружаем задачи…</p>
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
                    <p className="task-column-empty">Задач пока нет</p>
                  ) : (
                    columnTasks.map((task) => (
                      <article className="task-card" key={task.id}>
                        <div className="task-card-meta">
                          <span>Task #{task.id}</span>
                          <time dateTime={task.createdAt}>
                            {formatProjectDate(task.createdAt)}
                          </time>
                        </div>
                        <h4>{task.title}</h4>
                        <p>
                          {task.description ||
                            'Описание задачи пока не добавлено.'}
                        </p>
                        <label className="task-status-control">
                          <span>Статус</span>
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
      setError(loadError.message || 'Не удалось загрузить участников.')
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
      setError(addError.message || 'Не удалось добавить участника.')
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
      setError(changeError.message || 'Не удалось изменить роль.')
    } finally {
      setChangingMemberId(null)
    }
  }

  async function handleRemove(memberId) {
    setError('')
    setChangingMemberId(memberId)

    try {
      await removeWorkspaceMember(workspace.id, memberId)
      setMembers((current) =>
        current.filter((member) => member.id !== memberId),
      )
    } catch (removeError) {
      setError(removeError.message || 'Не удалось удалить участника.')
    } finally {
      setChangingMemberId(null)
    }
  }

  return (
    <section className="members-panel">
      <div className="members-panel-heading">
        <div>
          <p className="eyebrow">Команда workspace</p>
          <h3>Участники</h3>
        </div>
        {!isOwner && <span>Управление доступно владельцу</span>}
      </div>

      {isOwner && (
        <form className="member-add-form" onSubmit={handleAdd}>
          <FormField
            id="member-email"
            label="Email зарегистрированного пользователя"
            type="email"
            placeholder="member@example.com"
            maxLength={320}
            value={form.email}
            onChange={(email) =>
              setForm((current) => ({ ...current, email }))
            }
            error={fieldErrors.email}
          />
          <label className="form-field" htmlFor="member-role">
            <span>Роль</span>
            <select
              id="member-role"
              value={form.role}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  role: event.target.value,
                }))
              }
            >
              <option value="ADMIN">Admin</option>
              <option value="MEMBER">Member</option>
              <option value="VIEWER">Viewer</option>
            </select>
          </label>
          <button className="primary-button" disabled={isSubmitting}>
            {isSubmitting ? 'Добавляем…' : 'Добавить'}
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
          Загружаем участников…
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
                  с {formatProjectDate(member.joinedAt)}
                </time>
                {isOwner && !isWorkspaceOwner ? (
                  <div className="member-controls">
                    <select
                      aria-label={`Роль ${member.displayName}`}
                      value={member.role}
                      disabled={isChanging}
                      onChange={(event) =>
                        handleRoleChange(member.id, event.target.value)
                      }
                    >
                      <option value="ADMIN">Admin</option>
                      <option value="MEMBER">Member</option>
                      <option value="VIEWER">Viewer</option>
                    </select>
                    <button
                      className="danger-button"
                      type="button"
                      disabled={isChanging}
                      onClick={() => handleRemove(member.id)}
                    >
                      Удалить
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

  return new Intl.DateTimeFormat('ru-RU', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(date)
}

function LoadingScreen() {
  return (
    <main className="loading-screen">
      <Brand />
      <span className="loading-spinner" aria-label="Проверяем сессию" />
      <p>Проверяем текущую сессию…</p>
    </main>
  )
}

function App() {
  const [user, setUser] = useState(null)
  const [mode, setMode] = useState('login')
  const [isLoading, setIsLoading] = useState(true)
  const [startupError, setStartupError] = useState('')

  async function restoreSession() {
    setStartupError('')
    setIsLoading(true)

    try {
      setUser(await getCurrentUser())
    } catch (error) {
      setStartupError(
        error.message ||
          'Backend недоступен. Запустите MySQL и Spring Boot, затем повторите.',
      )
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    restoreSession()
  }, [])

  if (isLoading) {
    return <LoadingScreen />
  }

  if (startupError) {
    return (
      <main className="connection-page">
        <Brand />
        <div className="connection-card">
          <span className="connection-code">503</span>
          <p className="eyebrow">Нет соединения</p>
          <h1>Backend пока недоступен</h1>
          <p>{startupError}</p>
          <button className="primary-button" onClick={restoreSession}>
            Повторить подключение
          </button>
          <code>docker compose up -d mysql</code>
          <code>.\mvnw.cmd spring-boot:run</code>
        </div>
      </main>
    )
  }

  if (user) {
    return <Dashboard user={user} onLogout={() => setUser(null)} />
  }

  return (
    <AuthShell
      mode={mode}
      onModeChange={setMode}
      onAuthenticated={setUser}
    />
  )
}

export default App
