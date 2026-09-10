# Этап 17. Простые права на задачи и журнал действий

## Что должно получиться

После этого этапа правила станут проще:

- `OWNER`, `ADMIN` и `MEMBER` могут создавать задачи;
- `VIEWER` может только смотреть задачи;
- по умолчанию все участники workspace могут открывать обычные проекты и задачи;
- проект автоматически закрывается, если к нему явно назначили людей или роли;
- при создании проекта людей и разрешённые роли можно выбрать сразу;
- пользовательские роли проекта управляют только самим проектом;
- возле проекта и задачи видно, кто их создал;
- у задачи можно открыть небольшой журнал действий;
- в журнале видно, кто создал задачу, изменил статус или назначил исполнителя;
- `MEMBER` может взять свободную задачу на себя, но не назначать других;
- `OWNER` и `ADMIN` могут назначать людей и project-роли;
- создавать проекты и выбирать начальный доступ могут `OWNER` и `ADMIN`;
- `ADMIN` не может изменять `OWNER` или управлять его membership;
- ошибка подключения к Redis не ломает основной сценарий приложения.

Под «журналом» здесь понимается не сложная корпоративная audit-система. Это
обычная таблица MySQL, в которую приложение записывает несколько понятных
событий.

---

## 1. Сначала зафиксируй новые правила

До изменения кода важно понять, откуда теперь берутся права.

### Доступ по умолчанию

Новый проект, в который никого отдельно не назначили, доступен всем участникам
workspace:

```text
OWNER / ADMIN / MEMBER / VIEWER
              -> могут открыть проект
              -> могут увидеть разрешённые им задачи
```

Никому не нужно переключать `Project access` или выбирать
`WORKSPACE / RESTRICTED`. Такого переключателя больше нет.

Доступ вычисляется автоматически:

```text
нет явных назначений
-> проект открыт всему workspace

добавили человека или разрешили workspace custom role
-> проект автоматически закрыт
-> его видят OWNER, ADMIN, создатель, выбранные люди
   и участники с разрешёнными ролями

удалили последние явные назначения
-> проект снова открыт всему workspace
```

Создатель проекта не считается отдельным назначением. Иначе автоматически
созданная связь автора с проектом закрывала бы каждый новый проект сразу после
создания.

Пользователь, которого не назначили в закрытый проект, не должен получить
доступ подбором `projectId` в URL.

`VIEWER` не назначается автоматически. Это просто доступная workspace-роль,
которую `OWNER` или разрешённый `ADMIN` может выдать вручную, когда человеку
действительно нужен режим только для чтения.

### Пользовательские роли проекта

Пользовательская роль создаётся внутри workspace и может быть назначена одному
или нескольким workspace members. При создании проекта владелец выбирает, какие
из этих ролей допускаются в проект.

Получается две независимые связи:

```text
workspace member <-> custom role
project          <-> allowed custom role
```

Если у участника есть хотя бы одна роль из allow-list проекта, он получает
доступ. Дополнительно можно разрешить доступ конкретному человеку без роли.

На этом этапе в `ProjectPermission` остаётся:

```java
public enum ProjectPermission {
    EDIT_PROJECT
}
```

То есть такая роль одновременно может быть группой доступа и разрешать менять
название, описание или другие настройки проекта. Она не должна решать, можно ли
создавать или двигать задачи.

Роли не обязательны. Без выбранных людей и allowed roles обычный `MEMBER` всё
равно видит проект и может работать со своими задачами. Роли создаются только
тогда, когда владельцу нужна дополнительная организация, например:

```text
Project editor
Project lead
Reviewer
Designer
```

В текущей простой модели реальное разрешение у роли одно — `EDIT_PROJECT`.
Остальные роли могут использоваться как понятные группы доступа. Не нужно
создавать встроенную custom role автоматически для каждого человека.

### Создание проекта

После нажатия `+ New project` форма должна сразу показать:

```text
Project name
Description

Allowed roles
[ ] Project editor
[ ] Reviewer
[ ] Designer

Allowed people
[ ] Anna
[ ] Max
[ ] Sofia
```

Правило очень простое:

```text
ничего не выбрано -> общий проект
выбран хотя бы один roleId или workspaceMemberId -> закрытый проект
```

Поля можно менять до создания проекта. После создания те же настройки доступны
`OWNER` и `ADMIN` в управлении участниками проекта.

### Права на задачи

Права на задачи определяет обычная роль участника workspace:

| Workspace role | Смотреть | Создавать | Менять задачу | Взять себе | Назначить другого |
|---|---:|---:|---:|---:|---:|
| `OWNER` | да | да | любую | да | да |
| `ADMIN` | да | да | любую | да | да |
| `MEMBER` | да | да | свою или взятую | да | нет |
| `VIEWER` | да | нет | нет | нет | нет |

Под «своей» понимается задача, которую пользователь создал. Под «взятой» —
задача, где он является текущим assignee.

Это не означает, что любой участник видит закрытый проект. Сначала по-прежнему
проверяется доступ через `ProjectAccessService`. Затем проверяется workspace
role, а для обычного `MEMBER` — является ли он автором или исполнителем задачи.

