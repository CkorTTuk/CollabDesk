# Этап 14. Кастомные роли workspace для участников проектов

## Результат этапа

После этого этапа владелец или администратор workspace сможет создавать
переиспользуемые роли вроде:

```text
Developer
QA
Designer
Reviewer
Project Lead
```

Кастомная роль:

- создаётся только на уровне workspace;
- доступна для назначения во всех проектах этого workspace;
- назначается пользователю в контексте конкретного проекта;
- содержит название, цвет и набор permissions.

Например, роль `Developer` создаётся один раз в workspace. В проекте A её можно
назначить Alice, а в проекте B — Bob. Назначение Alice в проекте A не даёт ей
эту роль в проекте B.

Кастомных ролей, принадлежащих отдельному проекту или задаче, на этом этапе нет.

---

## 1. Не смешивать системную и кастомную роли

У `WorkspaceMember` уже есть системная `WorkspaceRole`:

```java
OWNER,
ADMIN,
MEMBER,
VIEWER
```

Она определяет положение пользователя внутри workspace:

- `OWNER` владеет workspace;
- `ADMIN` управляет workspace;
- `MEMBER` является обычным участником;
- `VIEWER` имеет жёсткий read-only режим.

Новая `AccessRole` определяет, что участник может делать внутри конкретного
проекта.

Пример:

```text
WorkspaceRole: MEMBER

CollabDesk MVP:
  AccessRole: Backend Developer

Marketing Site:
  AccessRole: Reviewer
```

Не добавляй `DEVELOPER`, `QA`, `DESIGNER` и другие предметные роли в
`WorkspaceRole`.

## 2. Границы модели

Роль всегда принадлежит workspace:

```text
Workspace
└── AccessRole
```

Назначение роли всегда принадлежит участнику проекта:

```text
ProjectMember
└── ProjectMemberRole
    └── AccessRole того же workspace
```

Из этого следуют правила:

- роль нельзя создать внутри отдельного проекта;
- роль нельзя назначить напрямую `WorkspaceMember`;
- роль нельзя назначить напрямую `User`;
- роль нельзя назначить `TaskAssignee`;
- пользователь сначала должен стать `ProjectMember`;
- назначенная роль действует во всём этом проекте, включая его задачи;
- роль не даёт доступ к проекту, в котором пользователь не состоит;
- одна роль workspace может использоваться во многих проектах;
- в разных проектах одному пользователю можно назначить разные роли.

В задаче роли не назначаются. У задачи может быть только один конкретный
assignee из состава Project team.

## 3. Жёсткие системные правила

Даже после появления кастомных permissions только `OWNER` и `ADMIN` могут:

- добавлять и удалять участников Project team;
- назначать и снимать task assignees;
- создавать, изменять и удалять кастомные роли;
- назначать роли участникам проекта.

`OWNER` и `ADMIN` имеют системный override для всех project permissions.

`VIEWER` остаётся read-only независимо от назначенных кастомных ролей. Кастомная
роль не может повысить `VIEWER` до пользователя с правом изменения.

Кастомные permissions не управляют составом workspace, Project team или списком
task assignees.

## 4. Permission enum

Создай:

```text
collabdesk/project/role/entity/ProjectPermission.java
```

Начальный набор:

```java
EDIT_PROJECT,
CREATE_TASK,
EDIT_TASK,
CHANGE_TASK_STATUS,
CHANGE_TASK_VISIBILITY
```

Пока не добавляй:

```text
MANAGE_PROJECT_MEMBERS
MANAGE_TASK_ASSIGNEES
MANAGE_ROLES
```

Эти операции остаются только у `OWNER` и `ADMIN`.

Отдельный `VIEW_PROJECT` не нужен: видимость определяется
`ProjectVisibility`, `TaskVisibility`, `ProjectMember` и `TaskAssignee`.

## 5. Effective permissions

Для обычного `MEMBER` действуют два взаимоисключающих режима:

1. если его `ProjectMember` не имеет кастомных ролей, используется базовый набор
   permissions;
