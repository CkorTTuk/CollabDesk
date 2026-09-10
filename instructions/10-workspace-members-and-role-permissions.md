# Этап 10: участники Workspace и role-based permissions

## Результат этапа

После завершения владелец workspace сможет:

```text
открыть workspace
    -> увидеть список участников
    -> добавить зарегистрированного пользователя по email
    -> назначить ADMIN / MEMBER / VIEWER
    -> изменить роль
    -> удалить участника
```

Остальные пользователи увидят workspace согласно своей роли:

```text
OWNER / ADMIN / MEMBER
    -> чтение
    -> создание projects и tasks
    -> изменение task status

VIEWER
    -> только чтение
```

Это первый этап с настоящей role-based authorization. Authentication и
membership больше не считаются достаточными для любой операции.

---

## 1. Границы этапа

Реализуем:

```text
GET    /api/v1/workspaces/{workspaceId}/members
POST   /api/v1/workspaces/{workspaceId}/members
PATCH  /api/v1/workspaces/{workspaceId}/members/{memberId}/role
DELETE /api/v1/workspaces/{workspaceId}/members/{memberId}
```

Также применяем роль `VIEWER` к существующим Project/Task endpoints.

Пока не реализуем:

- отправку email;
- приглашение незарегистрированного пользователя;
- invite token;
- принятие или отклонение приглашения;
- передачу ownership;
- несколько OWNER;
- самостоятельный выход участника;
- блокировку участника;
- audit log;
- assignee;
- comments;
- Redis.

На этом этапе POST добавляет только пользователя, у которого уже существует
аккаунт CollabDesk. Это управление участниками, а не полноценная система
email-приглашений.

---

## 2. Матрица разрешений

| Операция | OWNER | ADMIN | MEMBER | VIEWER |
|---|---:|---:|---:|---:|
| Получить workspace | да | да | да | да |
| Получить members | да | да | да | да |
| Получить projects/tasks | да | да | да | да |
| Создать project | да | да | да | нет |
| Создать task | да | да | да | нет |
| Изменить task status | да | да | да | нет |
| Добавить member | да | нет | нет | нет |
| Изменить role | да | нет | нет | нет |
| Удалить member | да | нет | нет | нет |

Пока `ADMIN` и `MEMBER` имеют одинаковые права на рабочие ресурсы. Разница
понадобится, когда появятся настройки workspace, архивирование и более тонкие
permissions.

---

## 3. API-контракт

### Получить участников

```text
GET /api/v1/workspaces/{workspaceId}/members
Authentication: HTTP session
```

Success:

```text
200 OK
```

```json
[
  {
    "id": 12,
    "userId": 4,
    "email": "owner@example.com",
    "displayName": "Workspace Owner",
    "role": "OWNER",
    "joinedAt": "2026-07-26T12:00:00Z"
  },
  {
    "id": 13,
    "userId": 5,
    "email": "member@example.com",
    "displayName": "Team Member",
    "role": "MEMBER",
    "joinedAt": "2026-07-26T12:10:00Z"
  }
]
```

Любой member может получить список участников.

### Добавить участника

```text
POST /api/v1/workspaces/{workspaceId}/members
Authentication: HTTP session
CSRF: обязателен
Permission: OWNER
```

Request:

```json
{
  "email": "member@example.com",
  "role": "MEMBER"
}
```

Success:

```text
201 Created
```

Response — `WorkspaceMemberResponse`.

Разрешённые роли:

```text
ADMIN
MEMBER
VIEWER
```

Передавать `OWNER` запрещено.

### Изменить роль

```text
PATCH /api/v1/workspaces/{workspaceId}/members/{memberId}/role
Authentication: HTTP session
CSRF: обязателен
Permission: OWNER
```

Request:

```json
{
  "role": "VIEWER"
}
```

Success:

```text
200 OK
```

Response — обновлённый `WorkspaceMemberResponse`.

### Удалить участника

```text
DELETE /api/v1/workspaces/{workspaceId}/members/{memberId}
Authentication: HTTP session
CSRF: обязателен
Permission: OWNER
```