### OWNER и ADMIN

`OWNER` обладает всеми административными возможностями. `ADMIN` может помогать
управлять workspace, проектами, участниками и назначениями, но owner membership
является защищённым:

- `ADMIN` не может удалить `OWNER` из workspace;
- `ADMIN` не может изменить роль `OWNER`;
- `ADMIN` не может назначить кому-либо роль `OWNER`;
- операции владельца не требуют одобрения администратора;
- если решение относится к защищённому owner-объекту, у владельца приоритет.

Проще говоря: `OWNER` — это такой же рабочий администратор, но над ним нет
другого workspace-администратора.

### Статусы задач

Пока оставь существующие статусы:

```text
TODO -> IN_PROGRESS -> DONE
```

Журнал покажет, кто выполнил каждый переход. Например:

```text
Anna created the task
Max took the task
Max changed status from TODO to IN_PROGRESS
Max changed status from IN_PROGRESS to DONE
Anna changed status from DONE to TODO
```

Отдельный статус `ARCHIVED` сейчас не добавляй. Архивация задачи меняет её
жизненный цикл и требует отдельного решения: показывать ли её на доске, кто
может восстановить её и можно ли редактировать архивную задачу.

---

## 2. Убери ручной project access и task permissions из project-ролей

### Удали отдельную видимость проекта

Больше не нужны:

```text
ProjectVisibility
UpdateProjectVisibilityRequest
PATCH /projects/{projectId}/visibility
ProjectService.changeVisibility(...)
селектор Project access во frontend
changeProjectVisibility(...) во frontend API
```

Также убери поле `visibility` из `Project`, `ProjectResponse` и
`ProjectAccessOverviewResponse`.

В новой миграции удали старую колонку:

```sql
ALTER TABLE projects
    DROP CHECK projects_visibility_ck,
    DROP COLUMN visibility;
```

Task visibility `PROJECT / ASSIGNEES` — другая настройка. Она относится к
отдельной задаче, поэтому на этом этапе её можно оставить.

Не удаляй `ProjectAccessService` и проверки доступа из task endpoints. Удаляется
только ручной флаг проекта. Сам access service теперь вычисляет доступ по
назначениям.

### Вычисляй закрытость по назначениям

В базе не нужно поле `restricted = true`. Состояние выводится из существующих
связей:

```text
есть прямой доступ хотя бы для одного человека
или есть allowed custom role хотя бы для одной роли
-> проект закрыт
```

Автоматическая запись `ProjectMember` для создателя может остаться: она нужна
для других связей, но при вычислении закрытости её нужно игнорировать.

### Добавь связи для role-based access

Чтобы выбранная роль означала доступ группы, нужны две таблицы:

```text
workspace_member_access_roles
    workspace_member_id
    role_id

project_allowed_roles
    project_id
    role_id
```

Добавь их в новую миграцию примерно так:

```sql
CREATE TABLE workspace_member_access_roles (
    workspace_member_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,

    PRIMARY KEY (workspace_member_id, role_id),

    CONSTRAINT workspace_member_access_roles_member_fk
        FOREIGN KEY (workspace_member_id)
        REFERENCES workspace_members(id) ON DELETE CASCADE,

    CONSTRAINT workspace_member_access_roles_role_fk
        FOREIGN KEY (role_id)
        REFERENCES access_roles(id) ON DELETE CASCADE
);

CREATE TABLE project_allowed_roles (
    project_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,

    PRIMARY KEY (project_id, role_id),

    CONSTRAINT project_allowed_roles_project_fk
        FOREIGN KEY (project_id)
        REFERENCES projects(id) ON DELETE CASCADE,

    CONSTRAINT project_allowed_roles_role_fk
        FOREIGN KEY (role_id)
        REFERENCES access_roles(id) ON DELETE CASCADE
);
```

Foreign keys проверяют существование записей, но не проверяют, что member, role
и project относятся к одному workspace. Эту проверку обязательно сделай в
service до сохранения.

Первая отвечает на вопрос «какие custom roles есть у человека в workspace».
Вторая — «какие custom roles разрешены в проекте».

То есть роль назначается человеку один раз на уровне workspace. Затем при
создании проекта выбирается, какие из этих ролей допускаются именно в этот
проект. Если у Анны есть роль `Reviewer`, а в новом проекте выбран
`Reviewer`, Анна получает доступ автоматически.

Прямые назначения людей продолжай хранить через `project_members`. Запись
создателя проекта считается технической и не закрывает проект.

Старую таблицу `project_member_roles` больше не используй как основной источник
доступа: она связывает роль с человеком только внутри уже созданного проекта и
не позволяет заранее выбрать allowed role в форме `New project`.

Для локальных данных можно мигрировать старые назначения в обе новые таблицы:

```text
project_member_roles + project_members
-> роль назначается соответствующему workspace member
-> та же роль добавляется в allowed roles соответствующего проекта
```

После переноса и проверки старую таблицу можно удалить отдельной миграцией.