2. если назначена хотя бы одна кастомная роль, базовый набор больше не
   применяется, а итоговые права равны объединению permissions всех назначенных
   ролей.

Базовый набор сохраняет поведение, которое до этого давал
`requireContributor`:

```text
EDIT_PROJECT
CREATE_TASK
EDIT_TASK
CHANGE_TASK_STATUS
CHANGE_TASK_VISIBILITY
```

Храни этот набор в одном месте внутри `ProjectPermissionService`, а не
дублируй по services и controllers.

Пример:

```text
Developer:
  CREATE_TASK
  EDIT_TASK

Workflow Manager:
  CHANGE_TASK_STATUS
  CHANGE_TASK_VISIBILITY

effective permissions:
  CREATE_TASK
  EDIT_TASK
  CHANGE_TASK_STATUS
  CHANGE_TASK_VISIBILITY
```

Для действий над задачей используется тот же набор project permissions.
Отдельных task roles и task permissions нет.

`CHANGE_TASK_STATUS` дополнительно требует назначения на конкретную задачу:

```text
OWNER/ADMIN -> могут менять статус любой доступной задачи
VIEWER      -> никогда не меняет статус
MEMBER      -> должен иметь CHANGE_TASK_STATUS и быть assignee этой задачи
```

Если задача не имеет assignee, менять её статус могут только `OWNER` и `ADMIN`.
Назначение нескольких пользователей на одну задачу не поддерживается.

Наличие кастомной роли проверяй по назначению `ProjectMemberRole`, а не по
результату запроса permissions. У назначенной роли может быть пустой набор
permissions; это означает отсутствие write-возможностей, а не переход к
базовому набору.

Системные исключения:

```text
OWNER/ADMIN -> имеют все ProjectPermission
VIEWER      -> не имеет ни одного изменяющего permission
MEMBER без кастомных ролей -> получает базовый набор
MEMBER с кастомными ролями -> получает объединение permissions этих ролей
```

Сначала всегда проверяется visibility через `ProjectAccessService` или
`TaskAccessService`. Permission не должен открывать скрытый проект или задачу.

Создание нового проекта остаётся доступно по системной `WorkspaceRole`, как и
до этого этапа. Кастомные роли начинают действовать только внутри уже
существующего проекта, потому что назначаются через `ProjectMember`.

## 6. Миграции V7, V8 и V9

V7 уже была применена как пустая миграция-заглушка с checksum `0`. Оставь файл
пустым для совместимости с существующими базами:

```text
src/main/resources/db/migration/V7__create_custom_access_roles.sql
```

Таблицы кастомных ролей создавай в:

```text
src/main/resources/db/migration/V8__create_custom_access_roles.sql
```

### Таблица `access_roles`

```sql
CREATE TABLE access_roles (
    id BIGINT AUTO_INCREMENT,
    workspace_id BIGINT NOT NULL,
    name VARCHAR(60) NOT NULL,
    color VARCHAR(7) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    CONSTRAINT access_roles_workspace_name_uk
        UNIQUE (workspace_id, name),

    CONSTRAINT access_roles_workspace_fk
        FOREIGN KEY (workspace_id)
            REFERENCES workspaces(id)
            ON DELETE CASCADE
);
```

У роли нет `scope` и `project_id`: единственный возможный scope —
workspace.

### Таблица `access_role_permissions`

```sql
CREATE TABLE access_role_permissions (
    role_id BIGINT NOT NULL,
    permission VARCHAR(40) NOT NULL,

    PRIMARY KEY (role_id, permission),

    CONSTRAINT access_role_permissions_role_fk
        FOREIGN KEY (role_id)
            REFERENCES access_roles(id)
            ON DELETE CASCADE,

    CONSTRAINT access_role_permissions_value_ck
        CHECK (permission IN (
            'EDIT_PROJECT',
            'CREATE_TASK',
            'EDIT_TASK',
            'CHANGE_TASK_STATUS',
            'CHANGE_TASK_VISIBILITY'
        ))
);
```

### Таблица `project_member_roles`

