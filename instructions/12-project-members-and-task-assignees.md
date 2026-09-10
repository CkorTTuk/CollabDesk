# Этап 12: команда проекта и исполнители задач

## Результат этапа

После завершения у каждого проекта появится собственная команда, состоящая
только из участников его workspace.

На задачи можно будет назначать одного или нескольких участников проекта:

```text
Workspace member
    -> добавлен в project team
    -> может быть назначен на task
```

Frontend сможет:

- показать команду проекта;
- добавить workspace member в project team;
- удалить участника из project team;
- показать исполнителей на карточке задачи;
- заменить список исполнителей задачи.

Назначение на задачу пока не меняет права доступа. Оно отвечает только на
вопрос:

```text
кто отвечает за выполнение задачи?
```

---

## 1. Границы этапа

Реализуем:

- таблицу `project_members`;
- таблицу `task_assignees`;
- автоматическое добавление автора проекта в project team;
- перенос существующих авторов проектов в `project_members`;
- просмотр команды проекта;
- управление командой проекта для `OWNER` и `ADMIN`;
- назначение нескольких исполнителей на задачу;
- отображение исполнителей в `TaskResponse`;
- OpenAPI-описание новых endpoints;
- repository, service и integration tests;
- минимальное подключение к существующей React-доске.

Пока не реализуем:

- закрытые проекты;
- скрытые задачи;
- выдачу доступа через назначение на задачу;
- project-specific роли;
- custom roles и permissions;
- комментарии;
- уведомления;
- сроки выполнения `dueDate`;
- drag-and-drop задач.

В этом этапе все участники workspace по-прежнему видят все его проекты.

---

## 2. Почему project member и task assignee — разные сущности

`WorkspaceMember` означает:

```text
пользователь состоит в workspace
```

`ProjectMember` означает:

```text
пользователь включён в команду конкретного проекта
```

`TaskAssignee` означает:

```text
участник project team назначен на конкретную задачу
```

Не добавляй напрямую:

```java
@ManyToMany
Set<User> assignees;
```

Отдельные entity нужны для:

- ограничений целостности;
- времени добавления;
- будущих project roles;
- будущей истории назначений;
- понятных repository queries.

---

## 3. Миграция V5

Создай:

```text
src/main/resources/db/migration/
V5__create_project_members_and_task_assignees.sql
```

### project_members

```sql
CREATE TABLE project_members (
    id BIGINT AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    workspace_member_id BIGINT NOT NULL,
    joined_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT project_members_project_fk
        FOREIGN KEY (project_id)
            REFERENCES projects(id)
            ON DELETE CASCADE,

    CONSTRAINT project_members_workspace_member_fk
        FOREIGN KEY (workspace_member_id)
            REFERENCES workspace_members(id)
            ON DELETE CASCADE,

    CONSTRAINT project_members_project_workspace_member_uk
        UNIQUE (project_id, workspace_member_id),

    INDEX project_members_project_joined_at_idx (
        project_id,
        joined_at
    )
);
```

После создания таблицы перенеси авторов существующих проектов:

```sql
INSERT INTO project_members (
    project_id,
    workspace_member_id,
    joined_at
)
SELECT
    p.id,
    wm.id,
    p.created_at
FROM projects p
JOIN workspace_members wm
    ON wm.workspace_id = p.workspace_id
    AND wm.user_id = p.created_by;
```

Если автор старого проекта уже был удалён из workspace, первый `INSERT` не
найдёт membership. Добавь fallback: включи в team владельца workspace для
каждого проекта, который всё ещё остался без project member.

```sql
INSERT INTO project_members (
    project_id,
    workspace_member_id,
    joined_at
)
SELECT
    p.id,
    MIN(wm.id),
    p.created_at
FROM projects p
JOIN workspace_members wm
    ON wm.workspace_id = p.workspace_id
    AND wm.role = 'OWNER'
LEFT JOIN project_members pm
    ON pm.project_id = p.id
WHERE pm.id IS NULL
GROUP BY p.id, p.created_at;
```

Backfill важен: после применения V5 старые проекты не должны остаться без
project team.

### task_assignees