Логика `requireAccessibleProject(...)` должна разрешать доступ, если выполнено
хотя бы одно условие:

```text
пользователь OWNER или ADMIN
или пользователь создал проект
или проект ещё не имеет явных назначений
или пользователь явно добавлен в этот проект
или одна из ролей пользователя разрешена в проекте
```

Для списка проектов используй те же условия в запросе
`ProjectRepository.findAccessibleForWorkspace(...)`. Нельзя сделать правильную
проверку только в endpoint одной карточки, но вернуть закрытый проект в общем
списке.

Пример смысла JPQL-условия:

```text
manager
OR project.createdBy.id = currentUserId
OR no explicit project members and no allowed project roles
OR current user is an explicit project member
OR current user has an allowed role
```

Для одиночной проверки подготовь repository-методы со следующим смыслом:

```java
boolean existsByProject_IdAndWorkspaceMember_User_IdNot(
        Long projectId,
        Long creatorId
);

boolean existsByProject_Id(Long projectId); // projectAllowedRoleRepository

boolean existsAllowedRoleForUser(
        Long projectId,
        Long workspaceMemberId
);
```

Первый находит явно добавленного человека, кроме автоматически добавленного
создателя. Второй проверяет наличие role allow-list. Третий делает join между
`project_allowed_roles` и `workspace_member_access_roles`.

Тогда смысл проверки в `ProjectAccessService` будет таким:

```java
boolean manager = isManager(membership.getRole());
boolean creator = project.getCreatedBy().getId().equals(currentUserId);
boolean assigned = projectMemberRepository
        .existsByProject_IdAndWorkspaceMember_User_Id(
                projectId,
                currentUserId
        );
boolean restricted = projectMemberRepository
        .existsByProject_IdAndWorkspaceMember_User_IdNot(
                projectId,
                project.getCreatedBy().getId()
        ) || projectAllowedRoleRepository.existsByProject_Id(projectId);
boolean allowedByRole = projectAllowedRoleRepository
        .existsAllowedRoleForUser(projectId, membership.getId());

if (manager || creator || !restricted || assigned || allowedByRole) {
    return new AccessibleProject(project, membership);
}
throw new ProjectNotFoundException(
        "Project was not found or is not accessible"
);
```

Для `findAccessibleForWorkspace(...)` перенеси те же условия в JPQL. Особенно
важно явно разрешить создателя проекта: после первого назначения проект
закроется, но его автор не должен потерять доступ.

После добавления или удаления direct member, workspace member role или allowed
project role access overview должен инвалидироваться через существующее Spring
event. Поэтому вычисленный доступ и Redis-кеш обновятся после успешного commit.

### Расширь CreateProjectRequest

Добавь в запрос создания проекта два списка:

```java
public record CreateProjectRequest(
        String name,
        String description,
        Set<Long> allowedRoleIds,
        Set<Long> allowedWorkspaceMemberIds
) {
}
```

Если клиент не передал списки, преобразуй `null` в пустой `Set`. Это сохранит
совместимость со старым frontend и тестами.

Создание проекта с настройкой доступа разреши только `OWNER` и `ADMIN`. Обычный
`MEMBER` создаёт задачи внутри доступных проектов, но не создаёт проекты и не
формирует allow-list.

Перед сохранением проверь:

- все role IDs принадлежат текущему workspace;
- все workspace member IDs принадлежат текущему workspace;
- disabled user не добавляется;
- дубликаты удалены через `Set`;
- `ADMIN` не пытается изменить или переопределить `OWNER`.

### Сохраняй проект и доступ одной транзакцией

`ProjectService.create(...)` должен выполнить всё внутри одного
`@Transactional`-метода:

```text
requireManager
-> validate roleIds and workspaceMemberIds
-> save Project
-> save technical creator ProjectMember
-> save selected direct ProjectMembers
-> save ProjectAllowedRole rows
-> publish one access-changed event
-> COMMIT
```

Это важно: нельзя сначала создать общий проект, вернуть `201`, а вторым HTTP-
запросом закрывать его. Между запросами посторонний workspace member успеет
увидеть проект или получить его из Redis.

Если хотя бы один role/member ID неправильный, должна откатиться вся операция —
включая сам проект.

В response можно вернуть вычисляемое поле:

```java
boolean restricted
```

Оно нужно только для бейджа `Restricted` во frontend. Пользователь не изменяет
его напрямую: значение равно `true`, когда сохранён хотя бы один direct member
или allowed role.

### Обнови форму New project

При открытии формы загрузи параллельно:

```text
workspace members
custom access roles
```

Добавь два multi-select блока с поиском. Создателя и `OWNER` можно не показывать
в списке direct people, потому что они и так сохраняют доступ. `VIEWER` можно
выбрать вручную: он увидит закрытый проект, но останется read-only.

При submit отправь всё одним JSON:

```json
{
  "name": "Mobile application",
  "description": "Release planning",
  "allowedRoleIds": [3, 7],
  "allowedWorkspaceMemberIds": [12, 18]
}
```

Пустые массивы означают общий проект.

### Убери task permissions из пользовательских ролей проекта

### Зачем