```sql
CREATE TABLE project_member_roles (
    project_member_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,

    PRIMARY KEY (project_member_id, role_id),

    CONSTRAINT project_member_roles_member_fk
        FOREIGN KEY (project_member_id)
            REFERENCES project_members(id)
            ON DELETE CASCADE,

    CONSTRAINT project_member_roles_role_fk
        FOREIGN KEY (role_id)
            REFERENCES access_roles(id)
            ON DELETE CASCADE
);
```

Совпадение workspace между `ProjectMember.project` и `AccessRole` проверяется в
service. Роль из другого workspace назначить нельзя.

Таблицу `task_assignee_roles` не создавай.

Уже применённую пустую V7 не изменяй. В V9 добавь в существующую
`task_assignees` unique constraint по `task_id`, чтобы у задачи был максимум
один assignee. При миграции существующей базы для задач с несколькими
назначениями оставь самое раннее назначение, остальные удали перед созданием
unique constraint.

## 7. Entity

Создай:

```text
project/role/entity/AccessRole.java
project/role/entity/AccessRolePermission.java
project/role/entity/ProjectMemberRole.java
```

`AccessRole` содержит:

```text
id
workspace
name
color
version
```

В конструкторе:

- нормализовать `name`;
- разрешить длину `2..60`;
- проверять цвет регулярным выражением `#[0-9A-Fa-f]{6}`;
- хранить цвет в hex-формате `#RRGGBB`;
- после валидации нормализовать буквы цвета в uppercase;
- требовать непустой `workspace`.

Не создавай `AccessRoleScope` и не добавляй в `AccessRole` ссылку на `Project`.

Не делай `@ManyToMany` между role и permissions или между project member и
roles. Отдельные entity и repository упрощают замену набора permissions,
проверку workspace и тестирование ограничений.

## 8. Без default-ролей и backfill

При создании workspace не создавай кастомные роли автоматически. По умолчанию
существуют только системные `WorkspaceRole`.

Миграция V8 создаёт таблицы, но не добавляет `Project Lead`, `Contributor` или
другие предустановленные роли и не создаёт назначений для существующих
`ProjectMember`.

Существующие и новые участники проекта без кастомных ролей продолжают работать
с базовым набором permissions. Поэтому отдельный backfill не нужен.

Создатель нового проекта, как и раньше, автоматически становится
`ProjectMember`, но кастомная роль ему не назначается. До ручного назначения
ролей для него действует базовый набор либо системный override, если он
`OWNER`/`ADMIN`.

## 9. Repository

Создай:

```text
AccessRoleRepository
AccessRolePermissionRepository
ProjectMemberRoleRepository
```

Нужны запросы:

```text
all roles of workspace
roles assigned to projectMember
effective permissions of projectMember
role usage count
```

Для effective permissions используй `select distinct permission`.

Не загружай роли и permissions по одной в цикле: это создаст N+1.

`TaskAssigneeRoleRepository` не нужен.

## 10. ProjectPermissionService

Создай:

```text
collabdesk/project/role/service/ProjectPermissionService.java
```

Методы:

```java
void requireProjectPermission(
    AccessibleProject access,
    ProjectPermission permission
);

void requireTaskPermission(
    AccessibleTask access,
    ProjectPermission permission
);

Set<ProjectPermission> findEffectiveProjectPermissions(...);

Set<ProjectPermission> findEffectiveTaskPermissions(...);
```

`findEffectiveTaskPermissions` возвращает те же permissions, что назначены
текущему пользователю через `ProjectMember` родительского проекта.

Алгоритм:

```text
сначала проверить доступ к project/task
OWNER/ADMIN -> разрешить
VIEWER      -> 403
MEMBER      -> найти ProjectMember
ролей нет   -> использовать базовый набор permissions
роли есть   -> загрузить объединение permissions этих ролей
permission отсутствует -> 403
```

## 11. Замена старых проверок

После внедрения permission service:

```text
ProjectService.change...       -> EDIT_PROJECT
TaskService.create             -> CREATE_TASK
TaskService.edit               -> EDIT_TASK
TaskService.changeStatus       -> CHANGE_TASK_STATUS
TaskService.changeVisibility   -> CHANGE_TASK_VISIBILITY
```

Для `TaskService.changeStatus` одной permission недостаточно: после её проверки
обычный `MEMBER` должен совпадать с единственным assignee задачи.

При этом:

```text
ProjectMemberService.add/remove -> всё ещё requireManager
TaskAssigneeService.assign      -> всё ещё requireManager
AccessRoleService               -> всё ещё requireManager
ProjectMemberRoleService        -> всё ещё requireManager
```

Не используй только `requireContributor`: он не учитывает, что назначенная
кастомная роль заменяет базовый набор permissions.

## 12. DTO

Создай:

```text
CreateAccessRoleRequest
UpdateAccessRoleRequest
AccessRoleResponse
AccessRoleSummaryResponse
ReplaceProjectMemberRolesRequest
EffectivePermissionsResponse
```

Пример создания роли:

```json
{
  "name": "Backend Developer",
  "color": "#4F7DF3",
  "permissions": [
    "CREATE_TASK",
    "EDIT_TASK",
    "CHANGE_TASK_STATUS"
  ]
}
```

`permissions`:

- обязательный `Set`;
- может быть пустым;
- максимум 5 значений;
- не содержит `null`.

Пример замены ролей участника проекта:

```json
{
  "roleIds": [1, 4]
}
```

Response роли возвращает permissions сразу, без отдельного HTTP-запроса.

## 13. REST API

### Управление ролями workspace

```http
GET    /api/v1/workspaces/{workspaceId}/access-roles
POST   /api/v1/workspaces/{workspaceId}/access-roles
PATCH  /api/v1/workspaces/{workspaceId}/access-roles/{roleId}
DELETE /api/v1/workspaces/{workspaceId}/access-roles/{roleId}
```

Project-local endpoints для создания ролей не добавляй.

### Назначение ролей при добавлении участника проекта

Расширь существующий request добавления `ProjectMember`:

```json
{
  "workspaceMemberId": 42,
  "roleIds": [1, 4]
}
```

Все `roleIds` должны принадлежать workspace проекта. Добавление
`ProjectMember` и назначение ролей выполняются в одной транзакции.

Если хотя бы одна роль не существует или принадлежит другому workspace,
операция полностью откатывается.

Пустой набор ролей допустим. Для такого `MEMBER` снова действует базовый набор
permissions.

### Замена ролей существующего ProjectMember

```http
PUT /api/v1/workspaces/{workspaceId}/projects/{projectId}/members/{projectMemberId}/roles
```

Body:

```json
{
  "roleIds": [1, 4]
}
```

PUT полностью заменяет текущий набор ролей. Пустой набор снимает все кастомные
роли и возвращает `MEMBER` к базовому набору permissions.

Все POST/PATCH/PUT/DELETE:

- требуют CSRF;
- требуют `OWNER` или `ADMIN`;
- проверяют workspace и project;
- не принимают role другого workspace.

Endpoints назначения ролей task assignee не добавляй.

## 14. Удаление роли

Если роль назначена хотя бы одному `ProjectMember`, возвращай:

```text
409 Conflict
```

Не удаляй назначения автоматически: владелец должен сначала явно заменить роли
участников проектов.

Создай:

```text
AccessRoleInUseException
AccessRoleNotFoundException
AccessRoleAlreadyExistsException
AccessRoleWorkspaceMismatchException
```

Добавь нейтральные `ProblemDetail`.

## 15. Изменение permissions

Обновление роли выполняется транзакционно:

1. проверить manager access;
2. найти роль строго внутри указанного workspace;
3. обновить name и color;
4. удалить старый набор permissions;
5. сохранить новый набор;
6. вернуть полную роль.

При конфликте имени вся транзакция откатывается.

Добавь optimistic locking через `version`.

Изменение permissions роли сразу влияет на все проекты, где эта workspace-роль
назначена. Это ожидаемое поведение и должно быть явно показано в UI.