```sql
CREATE TABLE task_assignees (
    id BIGINT AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    project_member_id BIGINT NOT NULL,
    assigned_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT task_assignees_task_fk
        FOREIGN KEY (task_id)
            REFERENCES tasks(id)
            ON DELETE CASCADE,

    CONSTRAINT task_assignees_project_member_fk
        FOREIGN KEY (project_member_id)
            REFERENCES project_members(id)
            ON DELETE CASCADE,

    CONSTRAINT task_assignees_task_project_member_uk
        UNIQUE (task_id, project_member_id),

    INDEX task_assignees_task_idx (task_id),
    INDEX task_assignees_project_member_idx (project_member_id)
);
```

`ON DELETE CASCADE` означает:

- удаление task удаляет её назначения;
- удаление project удаляет team и назначения;
- удаление workspace member удаляет его project memberships и назначения.

Не редактируй V1–V4: они уже считаются применённой историей.

---

## 4. Entity ProjectMember

Создай:

```text
collabdesk.projectmember.entity.ProjectMember
```

Поля:

```java
Long id;
Project project;
WorkspaceMember workspaceMember;
Instant joinedAt;
```

Основные annotations:

```java
@Entity
@Table(
        name = "project_members",
        uniqueConstraints = @UniqueConstraint(
                name = "project_members_project_workspace_member_uk",
                columnNames = {"project_id", "workspace_member_id"}
        )
)
```

Связи должны быть `LAZY`:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "project_id", nullable = false)

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "workspace_member_id", nullable = false)
```

Конструктор обязан проверить, что workspace совпадает:

```java
if (!project.getWorkspace().getId()
        .equals(workspaceMember.getWorkspace().getId())) {
    throw new IllegalArgumentException(
            "Project member must belong to the same workspace"
    );
}
```

Не храни второй `workspace_id` в `project_members`. Он уже определяется через
обе связанные сущности.

---

## 5. Entity TaskAssignee

Создай:

```text
collabdesk.taskassignee.entity.TaskAssignee
```

Поля:

```java
Long id;
Task task;
ProjectMember projectMember;
Instant assignedAt;
```

Конструктор проверяет принадлежность одному проекту:

```java
if (!task.getProject().getId()
        .equals(projectMember.getProject().getId())) {
    throw new IllegalArgumentException(
            "Assignee must belong to the task project"
    );
}
```

Назначение не должно менять `Task.status`.

Не добавляй коллекцию с `CascadeType.ALL` в `Task`: управление назначениями
будет выполняться явно через service и repository.

---

## 6. Repositories

Создай:

```text
ProjectMemberRepository
TaskAssigneeRepository
```

Минимальные методы `ProjectMemberRepository`:

```java
List<ProjectMember> findByProject_IdOrderByJoinedAtAsc(Long projectId);

Optional<ProjectMember> findByIdAndProject_Id(
        Long projectMemberId,
        Long projectId
);

Optional<ProjectMember> findByProject_IdAndWorkspaceMember_Id(
        Long projectId,
        Long workspaceMemberId
);

List<ProjectMember> findAllByProject_IdAndIdIn(
        Long projectId,
        Collection<Long> ids
);

boolean existsByProject_IdAndWorkspaceMember_Id(
        Long projectId,
        Long workspaceMemberId
);
```

Минимальные методы `TaskAssigneeRepository`:

```java
List<TaskAssignee> findByTask_IdOrderByAssignedAtAsc(Long taskId);

List<TaskAssignee> findByTask_Project_IdOrderByAssignedAtAsc(
        Long projectId
);