Сейчас `ProjectPermission` содержит разрешения вроде `CREATE_TASK` и
`CHANGE_TASK_STATUS`. Из-за этого участник с ролью `MEMBER` может неожиданно не
получить право создать задачу, если ему назначили ограниченную project-роль.

Это противоречит новому правилу: все участники, кроме `VIEWER`, могут создавать
задачи, а затем менять созданные или взятые ими задачи.

### Что изменить

Открой:

```text
src/main/java/collabdesk/project/role/entity/ProjectPermission.java
```

Оставь только `EDIT_PROJECT`.

Во frontend открой:

```text
frontend/src/App.jsx
```

В массиве `PROJECT_PERMISSIONS` тоже оставь только настройку `EDIT_PROJECT`.
Иначе UI будет предлагать разрешения, которые backend больше не использует.

### Не изменяй старую миграцию

Не редактируй `V8__create_custom_access_roles.sql`. Она уже могла примениться к
локальной базе. Все изменения этого этапа собери в новой миграции:

```text
src/main/resources/db/migration/V10__project_grants_and_task_activity.sql
```

В начале миграции удали старые значения:

```sql
DELETE FROM access_role_permissions
WHERE permission IN (
    'CREATE_TASK',
    'EDIT_TASK',
    'CHANGE_TASK_STATUS',
    'CHANGE_TASK_VISIBILITY'
);
```

Затем замени check constraint так, чтобы он разрешал только `EDIT_PROJECT`.

### Как проверить шаг

- форма создания project-роли показывает только `Edit project`;
- создание роли с `EDIT_PROJECT` работает;
- старые task permissions после миграции отсутствуют в базе;
- назначение project-роли не запрещает `MEMBER` создавать задачи.

### Кто управляет людьми и project-ролями

Создавать, изменять и удалять пользовательские project-роли могут `OWNER` и
`ADMIN`. Они же могут:

- добавить конкретного workspace member в проект, после чего проект
  автоматически станет закрытым;
- удалить обычного участника из проекта;
- назначить или снять custom role у workspace member;
- добавить или убрать custom role из allow-list проекта;
- назначить исполнителя задачи.

`MEMBER` этого делать не может. Его самостоятельное действие — только `Take
task`, которое назначает его самого на свободную задачу.

При manager-операциях сохрани owner protection. Если целевой участник имеет
workspace role `OWNER`, `ADMIN` не может удалить его из проекта или изменить
его назначения. Сам `OWNER` при этом может исправить действие администратора.

Не нужно делать неизменяемым каждый проект или задачу, которых когда-либо
коснулся владелец: это остановит совместную работу. Защищается сам владелец и
owner-only настройки, а изменения рабочих объектов становятся прозрачными за
счёт журнала действий.

---

## 3. Раздели создание, изменение и назначение задачи

### Зачем

Не нужно размазывать правила по controller и frontend. Backend должен сам
отвечать на три разных вопроса:

```text
может ли пользователь создать задачу?
может ли пользователь изменить эту конкретную задачу?
может ли пользователь назначить другого человека?
```

### Проверка изменения конкретной задачи

В `TaskAccessService` добавь метод, который после обычной проверки доступа
разрешает изменение только нужным пользователям:

```java
public AccessibleTask requireManageableTask(
        Long workspaceId,
        Long projectId,
        Long taskId,
        Long currentUserId
) {
    AccessibleTask access = requireAccessibleTask(
            workspaceId,
            projectId,
            taskId,
            currentUserId
    );
    WorkspaceRole role = access.projectAccess().membership().getRole();
    if (role == WorkspaceRole.VIEWER) {
        throw new WorkspaceOperationForbiddenException(
                "Viewer has read-only access"
        );
    }

    if (role == WorkspaceRole.OWNER || role == WorkspaceRole.ADMIN) {
        return access;
    }

    boolean creator = access.task().getCreatedBy().getId()
            .equals(currentUserId);
    boolean assignee = taskAssigneeRepository
            .existsByTask_IdAndProjectMember_WorkspaceMember_User_Id(
                    taskId,
                    currentUserId
            );
    if (creator || assignee) {
        return access;
    }

    throw new WorkspaceOperationForbiddenException(
            "Only the task creator or assignee can change it"
    );
}
```

Он делает три вещи:

1. Проверяет, что workspace, project и task действительно доступны.
2. Даёт `OWNER` и `ADMIN` управлять доступной задачей.
3. Для `MEMBER` разрешает изменение только своей или взятой задачи.

### Где использовать

В `TaskService` используй `requireManageableTask(...)` для:

- редактирования названия и описания;
- смены статуса;
- смены видимости.

После этого удали из этих методов проверки:

```text
EDIT_TASK
CHANGE_TASK_STATUS
CHANGE_TASK_VISIBILITY
```

Не разрешай обычному `MEMBER` менять чужую задачу только потому, что он видит её
на общей доске. Видимость и право изменения — разные вещи.

### Отдельно про создание задачи

При создании `Task` ещё не существует, поэтому `requireManageableTask(...)`
вызвать нельзя.