Success:

```text
204 No Content
```

OWNER нельзя удалить или понизить.

---

## 4. Бизнес-правила

- Добавляемый пользователь уже должен быть зарегистрирован.
- Email нормализуется через `trim().toLowerCase(Locale.ROOT)`.
- Disabled user не может быть добавлен.
- Один user не может состоять в workspace дважды.
- Только OWNER управляет участниками.
- OWNER membership нельзя удалить.
- OWNER membership нельзя изменить.
- Нельзя назначить роль OWNER через members API.
- Повторная установка той же роли допустима.
- `memberId` всегда проверяется вместе с `workspaceId`.
- После удаления пользователь сразу теряет доступ к workspace.
- VIEWER может читать, но не может изменять рабочие данные.

Не принимай `userId` от frontend. Поиск выполняется по email, а фактический
User загружается backend.

---

## 5. Миграция не нужна

V2 уже содержит:

```sql
CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER'))
```

Также уже существует:

```sql
UNIQUE (workspace_id, user_id)
```

Поэтому новую Flyway migration на этом этапе не создаём.

Не создавай пустую V5 «для порядка». Номер миграции используется только при
реальном изменении схемы.

---

## 6. Изменения `workspacemember`

Добавь фабрику для обычного участника:

```java
public static WorkspaceMember collaborator(
        Workspace workspace,
        User user,
        WorkspaceRole role
) {
    if (role == WorkspaceRole.OWNER) {
        throw new IllegalArgumentException(
                "OWNER cannot be assigned through member management"
        );
    }

    return new WorkspaceMember(workspace, user, role);
}
```

Добавь доменный метод:

```java
public void changeRole(WorkspaceRole newRole) {
    Objects.requireNonNull(newRole);

    if (role == WorkspaceRole.OWNER) {
        throw new IllegalStateException(
                "Owner role cannot be changed"
        );
    }
    if (newRole == WorkspaceRole.OWNER) {
        throw new IllegalArgumentException(
                "Owner role cannot be assigned"
        );
    }

    role = newRole;
}
```

Не добавляй публичный `setRole`.

Существующие factory methods `owner()` и `member()` можно оставить для
предыдущих тестов.

### Entity tests

Проверь:

- collaborator принимает ADMIN/MEMBER/VIEWER;
- collaborator отклоняет OWNER;
- changeRole меняет MEMBER на VIEWER;
- changeRole отклоняет null;
- owner нельзя понизить;
- обычному участнику нельзя назначить OWNER.

---

## 7. Repository

Добавь:

```java
List<WorkspaceMember> findByWorkspace_IdOrderByJoinedAtAsc(
        Long workspaceId
);

Optional<WorkspaceMember> findByIdAndWorkspace_Id(
        Long memberId,
        Long workspaceId
);
```

Второй метод защищает от подстановки:

```text
workspace A + memberId из workspace B
    -> Optional.empty()
    -> 404
```

Не используй обычный `findById(memberId)` в service.

### Repository tests

Проверь:

1. список содержит только members указанного workspace;
2. порядок соответствует `joinedAt`;
3. scoped lookup не находит member другого workspace;
4. database отклоняет duplicate `(workspace_id, user_id)`;
5. удаление membership не удаляет User;
6. удаление workspace всё ещё каскадно удаляет memberships.

---

## 8. DTO

### `AddWorkspaceMemberRequest`

```java
public record AddWorkspaceMemberRequest(
        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        @NotNull
        WorkspaceRole role
) {
}
```

### `UpdateWorkspaceMemberRoleRequest`

```java
public record UpdateWorkspaceMemberRoleRequest(
        @NotNull
        WorkspaceRole role
) {
}
```

### `WorkspaceMemberResponse`

```java
public record WorkspaceMemberResponse(
        Long id,
        Long userId,
        String email,
        String displayName,
        WorkspaceRole role,
        Instant joinedAt
) {
}
```

Не возвращай User или Workspace entity.

---

## 9. Исключения

Создай:

```text
WorkspaceOperationForbiddenException
WorkspaceMemberNotFoundException
WorkspaceMemberAlreadyExistsException
WorkspaceUserNotFoundException
```

HTTP mapping:

### Недостаточно role

```text
403 Forbidden
```

```json
{
  "title": "Workspace operation forbidden",
  "status": 403,
  "detail": "Your workspace role does not allow this operation"
}
```

### Member не найден в workspace

```text
404 Not Found
```

```json
{
  "title": "Workspace member not found",
  "status": 404,
  "detail": "Workspace member was not found"
}
```

### Аккаунт по email не найден

```text
404 Not Found
```

Не раскрывай дополнительные сведения о состоянии аккаунта.

### User уже member

```text
409 Conflict
```

### Попытка изменить OWNER

Используй:

```text
409 Conflict
```

Это конфликт с текущим состоянием workspace, а не проблема JSON validation.

Можно создать отдельный:

```text
WorkspaceOwnerMutationException
```

---

## 10. Расширение `WorkspaceAccessService`

Существующий:

```java
requireMember(workspaceId, currentUserId)
```

оставь для read operations.

Добавь:

```java
@Transactional(readOnly = true)
public WorkspaceMember requireOwner(
        Long workspaceId,
        Long currentUserId
) {
    WorkspaceMember membership =
            requireMember(workspaceId, currentUserId);

    if (membership.getRole() != WorkspaceRole.OWNER) {
        throw new WorkspaceOperationForbiddenException(
                "Owner role is required"
        );
    }

    return membership;
}
```

Добавь:

```java
@Transactional(readOnly = true)
public WorkspaceMember requireContributor(
        Long workspaceId,
        Long currentUserId
) {
    WorkspaceMember membership =
            requireMember(workspaceId, currentUserId);

    if (membership.getRole() == WorkspaceRole.VIEWER) {
        throw new WorkspaceOperationForbiddenException(
                "Viewer has read-only access"
        );
    }

    return membership;
}
```

Не копируй repository query в три метода. Базовый membership lookup должен
оставаться в одном месте.

### Access tests

Проверь:

- OWNER проходит `requireOwner`;
- ADMIN/MEMBER/VIEWER не проходят `requireOwner`;
- OWNER/ADMIN/MEMBER проходят `requireContributor`;
- VIEWER не проходит `requireContributor`;
- non-member по-прежнему получает нейтральную access exception.

---

## 11. `Service`

Dependencies:

```text
WorkspaceMemberRepository
WorkspaceAccessService
UserRepository
```

### List

```java
@Transactional(readOnly = true)
public List<WorkspaceMemberResponse> findForWorkspace(
        Long workspaceId,
        Long currentUserId
)
```

Алгоритм:

```text
1. requireMember
2. query members по workspaceId
3. mapping в immutable responses
```

### Add

```java
@Transactional
public WorkspaceMemberResponse add(
        Long workspaceId,
        Long currentUserId,
        String email,
        WorkspaceRole role
)
```

Алгоритм:

```text
1. requireOwner
2. проверить, что role != OWNER
3. нормализовать email
4. найти User
5. проверить UserStatus.ACTIVE
6. проверить отсутствие membership
7. создать WorkspaceMember.collaborator
8. сохранить
9. вернуть response
```

Workspace бери из owner membership:

```java
ownerMembership.getWorkspace()
```

Не делай дополнительный `workspaceRepository.findById`.

### Change role

```java
@Transactional
public WorkspaceMemberResponse changeRole(
        Long workspaceId,
        Long memberId,
        Long currentUserId,
        WorkspaceRole newRole
)
```

Алгоритм:

```text
1. requireOwner
2. scoped lookup memberId + workspaceId
3. member.changeRole(newRole)
4. вернуть response
```

Managed entity обновится через dirty checking.

### Remove

```java
@Transactional
public void remove(
        Long workspaceId,
        Long memberId,
        Long currentUserId
)
```

Алгоритм:

```text
1. requireOwner
2. scoped lookup member
3. если target OWNER -> conflict
4. repository.delete(target)
```

---

## 12. Применение VIEWER к Project и Task