void deleteByTask_Id(Long taskId);
```

Для чтения добавь `@EntityGraph`, чтобы response mapping не создавал цепочку
N+1 запросов:

```java
@EntityGraph(attributePaths = {
        "projectMember",
        "projectMember.workspaceMember",
        "projectMember.workspaceMember.user"
})
```

---

## 7. Политика доступа

Добавь в `WorkspaceAccessService`:

```java
@Transactional(readOnly = true)
public WorkspaceMember requireManager(
        Long workspaceId,
        Long currentUserId
) {
    WorkspaceMember membership = requireMember(
            workspaceId,
            currentUserId
    );

    if (membership.getRole() != WorkspaceRole.OWNER
            && membership.getRole() != WorkspaceRole.ADMIN) {
        throw new WorkspaceOperationForbiddenException(
                "Owner or admin role is required"
        );
    }

    return membership;
}
```

Правила этапа:

| Операция | Кто может |
|---|---|
| Посмотреть project team | любой workspace member |
| Добавить project member | `OWNER`, `ADMIN` |
| Удалить project member | `OWNER`, `ADMIN` |
| Посмотреть assignees | любой, кто видит task |
| Заменить assignees | `OWNER`, `ADMIN`, `MEMBER` |
| VIEWER изменить assignees | нет |

Назначение пользователя assignee не повышает его права. Если `VIEWER`
назначен на задачу, он всё равно не может менять её status.

---

## 8. DTO команды проекта

Создай:

```text
collabdesk.projectmember.dto.AddProjectMemberRequest
collabdesk.projectmember.dto.ProjectMemberResponse
```

Request:

```java
public record AddProjectMemberRequest(
        @NotNull
        @Schema(
                description = "Workspace member added to the project",
                example = "12"
        )
        Long workspaceMemberId
) {
}
```

Response:

```java
public record ProjectMemberResponse(
        Long id,
        Long workspaceMemberId,
        Long userId,
        String displayName,
        String email,
        Instant joinedAt
) {
}
```

Клиенту нужны имя и email, поэтому не заставляй frontend дополнительно искать
пользователя по `userId`.

---

## 9. ProjectMemberService

Создай:

```text
collabdesk.projectmember.service.ProjectMemberService
```

Методы:

```java
List<ProjectMemberResponse> findForProject(
        Long workspaceId,
        Long projectId,
        Long currentUserId
);

ProjectMemberResponse add(
        Long workspaceId,
        Long projectId,
        Long currentUserId,
        Long workspaceMemberId
);

void remove(
        Long workspaceId,
        Long projectId,
        Long projectMemberId,
        Long currentUserId
);
```

### findForProject

Порядок:

```text
requireAccessibleProject
    -> repository.findByProject_IdOrderByJoinedAtAsc
    -> map to response
```

### add

Порядок:

```text
requireManager(workspaceId, currentUserId)
    -> найти project строго по workspaceId + projectId
    -> найти workspace member строго по workspaceId + workspaceMemberId
    -> проверить duplicate
    -> создать ProjectMember
    -> save
```

Нельзя принимать `userId` вместо `workspaceMemberId`: добавляется именно
существующее membership внутри текущего workspace.

### remove

Порядок:

```text
requireManager
    -> requireAccessibleProject
    -> findByIdAndProject_Id
    -> delete
```

Удаление project member автоматически снимает его со всех задач этого проекта
через FK cascade.

Создай исключения:

```text
ProjectMemberNotFoundException       -> 404
ProjectMemberAlreadyExistsException -> 409
```

---

## 10. Автор проекта автоматически входит в team

Измени `ProjectService.create`.

После сохранения `Project` создай `ProjectMember` из текущего
`WorkspaceMember`:

```java
Project savedProject = projectRepository.save(project);

projectMemberRepository.save(
        new ProjectMember(savedProject, workspaceMember)
);
```

Обе записи должны создаваться в одной `@Transactional` операции.

Если сохранение project member упало, создание проекта должно откатиться.

Не добавляй автора повторно на уровне controller.

---

## 11. DTO исполнителей

Создай:

```text
collabdesk.taskassignee.dto.ReplaceTaskAssigneesRequest
collabdesk.taskassignee.dto.TaskAssigneeResponse
```

Request:

```java
public record ReplaceTaskAssigneesRequest(
        @NotNull
        @Size(max = 20)
        Set<@NotNull Long> projectMemberIds
) {
}
```

Пустой set разрешён и означает:

```text
снять с задачи всех исполнителей
```

`Set` не позволяет одному человеку дважды появиться в request.

Response:

```java
public record TaskAssigneeResponse(
        Long projectMemberId,
        Long workspaceMemberId,
        Long userId,
        String displayName,
        String email
) {
}
```

Добавь в `TaskResponse`:

```java
List<TaskAssigneeResponse> assignees
```

Список никогда не должен быть `null`. Для задачи без исполнителей возвращай:

```json
"assignees": []
```

---

## 12. TaskAssigneeService

Создай:

```text
collabdesk.taskassignee.service.TaskAssigneeService
```

Основной метод:

```java
TaskResponse replace(
        Long workspaceId,
        Long projectId,
        Long taskId,
        Long currentUserId,
        Set<Long> projectMemberIds
);
```

Алгоритм:

```text
1. requireWritableProject
2. найти task по taskId + projectId
3. одним запросом загрузить project members по projectId + ids
4. проверить, что найдено столько же уникальных members, сколько запрошено
5. удалить старые назначения task
6. сохранить новые TaskAssignee
7. вернуть обновлённый TaskResponse
```

Если передан member другого проекта, возвращай `404`, а не сообщай, в каком
проекте он существует.

Операция должна быть `@Transactional`: нельзя оставить task с наполовину
обновлённым списком.

Для неизвестного project member создай:

```text
ProjectMemberNotFoundException -> 404
```

---

## 13. Mapping TaskResponse без N+1

После добавления `assignees` старый `TaskService.toResponse(Task)` уже
недостаточен.

Создай отдельный:

```text
collabdesk.task.service.TaskResponseMapper
```

Он должен уметь:

```java
TaskResponse toResponse(
        Task task,
        List<TaskAssignee> assignees
);