Сначала получи `AccessibleProject`, а затем проверь workspace role:

```java
private void requireTaskContributor(AccessibleProject access) {
    if (access.membership().getRole() == WorkspaceRole.VIEWER) {
        throw new WorkspaceOperationForbiddenException(
                "Viewer has read-only access"
        );
    }
}
```

В `create(...)` последовательность должна быть такой:

```text
requireAccessibleProject
-> requireTaskContributor
-> new Task(...)
-> taskRepository.save(...)
```

### Раздели `Take task` и `Assign`

Это два разных бизнес-действия.

`Take task` означает:

```text
MEMBER нажимает кнопку
-> backend находит его ProjectMember
-> проверяет, что задача свободна
-> назначает текущего пользователя исполнителем
```

`Assign` означает:

```text
OWNER или ADMIN выбирает другого участника
-> backend проверяет manager role
-> назначает выбранного ProjectMember
```

Обычному `MEMBER` не показывай список всех людей для назначения. Он может только:

- взять свободную задачу на себя;
- отказаться от задачи, если сам является её исполнителем.

Лучше сделать отдельные endpoint:

```http
PUT    /tasks/{taskId}/claim
DELETE /tasks/{taskId}/claim
```

А существующий endpoint оставить для владельца и администратора:

```http
PUT /tasks/{taskId}/assignee
```

Для `/assignee` продолжай использовать manager-проверку. Для `/claim` backend
сам определяет текущего пользователя по session — клиент не передаёт чужой
`projectMemberId`.

Если два участника одновременно пытаются взять одну задачу, успешно назначиться
должен только один. Уникальное ограничение `task_assignees_task_uk` уже не даёт
создать двух исполнителей, но конфликт следует преобразовать в понятный
`409 Conflict`: `Task has already been claimed`.

---

## 4. Покажи автора проекта и задачи

### Зачем

В сущностях `Project` и `Task` поле `createdBy` уже существует. Новую таблицу
для автора создавать не нужно. Нужно только вернуть понятное имя в API.

### TaskResponse

Добавь в `TaskResponse`:

```java
Long createdById,
String createdByDisplayName,
```

В `TaskResponseMapper` заполни поля:

```java
task.getCreatedBy().getId(),
task.getCreatedBy().getDisplayName(),
```

### ProjectResponse

Добавь в `ProjectResponse`:

```java
Long createdById,
String createdByDisplayName,
```

Заполни их в `ProjectService.toResponse(...)`.

Такие же поля добавь в `ProjectAccessOverviewResponse` и заполни в
`WorkspaceProjectAccessOverviewQueryService`. Это важно, потому что список
проектов frontend получает именно через access overview, который кешируется в
Redis.

### Что показать во frontend

На карточке проекта:

```text
Created by Anna
```

На карточке задачи:

```text
Created by Max
```

Используй `createdByDisplayName`. `createdById` нужен для логики и тестов, но
пользователю число показывать не нужно.

---

## 5. Создай лёгкую модель журнала задач

Создай package:

```text
collabdesk.task.activity
├── dto
├── entity
├── repository
└── service
```

### Тип действия

Создай `TaskActivityType`:

```java
public enum TaskActivityType {
    CREATED,
    EDITED,
    STATUS_CHANGED,
    CLAIMED,
    RELEASED,
    ASSIGNEE_CHANGED,
    VISIBILITY_CHANGED
}
```

`CLAIMED` и `RELEASED` относятся к самостоятельным действиям `MEMBER`.
`ASSIGNEE_CHANGED` используется, когда `OWNER` или `ADMIN` назначил другого
человека.

### Сущность TaskActivity

Сущность должна хранить:

```text
id          идентификатор записи
task        к какой задаче относится запись
actor       кто выполнил действие
type        что произошло
oldValue    что было до изменения
newValue    что стало после изменения
createdAt   когда произошло действие
```

`oldValue` и `newValue` могут быть `null`. Например, для `CREATED` старого
значения нет, а для обычного `EDITED` необязательно сохранять весь текст задачи.

Не сохраняй в журнал пароли, session id, CSRF token или полный JSON запроса.

### Таблица

В этой же миграции V10 создай таблицу `task_activities`:

```sql
CREATE TABLE task_activities (
    id BIGINT AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    activity_type VARCHAR(30) NOT NULL,
    old_value VARCHAR(150),
    new_value VARCHAR(150),
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT task_activities_task_fk
        FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,

    CONSTRAINT task_activities_actor_fk
        FOREIGN KEY (actor_id) REFERENCES users(id),

    INDEX task_activities_task_created_at_idx (
        task_id,
        created_at,
        id
    )
);
```

`ON DELETE CASCADE` означает: если задача удалена, её история тоже удаляется.

---

## 6. Добавь repository, response и service журнала

### Repository

Нужен один основной запрос:

```java
@EntityGraph(attributePaths = "actor")
List<TaskActivity> findByTask_IdOrderByCreatedAtDescIdDesc(Long taskId);
```

Новые записи будут показаны первыми. `EntityGraph` сразу загружает автора и не
создаёт отдельный SQL-запрос для каждого пункта истории.

