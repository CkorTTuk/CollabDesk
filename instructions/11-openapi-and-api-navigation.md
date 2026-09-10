# Этап 11: OpenAPI, Swagger UI и карта REST API

## Результат этапа

После завершения backend автоматически строит OpenAPI specification из
Spring MVC controllers, DTO и validation annotations.

После входа в CollabDesk разработчик сможет открыть:

```text
http://localhost:8080/swagger-ui.html
```

и увидеть API, сгруппированный по возможностям:

```text
Authentication
Workspaces
Workspace members
Projects
Tasks
Infrastructure
```

Raw specification будет доступна по адресам:

```text
GET /v3/api-docs
GET /v3/api-docs.yaml
```

Swagger UI не заменяет frontend. Это интерактивная документация и инструмент
для разработки, проверки контрактов и поиска нужного endpoint.

---

## 1. Границы этапа

Реализуем:

- подключение springdoc для Spring Boot 4;
- генерацию OpenAPI 3 specification;
- Swagger UI;
- общую информацию о CollabDesk API;
- tags для controllers;
- понятные summary и response codes;
- описание session cookie и CSRF header;
- документирование validation constraints;
- тесты доступности specification;
- проверку, что все существующие endpoints попали в документацию.

Пока не реализуем:

- генерацию frontend-клиента из OpenAPI;
- публикацию документации в интернет;
- Maven plugin для сохранения статического `openapi.json`;
- OAuth2;
- JWT;
- замену HTTP session;
- кастомные workspace/project roles;
- project membership;
- task assignees;
- restricted task visibility.

OpenAPI не должен менять бизнес-логику приложения.

---

## 2. Версия dependency

Проект использует:

```xml
<version>4.1.0</version>
```

Для Spring Boot 4 используется springdoc 3.x.

Добавь в `pom.xml`:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>3.0.3</version>
</dependency>
```

Не используй:

```text
springfox
springdoc 1.x
springdoc 2.x
```

Они относятся к предыдущим поколениям Spring Boot.

После добавления dependency сначала выполни:

```text
.\mvnw.cmd clean test
```

`clean` важен, потому что в проекте уже были перенесены Java packages и старые
class-файлы могут остаться в `target`.

---

## 3. Базовая конфигурация

Создай:

```text
collabdesk.openapi.OpenApiConfig
```

Конфигурация должна предоставить bean `OpenAPI`:

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI collabDeskOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CollabDesk API")
                        .description(
                                "Session-based REST API for collaborative workspaces"
                        )
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(
                                "sessionCookie",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                                        .name("JSESSIONID")
                        )
                        .addParameters(
                                "csrfToken",
                                new HeaderParameter()
                                        .name("X-CSRF-TOKEN")
                                        .required(true)
                                        .description(
                                                "Token returned by GET /csrf"
                                        )
                        ));
    }
}
```

Imports:

```java
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityScheme;
```

### Зачем session scheme и CSRF parameter

CollabDesk использует два независимых механизма:

```text
JSESSIONID
    -> доказывает, кто выполняет запрос

X-CSRF-TOKEN
    -> доказывает, что mutation-запрос отправлен вашим frontend
```

CSRF token не заменяет session, а session не заменяет CSRF.

---

## 4. Настройки springdoc

Добавь в `application.properties`:

```properties
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.tags-sorter=alpha
springdoc.swagger-ui.operations-sorter=method
springdoc.paths-to-match=/api/**,/csrf
springdoc.packages-to-scan=collabdesk.controller
```

`paths-to-match` не позволяет случайно добавить в публичный контракт
внутренние Spring endpoints.

`packages-to-scan` фиксирует controllers, принадлежащие CollabDesk.

Не включай:

```properties
springdoc.show-actuator=true
```

Actuator пока отсутствует и не входит в публичный REST contract.

---

## 5. SecurityConfig

Swagger и specification содержат структуру backend API. На этом этапе они
должны быть доступны только аутентифицированному пользователю.

Добавь явные matchers до `anyRequest()`:

```java
.requestMatchers(
        HttpMethod.GET,
        "/v3/api-docs",
        "/v3/api-docs.yaml",
        "/v3/api-docs/**",
        "/swagger-ui.html",
        "/swagger-ui/**"
).authenticated()
```