List<TaskResponse> toResponses(
        List<Task> tasks,
        List<TaskAssignee> assignees
);
```

Для списка задач:

```text
1 запрос -> tasks проекта
1 запрос -> assignees проекта с нужными relations
groupingBy(taskAssignee.getTask().getId())
mapping -> responses
```

Не выполняй отдельный assignee query для каждой карточки.

`create` возвращает пустой список assignees.

`changeStatus` возвращает существующих assignees, а не очищает их.

---

## 14. REST endpoints команды проекта

Создай:

```text
ProjectMemberController
```

Base path:

```text
/api/v1/workspaces/{workspaceId}/projects/{projectId}/members
```

Endpoints:

```text
GET    /members
POST   /members
DELETE /members/{projectMemberId}
```

Status codes:

```text
GET    -> 200
POST   -> 201
DELETE -> 204
```

Не используй path `/users`: API управляет project membership, а не
пользовательскими аккаунтами.

---

## 15. REST endpoint исполнителей

Добавь в `TaskController`:

```text
PUT /api/v1/workspaces/{workspaceId}/projects/{projectId}
    /tasks/{taskId}/assignees
```

Пример body:

```json
{
  "projectMemberIds": [5, 8]
}
```

Пример response:

```json
{
  "id": 31,
  "projectId": 7,
  "title": "Document workspace API",
  "description": null,
  "status": "IN_PROGRESS",
  "createdById": 2,
  "createdAt": "2026-07-28T12:30:00Z",
  "updatedAt": "2026-07-28T13:00:00Z",
  "assignees": [
    {
      "projectMemberId": 5,
      "workspaceMemberId": 9,
      "userId": 2,
      "displayName": "Alex Morgan",
      "email": "alex@example.com"
    }
  ]
}
```

Используется `PUT`, потому что клиент передаёт полное желаемое состояние
списка, а не добавляет один элемент.

---

## 16. GlobalExceptionHandler

Добавь handlers:

```text
ProjectMemberNotFoundException
    -> 404 Project member not found

ProjectMemberAlreadyExistsException
    -> 409 Project member already exists