### Response

Создай `TaskActivityResponse`:

```java
public record TaskActivityResponse(
        Long id,
        TaskActivityType type,
        Long actorId,
        String actorDisplayName,
        String oldValue,
        String newValue,
        Instant createdAt
) {
}
```

### Service

В `TaskActivityService` нужны два сценария:

```text
record(...)       сохранить новое действие
findForTask(...)  проверить доступ и вернуть историю
```

Перед чтением истории обязательно вызови:

```java
taskAccessService.requireAccessibleTask(...);
```

Иначе пользователь сможет подобрать `taskId` и прочитать историю закрытой
задачи.

---

## 7. Записывай события внутри тех же транзакций

### Почему внутри транзакции

Если изменение задачи откатилось, запись в журнале тоже должна откатиться.
Нельзя получить историю «Max завершил задачу», если статус в базе остался
`TODO`.

Поэтому `taskActivityService.record(...)` вызывается из существующих
`@Transactional`-методов.

### Создание

После `taskRepository.save(task)` запиши:

```text
type = CREATED
oldValue = null
newValue = TODO
actor = текущий пользователь
```

### Смена статуса

До изменения сохрани старый статус:

```java
TaskStatus oldStatus = task.getStatus();
```

Если статус действительно изменился, запиши `STATUS_CHANGED`:

```text
oldValue = TODO
newValue = IN_PROGRESS
```

Повторный запрос `DONE -> DONE` не должен создавать новую запись журнала.

### Назначение

До удаления старого `TaskAssignee` сохрани имя прежнего исполнителя. После
назначения запиши `ASSIGNEE_CHANGED`:

```text
null -> Max
Max -> Anna
Anna -> null
```

В `actor` записывается пользователь, который нажал кнопку назначения. В
`newValue` записывается пользователь, которого назначили. Это могут быть разные
люди.

Для отдельных claim-endpoint используй более точные события:

```text
CLAIMED   actor=Max, newValue=Max
RELEASED  actor=Max, oldValue=Max
```

### Редактирование и видимость

Для изменения текста достаточно события `EDITED` без полного старого и нового
описания. Для видимости сохрани:

```text
PROJECT -> ASSIGNEES
ASSIGNEES -> PROJECT
```

Если снятие последнего исполнителя автоматически возвращает видимость к
`PROJECT`, это тоже запиши отдельным `VISIBILITY_CHANGED`.

---

## 8. Добавь endpoint истории

В `TaskController` добавь:

```http
GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}/activities
```

Метод возвращает:

```json
[
  {
    "id": 12,
    "type": "STATUS_CHANGED",
    "actorId": 3,
    "actorDisplayName": "Max",
    "oldValue": "TODO",
    "newValue": "IN_PROGRESS",
    "createdAt": "2026-08-29T12:00:00Z"
  },
  {
    "id": 11,
    "type": "CREATED",
    "actorId": 2,
    "actorDisplayName": "Anna",
    "oldValue": null,
    "newValue": "TODO",
    "createdAt": "2026-08-29T11:50:00Z"
  }
]
```

Не добавляй всю историю в каждый `TaskResponse`. Иначе обычная загрузка доски
будет постоянно читать журнал для всех задач. Историю лучше загружать только
после нажатия кнопки `Activity`.

---

## 9. Подключи историю во frontend

### API

В `frontend/src/api/taskApi.js` добавь:

```js
export async function getTaskActivities(workspaceId, projectId, taskId) {
  const response = await apiFetch(
    `${tasksUrl(workspaceId, projectId)}/${taskId}/activities`,
    { credentials: 'same-origin' },
  )

  if (!response.ok) {
    throw await createApiError(response, 'Unable to load task activity.')
  }

  return response.json()
}
```

### Кнопки и права

Для создания задачи достаточно проверить read-only роль:

```js
const canCreateTask = workspace.role !== 'VIEWER'
```

Для каждой существующей задачи вычисли отдельные возможности:

```js
const isManager = ['OWNER', 'ADMIN'].includes(workspace.role)
const isCreator = task.createdById === user.id
const isAssignee = task.assignee?.userId === user.id
const canManageTask = isManager || isCreator || isAssignee
const canAssignTask = isManager
const canClaimTask = workspace.role === 'MEMBER' && !task.assignee
const canReleaseTask = workspace.role === 'MEMBER' && isAssignee
```

Используй их так:

- `canCreateTask` — кнопка `+ New task`;
- `canManageTask` — редактирование, статус и видимость;
- `canAssignTask` — picker со списком участников;
- `canClaimTask` — кнопка `Take task`;
- `canReleaseTask` — кнопка `Release task`.

`hasPermission('EDIT_PROJECT')` оставь только для настройки проекта.

Frontend-проверки нужны для удобного интерфейса, но не заменяют backend-
авторизацию. Пользователь может отправить HTTP-запрос вручную.

### Отображение истории

На карточке задачи добавь кнопку:

```text
Activity
```

При первом нажатии загрузи endpoint истории. При повторном нажатии скрой блок.

Примеры текста:

```text
Anna created the task
Max changed status from todo to in progress
Anna assigned Max
Max changed status from in progress to done
```

После изменения статуса или исполнителя закрой открытую историю либо загрузи её
заново. Иначе пользователь увидит старый список до следующего обновления.

---

## 10. Что означали предупреждения Redis

Сообщение:

```text
Redis cache operation failed: operation=get ...
exception=RedisConnectionFailureException
```

означает, что приложение было запущено с профилем `redis`, но не смогло
подключиться к `REDIS_HOST:REDIS_PORT`.

Это не повреждение кеша и не ошибка MySQL. `LenientRedisCacheErrorHandler`
поймал исключение, записал warning и позволил приложению построить response из
MySQL.

### Если backend запущен на компьютере

Используй:

```text
REDIS_HOST=localhost
REDIS_PORT=6379
```

Запусти Redis:

```powershell
docker compose up -d redis
docker compose ps redis
docker compose exec redis redis-cli ping
```

Последняя команда должна вернуть:

```text
PONG
```

### Если backend когда-нибудь будет запущен в Docker Compose

Внутри контейнера `localhost` указывает на сам backend-контейнер. Тогда нужно:

```text
REDIS_HOST=redis
REDIS_PORT=6379
```

Сейчас backend отсутствует в `docker-compose.yaml`, поэтому для запуска из IDE
правильным остаётся `localhost`.

Если после запуска Redis warnings исчезли, текущая проблема решена. Lenient
handler всё равно оставь: Redis является ускорителем и его временное падение не
должно отключать основную работу с MySQL.

---

## 11. Какие тесты обязательно изменить и добавить

### Unit-тесты прав

Проверь:

1. `MEMBER` создаёт задачу без `CREATE_TASK` в project-role.
2. `VIEWER` получает запрет и `taskRepository.save(...)` не вызывается.
3. Автор задачи может её изменить.
4. Текущий assignee может её изменить.
5. Посторонний `MEMBER` видит общую задачу, но не может изменить её.
6. `OWNER` и `ADMIN` могут изменить доступную задачу.
7. Для изменения задачи вызывается `requireManageableTask(...)`.
8. Смена статуса записывает старый и новый статус.
9. Повторная установка того же статуса не добавляет событие.

### Тесты claim и assignment

Проверь отдельно:

1. `MEMBER` может взять свободную задачу на себя.
2. `MEMBER` не может передать в claim чужой user/project-member id.
3. `MEMBER` не может забрать уже занятую задачу.
4. `MEMBER` может освободить только задачу, взятую им самим.
5. `MEMBER` не может вызвать manager endpoint назначения другого человека.
6. `OWNER` и `ADMIN` могут назначить участника проекта.
7. `ADMIN` не может изменить или удалить owner membership.
8. Два одновременных claim не создают двух assignee.

### Integration-тест истории

Сценарий:

```text
создать задачу
-> назначить исполнителя
-> изменить TODO на IN_PROGRESS
-> запросить /activities
```

Если задачу взял сам `MEMBER`, ожидай записи:

```text
STATUS_CHANGED
CLAIMED
CREATED
```

Если исполнителя выбрал `OWNER` или `ADMIN`, вместо `CLAIMED` ожидай
`ASSIGNEE_CHANGED` и проверь отдельно actor и назначенного пользователя.

Также проверь `actorDisplayName`, `oldValue` и `newValue`.

### Тест project-role

Создай роль только с `EDIT_PROJECT`, назначь её обычному `MEMBER` и проверь:

- project settings доступны согласно роли;
- создание задачи всё равно доступно;
- свою задачу и взятую задачу он может менять;
- чужую невзятую задачу он изменить не может;
- `VIEWER` по-прежнему не может ничего изменять.

### Тест автоматического закрытия проекта

Проверь отдельным integration-тестом:

1. Новый проект без явных назначений видят все workspace members.
2. Автоматическая запись создателя в `project_members` не закрывает проект.
3. После добавления первого другого участника проект становится закрытым.
4. Закрытый проект видят `OWNER`, `ADMIN`, его создатель и назначенный участник.
5. Неназначенный `MEMBER` и `VIEWER` не видят закрытый проект.
6. После удаления последних явных людей и ролей проект снова становится общим.
7. После каждого назначения Redis overview очищается после commit.
8. При rollback доступ и кеш не должны измениться.

### Тест создания проекта с начальным доступом

Проверь запрос `POST /projects` отдельно:

1. Пустые `allowedRoleIds` и `allowedWorkspaceMemberIds` создают общий проект.
2. Выбранный человек получает доступ сразу в response после создания.
3. Выбранная custom role открывает проект всем workspace members с этой ролью.
4. Участник без выбранной роли не видит проект.
5. Одновременный выбор роли и человека работает без дубликатов.
6. Role ID из другого workspace возвращает ошибку и проект не сохраняется.
7. Workspace member ID из другого workspace возвращает ошибку и проект не
   сохраняется.
8. `MEMBER` не может создать проект или передать allow-list напрямую.
9. `OWNER` и `ADMIN` могут создать проект с initial access.
10. До commit закрытый проект не появляется в кеше как общий.