### Project create

В `ProjectService.create()` замени:

```java
requireMember(...)
```

на:

```java
requireContributor(...)
```

`findForWorkspace()` продолжает использовать `requireMember()`.

### Project access

В `ProjectAccessService` оставь read method:

```java
requireAccessibleProject(...)
```

Добавь write method:

```java
requireWritableProject(...)
```

Он должен:

```text
1. requireContributor
2. scoped project lookup
3. вернуть AccessibleProject
```

Чтобы не дублировать project lookup, вынеси private method.

### Task

Используй:

```text
findForProject       -> requireAccessibleProject
create               -> requireWritableProject
changeStatus         -> requireWritableProject
```

Так VIEWER видит доску, но POST/PATCH получает 403.

---

## 13. Controller

Создай:

```text
collabdesk.controller.WorkspaceMemberController
```

Base URL:

```java
@RequestMapping("/api/v1/workspaces/{workspaceId}/members")
```

Методы:

```java
@GetMapping
List<WorkspaceMemberResponse> findAll(...)

@PostMapping
@ResponseStatus(HttpStatus.CREATED)
WorkspaceMemberResponse add(...)

@PatchMapping("/{memberId}/role")
WorkspaceMemberResponse changeRole(...)

@DeleteMapping("/{memberId}")
@ResponseStatus(HttpStatus.NO_CONTENT)
void remove(...)
```

Во всех операциях current user берётся из principal.

---

## 14. Service tests

Проверь:

1. member может получить список;
2. список immutable;
3. OWNER добавляет зарегистрированного ACTIVE user;
4. email нормализуется;
5. добавленный member связан с правильным workspace;
6. creator запроса не берётся из body;
7. non-owner не добавляет member;
8. VIEWER не добавляет member;
9. неизвестный email возвращает нужную exception;
10. disabled user не добавляется;
11. duplicate membership возвращает conflict;
12. роль OWNER нельзя назначить;
13. OWNER меняет MEMBER на VIEWER;
14. нельзя изменить owner membership;
15. memberId другого workspace не находится;
16. OWNER удаляет обычного member;
17. User остаётся после удаления membership;
18. OWNER нельзя удалить;
19. при отказе доступа mutation repository methods не вызываются.

---

## 15. HTTP integration tests

Настоящий flow:

```text
register owner
register second user
owner login
create workspace
owner adds second user
second user login
second user sees workspace
second user opens projects/tasks
```

Сценарии:

1. owner получает список с самим собой;
2. owner добавляет user и получает `201`;
3. добавленный user видит workspace в своём GET;
4. duplicate add получает `409`;
5. unknown email получает `404`;
6. member получает список участников;
7. non-member получает нейтральный `404`;
8. MEMBER не может управлять participants — `403`;
9. VIEWER читает projects/tasks;
10. VIEWER не создаёт project — `403`;
11. VIEWER не создаёт task — `403`;
12. VIEWER не меняет task status — `403`;
13. owner меняет role — `200`;
14. role сохраняется после повторного GET;
15. owner удаляет member — `204`;
16. удалённый user перестаёт видеть workspace;
17. owner нельзя изменить;
18. owner нельзя удалить;
19. memberId другого workspace возвращает `404`;
20. anonymous GET получает `401`;
21. POST/PATCH/DELETE без CSRF получают `403`;
22. invalid email получает `400`;
23. null role получает `400`;
24. unknown role получает `400`.

### Главный permission test

```text
OWNER создаёт workspace, project и task
OWNER добавляет VIEWER
VIEWER входит
```

VIEWER:

```text
GET projects       -> 200
GET tasks          -> 200
POST project       -> 403
POST task          -> 403
PATCH task status  -> 403
```

После запрещённых запросов данные в MySQL не изменились.

---

## 16. Frontend

Добавь:

```text
frontend/src/api/memberApi.js
```

Методы:

```javascript
getWorkspaceMembers(workspaceId)
addWorkspaceMember(workspaceId, form)
changeWorkspaceMemberRole(workspaceId, memberId, role)
removeWorkspaceMember(workspaceId, memberId)
```