```

Не возвращай stack trace, entity или SQL constraint message.

Race condition всё равно может нарушить unique constraint между проверкой и
`save`. Преобразуй соответствующий `DataIntegrityViolationException` в
понятный `409` на service boundary.

---

## 17. OpenAPI

Добавь tag:

```text
Project members
```

Для новых endpoints опиши:

- summary;
- session cookie;
- CSRF header для POST, PUT и DELETE;
- `201`, `204`, `400`, `401`, `403`, `404`, `409`;
- request/response schemas;
- ограничение максимум 20 assignees.

Обнови `OpenApiIntegrationTest`.

Новые paths:

```text
/api/v1/workspaces/{workspaceId}/projects/{projectId}/members
/api/v1/workspaces/{workspaceId}/projects/{projectId}/members/{projectMemberId}
/api/v1/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}/assignees
```

Новые schemas:

```text
AddProjectMemberRequest
ProjectMemberResponse
ReplaceTaskAssigneesRequest
TaskAssigneeResponse
```

---

## 18. Tests

### Entity tests

`ProjectMemberTest`:

- создаётся для member того же workspace;
- отклоняет member другого workspace;
- устанавливает `joinedAt`.

`TaskAssigneeTest`:

- создаётся для member того же project;
- отклоняет member другого project;
- устанавливает `assignedAt`.

### Repository tests

Проверь:

- unique project + workspace member;
- project team сортируется по `joinedAt`;
- поиск project member ограничен `projectId`;
- unique task + project member;
- удаление task удаляет assignees;
- удаление project member удаляет его assignees.

### Service tests

Проверь:

- creator автоматически добавлен в team;
- `OWNER` и `ADMIN` добавляют project member;
- `MEMBER` и `VIEWER` не управляют project team;
- нельзя добавить member другого workspace;
- duplicate даёт conflict;
- contributor заменяет assignees;
- `VIEWER` не меняет assignees;
- пустой set снимает все назначения;
- member другого project отклоняется;
- ошибка во время replace откатывает старый список;
- `changeStatus` сохраняет assignees;
- task list не возвращает `null`.

### Integration tests

Проверь полный HTTP flow:

```text
1. OWNER создаёт workspace и project.
2. Creator автоматически есть в GET project members.
3. OWNER добавляет workspace member в project.
4. MEMBER создаёт task.
5. MEMBER назначает двух project members.
6. GET tasks возвращает обоих assignees.
7. VIEWER получает 403 на PUT assignees.
8. OWNER удаляет project member.
9. GET tasks больше не содержит удалённого assignee.
```

Проверяй CSRF на всех mutation endpoints.

---

## 19. Минимальное подключение frontend

После готовности backend добавь в React:

```text
Project team
    -> список project members
    -> Add member из существующих workspace members
    -> Remove

Task card
    -> аватары/инициалы assignees
    -> Assign people
    -> dropdown с project members
    -> Save
```

Не проси пользователя вводить `projectMemberId` вручную.

В API client добавь:

```text
getProjectMembers
addProjectMember
removeProjectMember
replaceTaskAssignees
```

При выборе исполнителей держи временный `Set` на frontend и отправляй полный
список только по кнопке `Save`.

Если assignees больше трёх, карточка может показать:

```text
AM  JS  RK  +2
```

После успешного `PUT` замени task в существующем React state ответом backend,
не перезагружая всю доску.

---

## 20. Ручная проверка

```text
1. Создать workspace.
2. Добавить ещё двух workspace members.
3. Создать project.
4. Убедиться, что creator уже в Project team.
5. Добавить второго участника в project.
6. Создать task.
7. Назначить обоих участников.
8. Обновить страницу.
9. Убедиться, что assignees восстановились из backend.
10. Снять одного assignee.
11. Проверить TaskResponse через Swagger UI.
```

Дополнительно проверь разные аккаунты и роли.

---

## 21. Что не делать

- Не связывать task напрямую с `User`.
- Не разрешать назначать любого workspace member без project membership.
- Не превращать assignee в permission.
- Не скрывать task от неназначенных пользователей на этом этапе.
- Не добавлять `PROJECT_ADMIN` enum.
- Не добавлять custom role в `WorkspaceRole`.
- Не возвращать JPA entity из controller.
- Не загружать assignees отдельным query для каждой task.
- Не редактировать старые Flyway migrations.
- Не отключать CSRF.

---

## 22. Критерии завершения

- [ ] V5 создаёт обе таблицы и backfill существующих проектов.
- [ ] Автор нового проекта автоматически становится project member.
- [ ] Project member всегда принадлежит workspace проекта.
- [ ] Task assignee всегда принадлежит project team задачи.
- [ ] `OWNER` и `ADMIN` управляют project team.
- [ ] `MEMBER` и `VIEWER` не управляют project team.
- [ ] Contributor может заменить assignees.
- [ ] `VIEWER` остаётся read-only.
- [ ] Task без assignees возвращает `[]`.
- [ ] Task list загружает assignees без N+1.
- [ ] Новые endpoints описаны в OpenAPI.
- [ ] Entity, repository, service и integration tests проходят.
- [ ] Полный Maven test suite проходит.
- [ ] Frontend показывает и изменяет assignees.
- [ ] React lint и production build проходят.

---

## 23. Что будет следующим

После этого этапа появятся реальные объекты, на которых можно строить доступ:

```text
Этап 13:
    restricted projects
    project visibility
    task visibility

Этап 14:
    custom workspace/project roles
    permission assignments
```

Сначала создаём membership и assignment, затем используем их в правилах
видимости. Так access model остаётся проверяемой и не превращается в один
огромный условный блок.