### Команды проверки

```powershell
.\mvnw.cmd test
```

```powershell
cd frontend
npm.cmd run lint
npm.cmd run build
```

---

## 12. Ручная проверка через интерфейс

Проверь по порядку:

1. Запусти MySQL и Redis.
2. Запусти backend с профилем `redis`.
3. Войди владельцем workspace.
4. Добавь пользователя с ролью `MEMBER`.
5. Добавь пользователя с ролью `VIEWER`.
6. Создай custom role `Reviewer` и назначь её пользователю `MEMBER`.
7. Нажми `+ New project`, ничего не выбирай и создай общий проект.
8. Войди как `MEMBER` и `VIEWER`: оба должны видеть общий проект.
9. Создай второй проект и сразу выбери `Reviewer` в `Allowed roles`.
10. Убедись, что этот проект видит `MEMBER`, но не видит `VIEWER`.
11. Создай третий проект и выбери `MEMBER` вручную в `Allowed people`.
12. Убедись, что он также закрыт сразу, без промежуточного общего состояния.
13. Удали последние grants у второго проекта и проверь, что он снова стал общим.
14. Верни role grant для дальнейшей проверки задач.
15. Убедись, что возле проекта показан его создатель.
16. Войди как `MEMBER` и создай задачу.
17. Убедись, что возле задачи показан её создатель.
18. Нажми `Take task` и возьми задачу себе.
19. Переведи её из `TODO` в `IN_PROGRESS`, затем в `DONE`.
20. Открой `Activity` и проверь имена и порядок действий.
21. Создай вторую задачу другим пользователем и не назначай её текущему.
22. Убедись, что `MEMBER` видит её, но не может редактировать или двигать.
23. Войди как `ADMIN` и назначь исполнителя через picker.
24. Убедись, что `ADMIN` не может изменить или удалить `OWNER`.
25. В открытом проекте войди как `VIEWER` и проверь read-only интерфейс.
26. Попробуй отправить POST вручную и убедись, что backend возвращает `403`.
27. Останови Redis и обнови страницу.
28. Убедись, что данные продолжают загружаться из MySQL, а в лог попал warning.
29. Снова запусти Redis и убедись, что warning больше не повторяется.

---

## 13. Что искать в видеороликах и статьях

Для этого этапа полезны не общие видео про Spring, а конкретные запросы:

```text
Spring Boot audit log entity user action history example
Spring Data JPA activity log one to many example
Spring Security service layer authorization workspace role
Spring Boot authorization read only role service layer
Spring Data JPA EntityGraph avoid N+1 example
Flyway add audit table migration MySQL
React fetch activity log on button click
React expandable activity timeline component
RedisConnectionFailureException Spring Boot Docker localhost
Docker Compose Redis healthcheck redis-cli ping
Spring Cache CacheErrorHandler Redis fallback
```

На русском:

```text
Spring Boot журнал действий пользователей пример
Spring Data JPA аудит изменений сущности
Spring Boot проверка ролей на уровне service
Flyway создание таблицы истории изменений MySQL
React журнал активности задачи
Spring Redis ошибка подключения Docker localhost
```

Ищи материалы, где показаны похожие методы: отдельная audit entity, запись
события внутри транзакции, `EntityGraph`, service-level authorization и ленивое
получение истории отдельным endpoint.

---

## 14. Когда этап считается законченным

Этап готов, если одновременно выполняется всё:

- task permissions удалены из пользовательских project-ролей;
- `MEMBER` может создавать задачи;
- `MEMBER` может менять только созданные или взятые им задачи;
- `MEMBER` может взять свободную задачу, но не назначить другого человека;
- `OWNER` и `ADMIN` могут назначать людей и project-роли;
- при нажатии `+ New project` сразу доступны выбор allowed roles и allowed people;
- проект и выбранные grants сохраняются одним запросом и одной транзакцией;
- неправильный role/member ID откатывает создание проекта целиком;
- `ADMIN` не может изменить или удалить `OWNER`;
- `VIEWER` только читает;
- отдельного поля, endpoint и селектора project visibility больше нет;
- проект без явных назначений доступен всем участникам workspace;
- первое явное назначение человека или project-роли автоматически закрывает проект;
- закрытый проект доступен managers, создателю, явно назначенным людям и
  участникам с одной из allowed roles;
- удаление последних явных назначений автоматически снова открывает проект;
- доступ к закрытому проекту нельзя обойти через task endpoint;
- API возвращает имена создателей проекта и задачи;
- журнал сохраняет автора, тип действия, значения и время;
- история недоступна пользователю без доступа к задаче;
- frontend показывает автора и раскрываемый блок `Activity`;
- миграция V10 применяется на чистой и существующей базе;
- все backend-тесты проходят;
- frontend lint и build проходят;
- при выключенном Redis приложение работает через MySQL;
- при включённом Redis warnings подключения исчезают.

После этого отдельным этапом можно решить, нужен ли настоящий архив задач или
архив проектов с собственным журналом `ACTIVE <-> ARCHIVED`.