## 16. Response существующих API

Расширь `ProjectMemberResponse`:

```text
roles: List<AccessRoleSummaryResponse>
effectivePermissions: Set<ProjectPermission>
```

Расширь `TaskResponse` для текущего пользователя:

```text
currentUserPermissions: Set<ProjectPermission>
```

`currentUserPermissions` задачи вычисляется по ролям текущего пользователя в
родительском проекте.

`TaskAssigneeResponse` ролями не расширяй. `TaskResponse` возвращает один
nullable `assignee`, а не список `assignees`.

Frontend использует permissions из backend только для управления интерфейсом.
Backend остаётся единственным источником безопасности.

## 17. Frontend

### Управление ролями

Для `OWNER` и `ADMIN` добавь `Manage roles` на странице workspace.

Форма роли:

- name;
- выбор цвета из заранее заданной палитры;
- список permissions с понятными описаниями.

Цвет отправляется и хранится в hex-формате `#RRGGBB`. Используй одну общую
палитру для создания, редактирования и отображения ролей. Произвольный ввод
цвета пользователю не нужен.

Покажи предупреждение, что изменение роли повлияет на все проекты workspace,
где она назначена.

### Project team

В форме добавления workspace member в проект:

- добавить выбор одной или нескольких ролей workspace;
- отправлять `roleIds` вместе с `workspaceMemberId`;
- показывать только роли текущего workspace.

В строке участника:

- показывать цветные chips его ролей в этом проекте;
- добавить отдельную кнопку редактирования ролей;
- разрешить сохранить пустой набор ролей.

Если ролей нет, покажи нейтральную отметку `Default permissions`, чтобы было
понятно, что участник не остаётся без прав.

Роли не показываются и не редактируются в выборе task assignee. Для задачи
выбирается один конкретный участник проекта либо `Unassigned`.

### Управление кнопками

Используй `effectivePermissions`, полученные от backend:

```text
EDIT_PROJECT            -> разрешить редактирование проекта
CREATE_TASK             -> показать New task
EDIT_TASK               -> разрешить редактирование задачи
CHANGE_TASK_STATUS      -> разрешить status picker
CHANGE_TASK_VISIBILITY  -> показать visibility picker
```

Не вычисляй доступ по названию роли: роль может называться как угодно, значение
имеют только её permissions.

## 18. OpenAPI

Задокументируй:

- `ProjectPermission`;
- все role DTO;
- workspace role endpoints;
- `roleIds` в request добавления `ProjectMember`;
- endpoint замены ролей `ProjectMember`;
- `409` при duplicate или role in use;
- ошибку роли из другого workspace;
- CSRF;
- примеры permissions.

Обнови `OpenApiIntegrationTest`.

## 19. Структура пакетов

Не оставляй все домены и контроллеры в плоских пакетах внутри `collabdesk`.
Группируй код по предметной области:

```text
collabdesk
├── auth/controller
├── common/web
├── workspace
│   ├── controller
│   └── member/{controller,dto,entity,repository,service}
├── project
│   ├── controller
│   ├── member/{controller,dto,entity,repository,service}
│   └── role/{controller,dto,entity,repository,service}
└── task
    ├── controller
    └── assignee/{dto,entity,repository,service}
```

Структура `src/test/java/collabdesk` должна зеркально повторять production
packages. Общий пакет `collabdesk.controller`, а также плоские
`workspacemember`, `projectmember`, `accessrole` и `taskassignee` не оставляй.

## 20. Tests

### Entity

Проверь:

- нормализацию name;
- валидацию и нормализацию hex-цвета;
- обязательный workspace;
- version;
- невозможность создать некорректную роль.

### Repository

Проверь:

- уникальное имя роли внутри workspace;
- одинаковое имя разрешено в разных workspace;
- список возвращает только роли запрошенного workspace;
- effective permissions возвращаются через `distinct`;
- cascade при удалении workspace и project member.

### Service

Проверь:

- только manager создаёт, изменяет и назначает роли;
- `VIEWER` не получает write permission;
- `MEMBER` без ролей получает базовый набор permissions;
- назначение первой роли заменяет базовый набор набором permissions этой роли;
- назначенная роль с пустым набором permissions не включает fallback;
- снятие всех ролей возвращает базовый набор;
- permissions нескольких ролей объединяются;
- роль действует во всём проекте и его задачах;
- `MEMBER` меняет статус только назначенной ему задачи;
- у задачи не может быть больше одного assignee;
- роль не действует в другом проекте без отдельного назначения;
- одна workspace-роль может быть назначена в разных проектах;
- роль другого workspace нельзя назначить;
- роли назначаются транзакционно вместе с новым `ProjectMember`;
- роль в использовании нельзя удалить;
- изменение набора permissions транзакционно;
- workspace и project создаются без автоматических кастомных ролей.

### Integration

Полный HTTP flow:

1. создать workspace;
2. убедиться, что default-роли не создавались;
3. создать два проекта;
4. добавить workspace member в первый проект без ролей;
5. проверить, что для него действует полный базовый набор;
6. создать workspace-роль `Reviewer` только с `CHANGE_TASK_STATUS`;
7. назначить её участнику первого проекта;
8. проверить, что без назначения на задачу status изменить нельзя;
9. назначить участника единственным assignee и проверить смену status;
10. проверить невозможность назначить двух assignees;
11. снять все роли и проверить возврат базового набора;
12. убедиться, что назначение в первом проекте не даёт доступ ко второму;
13. проверить объединение нескольких ролей и `VIEWER` hard ceiling;
14. проверить duplicate role, wrong workspace, CSRF и role-in-use conflict.

## 21. Критерии завершения

- [ ] Пустая V7 сохраняет checksum `0`, а V8 и V9 применяются на пустой и
  существующей базе.
- [ ] Роли создаются только внутри workspace.
- [ ] В `AccessRole` нет project или task scope.
- [ ] Workspace-роли переиспользуются между проектами.
- [ ] Роли назначаются при добавлении пользователя в Project team.
- [ ] Назначение роли действует только в конкретном проекте.
- [ ] Для другого проекта требуется отдельное назначение.
- [ ] Роль другого workspace назначить нельзя.
- [ ] Default-роли и автоматические назначения не создаются.
- [ ] MEMBER без кастомных ролей получает базовый набор permissions.
- [ ] Назначенные роли заменяют базовый набор своим объединением permissions.
- [ ] Снятие всех ролей возвращает базовый набор.
- [ ] Старые project members продолжают работать без backfill.
- [ ] OWNER/ADMIN имеют системный override.
- [ ] VIEWER всегда остаётся read-only.
- [ ] Назначать участников, assignees и роли могут только OWNER/ADMIN.
- [ ] Отдельных task roles и task-role endpoints нет.
- [ ] У задачи не больше одного assignee.
- [ ] MEMBER меняет статус только задачи, на которую он назначен.
- [ ] Списки и responses не создают N+1.
- [ ] OpenAPI полностью обновлён.
- [ ] Полный Maven suite проходит.
- [ ] Frontend lint и production build проходят.

## Что пока не делать

- не добавлять предметные роли в `WorkspaceRole`;
- не создавать project-local роли;
- не создавать task roles;
- не связывать role напрямую с `User` или `WorkspaceMember`;
- не использовать название роли для проверки доступа;
- не разрешать custom role управлять project members или task assignees;
- не добавлять deny-permissions — пока используем только объединение allow;
- не добавлять наследование ролей;
- не добавлять default-роли;
- не добавлять автоназначение роли при добавлении пользователя в workspace;
- не кешировать permissions в Redis на этом этапе.

В будущем можно добавить настройку default custom role для новых участников
workspace. Она должна быть отдельной opt-in настройкой workspace, а не скрытым
поведением базовой permission-модели.

Redis лучше подключать после стабилизации permission-модели. Иначе изменение
workspace-роли потребует сложной инвалидации кеша по всем проектам и участникам,
которым она назначена.
