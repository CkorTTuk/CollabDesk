<p align="center">
  <img src="frontend/public/collabdesk.svg" width="88" alt="CollabDesk logo">
</p>

<h1 align="center">CollabDesk</h1>

<p align="center">
  A collaborative workspace for managing projects, teams, tasks, and fine-grained access.
</p>

<p align="center">
  <a href="#features">Features</a> ·
  <a href="#tech-stack">Tech stack</a> ·
  <a href="#local-development">Local development</a> ·
  <a href="#api-documentation">API documentation</a>
</p>

## About

CollabDesk brings workspaces, projects, teams, and tasks together in a single interface. It supports built-in and custom roles, restricted projects, task assignment, and a detailed task activity history.

The backend is a session-based REST API built with Spring Boot, while the frontend is a React single-page application. MySQL schema changes are managed by Flyway, and Redis can optionally cache aggregated project-access data.

## Features

- Email registration with a six-digit verification code
- Sign-in with email and password, Google OpenID Connect, or GitHub OAuth2
- New-user onboarding and account profile management
- Avatar upload and removal
- English, Russian, and Slovak interface languages
- Light and dark themes
- Multiple workspaces per user
- Built-in workspace roles: `OWNER`, `ADMIN`, `MEMBER`, and `VIEWER`
- Workspace membership and access management
- Public and restricted projects
- Custom roles with project-level permissions
- Project team management
- Tasks with `TODO`, `IN_PROGRESS`, and `DONE` statuses
- A single assignee per task, including self-claim and release flows
- Project-wide or assignee-only task visibility
- Task activity history
- OpenAPI specification and Swagger UI
- Redis-backed project-access cache with graceful degradation when Redis is unavailable

## Tech stack

| Area | Technologies |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1, Spring MVC, Spring Security, Spring Data JPA |
| Authentication | HTTP sessions, CSRF, OAuth2 Client, OpenID Connect |
| Database | MySQL 8.4, Flyway |
| Cache | Redis 8, Spring Cache |
| API documentation | springdoc-openapi, Swagger UI |
| Frontend | React 19, Vite 8, CSS |
| Testing | JUnit 5, Spring Boot Test, Testcontainers, Mockito |
| Infrastructure | Docker Compose, Mailpit |

## Architecture

```text
Browser (React/Vite)
        │
        │ REST, session cookie, CSRF
        ▼
Spring Boot API ───────────────► Local avatar storage
        │
        ├──────────────────────► MySQL
        ├──────────────────────► Redis (redis profile)
        └──────────────────────► SMTP / Mailpit
```

The backend is organized by domain: `auth`, `account`, `workspace`, `project`, and `task`. Within each domain, HTTP controllers are separated from services, repositories, entities, and DTOs. Database migrations are stored in `src/main/resources/db/migration`.

## Requirements

- JDK 21
- Docker with Docker Compose
- Node.js `^20.19.0` or `>=22.12.0`
- npm
- Google and/or GitHub OAuth applications if social sign-in is required

Maven and MySQL do not need to be installed separately. The repository includes Maven Wrapper, and the supporting services run in Docker.

## Local development

### 1. Clone the repository

```bash
git clone https://github.com/CkorTTuk/CollabDesk.git
cd CollabDesk
```

### 2. Configure the environment

Copy the example environment file:

```bash
# Linux / macOS
cp .env.example .env

# Windows PowerShell
Copy-Item .env.example .env
```

When running the frontend through Vite, set the following value in `.env`:

```dotenv
APP_FRONTEND_URL=http://localhost:5173
```

Replace `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GITHUB_CLIENT_ID`, and `GITHUB_CLIENT_SECRET` with real credentials if you want to use external identity providers. Mailpit is sufficient for testing local email registration.

### 3. Start the supporting services

```bash
docker compose up -d
```

Docker Compose starts the following services:

| Service | Address |
| --- | --- |
| MySQL | `localhost:3306` |
| Redis | `localhost:6379` |
| SMTP | `localhost:1025` |
| Mailpit UI | [http://localhost:8025](http://localhost:8025) |

Verification emails are captured locally and can be opened in the Mailpit UI.

### 4. Start the backend

```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows
.\mvnw.cmd spring-boot:run
```

The API starts at [http://localhost:8080](http://localhost:8080). Flyway applies pending database migrations automatically during startup.

To enable the Redis cache, start the backend with the `redis` profile:

```bash
# Linux / macOS
SPRING_PROFILES_ACTIVE=redis ./mvnw spring-boot:run

# Windows PowerShell
$env:SPRING_PROFILES_ACTIVE='redis'; .\mvnw.cmd spring-boot:run
```

### 5. Start the frontend

Open another terminal and run:

```bash
cd frontend
npm ci
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). The Vite development server proxies API, CSRF, and OAuth requests to the backend.

## OAuth setup

Configure the following callback URLs in your OAuth applications:

- Google: `http://localhost:8080/login/oauth2/code/google`
- GitHub: `http://localhost:8080/login/oauth2/code/github`

After a successful OAuth sign-in, the backend redirects the browser to the URL configured through `APP_FRONTEND_URL`.

## API documentation

Once authenticated, the following endpoints are available:

- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

The API uses HTTP sessions and CSRF protection. Before a state-changing request, a client retrieves a token from `GET /csrf` and sends it through the response-defined header. The frontend handles this automatically.

### Endpoint groups

| Area | Base path |
| --- | --- |
| Authentication | `/api/v1/auth` |
| Account and onboarding | `/api/v1/account` |
| Workspaces | `/api/v1/workspaces` |
| Workspace members | `/api/v1/workspaces/{workspaceId}/members` |
| Projects | `/api/v1/workspaces/{workspaceId}/projects` |
| Access roles | `/api/v1/workspaces/{workspaceId}/access-roles` |
| Project members | `/api/v1/workspaces/{workspaceId}/projects/{projectId}/members` |
| Tasks | `/api/v1/workspaces/{workspaceId}/projects/{projectId}/tasks` |

## Testing

Backend integration tests use Testcontainers, so Docker must be running:

```bash
# Linux / macOS
./mvnw test

# Windows
.\mvnw.cmd test
```

Run the frontend checks with:

```bash
cd frontend
npm ci
npm run lint
npm run build
```

## Configuration

The main environment variables are documented in `.env.example`:

| Variable | Purpose |
| --- | --- |
| `DATABASE_URL` | JDBC database URL |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | MySQL credentials |
| `DATABASE_ROOT_PASSWORD` | MySQL root password used by Docker Compose |
| `REDIS_HOST` / `REDIS_PORT` | Redis connection settings |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OpenID Connect credentials |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | GitHub OAuth2 credentials |
| `MAIL_*` | SMTP connection and sender settings |
| `VERIFICATION_CODE_PEPPER` | Secret used when hashing verification codes |
| `APP_FRONTEND_URL` | Frontend URL used for OAuth redirects |
| `AVATAR_*` | Local avatar storage and upload limits |
| `OPENAPI_ENABLED` | Enables or disables OpenAPI and Swagger UI |

Do not commit `.env`, OAuth secrets, or files stored under `data/avatars`.

## Repository structure

```text
CollabDesk/
├── frontend/                    # React SPA
│   ├── public/                  # Static assets
│   └── src/                     # UI, API clients, and localization
├── src/main/java/collabdesk/    # Spring Boot application
├── src/main/resources/
│   ├── db/migration/            # Flyway migrations
│   └── application*.properties  # Spring configuration
├── src/test/                    # Unit and integration tests
├── docker-compose.yaml          # MySQL, Redis, and Mailpit
├── pom.xml
└── .env.example
```

## License

No license has been specified for this project yet.