Технически `anyRequest().authenticated()` уже защищает эти URL. Явные matchers
нужны, чтобы политика документации была видна прямо в `SecurityConfig`.

Не делай:

```java
permitAll()
```

без отдельного решения о публикации API documentation.

### Как открыть Swagger UI

```text
1. Войти через React frontend.
2. Не очищать cookie.
3. Открыть http://localhost:8080/swagger-ui.html.
```

Cookie привязана к host, а не к port, поэтому session для `localhost` будет
доступна и backend Swagger UI.

---

## 6. Особенность Try it out и CSRF

GET-запросы из Swagger UI должны работать после входа.

Для POST/PATCH/DELETE нужен CSRF token:

```text
1. Выполнить GET /csrf.
2. Скопировать значение `token`.
3. Открыть нужный POST/PATCH/DELETE endpoint.
4. Вставить token в поле `X-CSRF-TOKEN`.
5. Выполнить mutation request.
```

Spring возвращает реальное имя заголовка в поле:

```json
{
  "headerName": "X-CSRF-TOKEN",
  "parameterName": "_csrf",
  "token": "..."
}
```

Не отключай CSRF ради Swagger.

Не добавляй token в OpenAPI specification как постоянное значение: он связан
с session и изменяется.

---

## 7. Tags

Добавь `@Tag` на каждый controller:

```java
@Tag(
        name = "Workspace members",
        description = "Workspace membership and role management"
)
```

Рекомендуемое соответствие:

| Controller | Tag |
|---|---|
| `AuthController` | `Authentication` |
| `CsrfController` | `Infrastructure` |
| `WorkspaceController` | `Workspaces` |
| `WorkspaceMemberController` | `Workspace members` |
| `ProjectController` | `Projects` |
| `TaskController` | `Tasks` |

Imports:

```java
import io.swagger.v3.oas.annotations.tags.Tag;
```

Tag отвечает на вопрос:

```text
к какой возможности относится endpoint?
```

Java package отвечает на другой вопрос:

```text
где находится реализация?
```

---

## 8. Operations

На методы controller добавь краткий `@Operation`.

Пример:

```java
@Operation(
        summary = "List workspace members",
        description = "Available to every member of the workspace"
)
@GetMapping
public List<WorkspaceMemberResponse> findAll(...) {
    ...
}
```

Для создания:

```java
@Operation(
        summary = "Add a workspace member",
        description = """
                Adds an existing active CollabDesk user by email.
                Requires the OWNER workspace role.
                """
)
```

Summary должен описывать действие, а не повторять имя Java method.

Хорошо:

```text
Create a task
Change task status
List workspace members
```

Плохо:

```text
create
findAll
changeStatus method
```

---

## 9. SecurityRequirements

На защищённые controllers добавь:

```java
@SecurityRequirement(name = "sessionCookie")
```

Для mutation methods дополнительно:

```java
import io.swagger.v3.oas.annotations.Parameter;

@Parameter(ref = "#/components/parameters/csrfToken")
```

Пример:

```java
@PostMapping
@Parameter(ref = "#/components/parameters/csrfToken")
public TaskResponse create(...) {
    ...
}
```

Class-level `sessionCookie` и method-level CSRF header здесь документируют
реальные требования API. CSRF оформлен как обязательный header parameter, а
не как второй способ authentication.

Annotations не выполняют authorization. Проверки всё ещё выполняются Spring
Security и access services.

---

## 10. Response codes

Документируй только meaningful responses.

Пример для добавления member:

```java
@ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Workspace member added"
        ),
        @ApiResponse(
                responseCode = "400",
                description = "Request validation failed"
        ),
        @ApiResponse(
                responseCode = "401",
                description = "Authentication required"
        ),
        @ApiResponse(
                responseCode = "403",
                description = "OWNER role required"
        ),
        @ApiResponse(
                responseCode = "404",
                description = "Workspace or user not found"
        ),
        @ApiResponse(
                responseCode = "409",
                description = "User is already a member or OWNER mutation"
        )
})
```

Imports:

```java
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
```

Не копируй одинаковый огромный список на каждый GET. Документация должна
помогать, а не превращать controller в нечитаемый файл.

---