POST/PATCH/DELETE используют `withCsrf`.

### UI

В открытом workspace добавь кнопку:

```text
Участники
```

Панель показывает:

- displayName;
- email;
- role;
- дату добавления.

Если текущая роль workspace равна OWNER:

- видна форма добавления по email;
- для non-owner доступен select роли;
- доступна кнопка удаления.

Для остальных ролей панель read-only.

### VIEWER UI

Если `workspace.role === 'VIEWER'`:

- скрыть кнопку создания project;
- скрыть кнопку создания task;
- отключить select смены task status;
- показать badge `Только просмотр`.

Frontend-проверки не заменяют backend authorization. Даже если пользователь
вручную отправит запрос, backend обязан вернуть 403.

---

## 17. Порядок реализации

```text
1. WorkspaceMember domain methods
   -> entity tests

2. repository scoped queries
   -> repository tests

3. DTO + exceptions

4. requireOwner / requireContributor
   -> access tests

5. WorkspaceMemberService
   -> service tests

6. WorkspaceMemberController
   -> HTTP tests

7. применить VIEWER к ProjectService

8. добавить writable Project access

9. применить writable access к TaskService
   -> permission tests

10. full backend suite

11. React members panel

12. VIEWER state во frontend

13. frontend lint/build

14. browser flow с двумя аккаунтами
```

---

## 18. Что не делать

- Не принимать userId от frontend.
- Не разрешать назначение OWNER.
- Не разрешать изменение или удаление текущего OWNER.
- Не искать membership только по memberId.
- Не считать ADMIN владельцем.
- Не проверять role только на frontend.
- Не возвращать подробности чужого workspace.
- Не удалять User при удалении membership.
- Не добавлять email delivery в этот этап.
- Не добавлять assignee до завершения member API.
- Не кэшировать membership или permissions.

---

## 19. Redis

Redis всё ещё не нужен.

Особенно нельзя кэшировать:

```text
membership
role
permissions
```

После удаления пользователя или смены VIEWER → MEMBER новое разрешение должно
действовать сразу. Устаревший authorization cache может либо выдать лишний
доступ, либо продолжить запрещать уже разрешённую операцию.

---

## 20. Критерии завершения

- [ ] Owner видит себя в members list.
- [ ] Owner добавляет зарегистрированного пользователя.
- [ ] Duplicate membership отклоняется.
- [ ] OWNER нельзя назначить через API.
- [ ] Owner меняет ADMIN/MEMBER/VIEWER.
- [ ] Owner удаляет non-owner member.
- [ ] Owner membership нельзя изменить или удалить.
- [ ] Member lookup scoped по workspace.
- [ ] Non-owner mutation получает 403.
- [ ] VIEWER читает workspace/project/task.
- [ ] VIEWER не создаёт project/task.
- [ ] VIEWER не изменяет task status.
- [ ] Удалённый member сразу теряет доступ.
- [ ] Anonymous получает 401.
- [ ] Mutating requests требуют CSRF.
- [ ] Backend suite проходит.
- [ ] Frontend members panel работает.
- [ ] Frontend учитывает VIEWER.
- [ ] Frontend lint/build проходят.
- [ ] Redis не добавлен.

---

## 21. Вопросы для самопроверки

1. Чем authentication отличается от membership?
2. Чем membership отличается от permission?
3. Почему VIEWER получает 403, а non-member нейтральный 404?
4. Почему memberId ищется вместе с workspaceId?
5. Почему OWNER нельзя назначить обычным PATCH?
6. Почему owner membership нельзя удалить?
7. Почему удаление membership не удаляет User?
8. Почему User ищется по нормализованному email?
9. Почему frontend не должен отправлять userId?
10. Почему `requireMember` остаётся для GET?
11. Почему POST project использует `requireContributor`?
12. Почему Task GET и Task PATCH используют разные access methods?
13. Почему скрытая frontend-кнопка не является authorization?
14. Какие тесты доказывают, что VIEWER действительно read-only?
15. Почему permissions нельзя кэшировать сейчас?