## 11. DTO schemas

Bean Validation уже позволяет springdoc увидеть:

- required fields;
- допустимые enum values;
- max length;
- email format.

Добавляй `@Schema` только для смысла и примеров:

```java
public record AddWorkspaceMemberRequest(
        @Schema(
                description = "Email of an existing CollabDesk account",
                example = "member@example.com"
        )
        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        @Schema(
                description = "Workspace role assigned to the user",
                example = "MEMBER",
                allowableValues = {"ADMIN", "MEMBER", "VIEWER"}
        )
        @NotNull
        WorkspaceRole role
) {
}
```

Не добавляй `OWNER` в `allowableValues` для member management, даже несмотря
на наличие значения в Java enum.

Добавь полезные examples как минимум для:

- registration;
- workspace creation;
- project creation;
- task creation;
- task status;
- member addition;
- member role update.

Не аннотируй entity. Публичный контракт строится из request/response DTO.

---

## 12. ProblemDetail

Существующие ошибки возвращаются как `ProblemDetail`.

Создай documentation-only schema:

```text
collabdesk.openapi.ApiProblemResponse
```

Вариант record:

```java
@Schema(description = "RFC 9457 API error")
public record ApiProblemResponse(
        String type,
        String title,
        Integer status,
        String detail,
        String instance,
        Map<String, String> errors
) {
}
```

Этот record не нужно возвращать из production controller. Он используется в:

```java
@Content(
        schema = @Schema(implementation = ApiProblemResponse.class)
)
```

для документирования error body.

---

## 13. Tests

Создай:

```text
src/test/java/collabdesk/openapi/OpenApiIntegrationTest.java
```

Используй:

```text
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
```

### Проверка authentication

```text
anonymous GET /v3/api-docs
    -> 401

authenticated GET /v3/api-docs
    -> 200
```

Для проверки authentication можно использовать настоящий login flow либо
security test principal. Для этого теста настоящий login предпочтительнее:
он доказывает совместимость со всей session-конфигурацией.

### Проверка specification

Проверь наличие paths:

```text
/api/v1/auth/register
/api/v1/auth/login
/api/v1/workspaces
/api/v1/workspaces/{workspaceId}/members
/api/v1/workspaces/{workspaceId}/members/{memberId}/role
/api/v1/workspaces/{workspaceId}/projects
/api/v1/workspaces/{workspaceId}/projects/{projectId}/tasks
/api/v1/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}/status
/csrf
```

Проверь:

```text
info.title == CollabDesk API
info.version == v1
components.securitySchemes.sessionCookie существует
components.parameters.csrfToken существует
```

Проверь schemas:

```text
AddWorkspaceMemberRequest
WorkspaceMemberResponse
CreateTaskRequest
TaskResponse
```

### Swagger UI

Authenticated request:

```text
GET /swagger-ui.html
    -> redirect или 200
```

Если endpoint отвечает redirect, дополнительно проверь location на
`/swagger-ui/index.html`.

---

## 14. Ручная проверка

Запусти backend и frontend.

```text
1. Зарегистрироваться или войти через React.
2. Открыть http://localhost:8080/swagger-ui.html.
3. Проверить шесть tags.
4. Открыть Workspace members.
5. Выполнить GET списка участников.
6. Выполнить GET /csrf.
7. Вставить token в поле `X-CSRF-TOKEN` mutation endpoint.
8. Выполнить безопасный mutation на тестовом workspace.
9. Убедиться, что response schema и status совпали с документацией.
```

Не выполняй destructive DELETE на важных локальных данных.

---

## 15. Что не делать

- Не отключать CSRF.
- Не переводить authentication на JWT только ради Swagger.
- Не делать OpenAPI endpoints публичными без необходимости.
- Не добавлять Swagger annotations в entity.
- Не описывать внутренние repository/service methods как API.
- Не документировать несуществующие response codes.
- Не добавлять OWNER в member request examples.
- Не хранить реальный CSRF token в configuration.
- Не добавлять кастомные roles в этот инфраструктурный этап.
- Не генерировать новый frontend API client посреди текущего React-кода.

---

## 16. Критерии завершения

- [ ] Используется springdoc 3.x для Spring Boot 4.
- [ ] `/v3/api-docs` отдаёт OpenAPI JSON после authentication.
- [ ] `/v3/api-docs.yaml` доступен после authentication.
- [ ] `/swagger-ui.html` открывает Swagger UI.
- [ ] Anonymous не получает API specification.
- [ ] Все текущие endpoints присутствуют.
- [ ] Controllers сгруппированы через tags.
- [ ] Основные operations имеют summary.
- [ ] Session cookie описана.
- [ ] CSRF header описан.
- [ ] Request DTO имеют полезные examples.
- [ ] Error body описан через documentation schema.
- [ ] OpenAPI integration tests проходят.
- [ ] Полный backend suite проходит.
- [ ] React lint/build продолжают проходить.

---

## 17. Как OpenAPI поможет не путаться

Теперь искать функцию можно сверху вниз:

```text
Swagger tag
    -> endpoint
    -> Controller method
    -> Service method
    -> AccessService
    -> Repository
```

Пример:

```text
Swagger: Tasks / Change task status
    -> PATCH .../tasks/{taskId}/status
    -> TaskController.changeStatus
    -> TaskService.changeStatus
    -> ProjectAccessService.requireWritableProject
    -> TaskRepository.findByIdAndProject_Id
    -> Task.changeStatus
```

OpenAPI структурирует внешний контракт. IntelliJ `Call Hierarchy` и
`Find Usages` структурируют внутреннюю реализацию.

---

## 18. Будущая модель project roles и закрытых задач

Не объединяй в одно понятие:

```text
роль
назначение на задачу
видимость задачи
участие в проекте
```

Это четыре разные вещи.

### Базовая workspace role

Существующие:

```text
OWNER / ADMIN / MEMBER / VIEWER
```

отвечают за базовый уровень доступа к workspace.

### Project membership

Для закрытых проектов понадобится:

```text
project_members
    project_id
    workspace_member_id
```

и режим проекта:

```text
WORKSPACE
RESTRICTED
```

В `RESTRICTED` project входят только явно добавленные workspace members.

### Task assignees

Назначение исполнителя:

```text
task_assignees
    task_id
    workspace_member_id
```

Assignee отвечает на вопрос:

```text
кто выполняет задачу?
```

Он не должен автоматически отвечать на вопрос:

```text
кто может видеть задачу?
```

### Task visibility

Если нужны закрытые задачи, добавь отдельный режим:

```text
PROJECT
ASSIGNEES
```

`PROJECT` видят участники проекта.

`ASSIGNEES` видят:

- assignees;
- creator;
- пользователи с административным permission;
- OWNER.

### Custom roles

Не добавляй новые значения в Java enum для каждой создаваемой роли.

Пользовательские роли должны храниться в database:

```text
custom_roles
    id
    workspace_id
    project_id nullable
    name
    scope: WORKSPACE / PROJECT

role_permissions
    role_id
    permission

member_role_assignments
    workspace_member_id
    role_id
```

Permissions остаются контролируемым backend enum:

```text
WORKSPACE_MANAGE_MEMBERS
PROJECT_CREATE
PROJECT_MANAGE_MEMBERS
PROJECT_READ
TASK_CREATE
TASK_UPDATE
TASK_ASSIGN
TASK_READ_RESTRICTED
```

Пользователь создаёт комбинации permissions и даёт им название:

```text
QA
Project manager
Developer
Client observer
```

но не создаёт произвольный код permission.

Для первой версии custom RBAC используй только additive permissions:

```text
роль может разрешить действие
роль не содержит явных DENY
```

`ALLOW + DENY`, inheritance и приоритеты ролей резко усложняют вычисление
доступа и тестирование.

### Рекомендуемый порядок будущих этапов

```text
11. OpenAPI
12. Project membership + task assignees
13. Restricted projects/tasks
14. Custom workspace/project roles and permissions
```

Так custom roles будут применяться к уже существующим объектам доступа, а не
к абстрактной модели без реальных project members и assignees.

---

## 19. Официальные источники

- Springdoc Getting Started:
  `https://springdoc.org/v4/index.html`
- Springdoc modules:
  `https://springdoc.org/modules.html`
- Springdoc releases:
  `https://github.com/springdoc/springdoc-openapi/releases`
