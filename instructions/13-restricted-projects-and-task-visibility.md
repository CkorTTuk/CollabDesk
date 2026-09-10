# Этап 13. Ограниченные проекты и приватные задачи

## Результат этапа

После этого этапа проект сможет быть доступен всему workspace либо только своей
команде. Отдельную задачу можно будет показать всей доступной команде проекта
либо только её исполнителям.

Пользовательские роли и конструктор permissions пока не добавляем. На этом этапе
используем существующие роли `OWNER`, `ADMIN`, `MEMBER`, `VIEWER` и уже созданные
сущности `ProjectMember` и `TaskAssignee`.

## 1. Правила доступа

### Видимость проекта

Создай enum:

```text
collabdesk/project/entity/ProjectVisibility.java
```

Значения:

```java
WORKSPACE,
RESTRICTED
```

Правила:

- `WORKSPACE` видят все участники workspace;
- `RESTRICTED` видят только участники проекта;
- `OWNER` и `ADMIN` workspace видят все проекты для администрирования;
- пользователь не из workspace не получает информацию о существовании проекта.

### Видимость задачи

Создай enum:

```text
collabdesk/task/entity/TaskVisibility.java
```

Значения:

```java
PROJECT,
ASSIGNEES
```

Правила:

- `PROJECT` видит каждый, кому доступен проект;
- `ASSIGNEES` видят назначенные исполнители;
- создатель задачи всегда сохраняет доступ;
- `OWNER` и `ADMIN` workspace всегда сохраняют доступ;
- недоступная задача отвечает как `404`, а не `403`.

Так API не раскрывает существование закрытых проектов и задач.

## 2. Миграция V6

Создай:

```text
src/main/resources/db/migration/V6__add_project_and_task_visibility.sql
```

Добавь в `projects`:

```sql
visibility VARCHAR(20) NOT NULL DEFAULT 'WORKSPACE'
```

Добавь check constraint:

```sql
visibility IN ('WORKSPACE', 'RESTRICTED')
```

Добавь в `tasks`:

```sql
visibility VARCHAR(20) NOT NULL DEFAULT 'PROJECT'
```

Добавь check constraint:

```sql
visibility IN ('PROJECT', 'ASSIGNEES')
```

Старые записи должны остаться доступными по прежним правилам:

- старые проекты получают `WORKSPACE`;
- старые задачи получают `PROJECT`.

## 3. Изменение entity

В `Project` добавь:

```java
@Enumerated(EnumType.STRING)
@Column(name = "visibility", nullable = false, length = 20)
private ProjectVisibility visibility;
```

Новый проект по умолчанию создаётся с `WORKSPACE`.

Добавь метод:

```java
public void changeVisibility(ProjectVisibility visibility)
```

В `Task` аналогично добавь `TaskVisibility`, значение по умолчанию `PROJECT` и
метод `changeVisibility`.

Не передавай visibility напрямую в произвольные setters: изменение должно
проходить через service и проверку прав.

## 4. Repository-запросы

В `ProjectMemberRepository` добавь:

```java
boolean existsByProject_IdAndWorkspaceMember_User_Id(
    Long projectId,
    Long userId
);
```

В `TaskAssigneeRepository` добавь:

```java
boolean existsByTask_IdAndProjectMember_WorkspaceMember_User_Id(
    Long taskId,
    Long userId
);
```

Для списков не загружай всё и не фильтруй Java-кодом. Добавь repository query,
который сразу возвращает только доступные проекты:

```text
WORKSPACE projects
OR projects where current user is a project member
OR all projects for OWNER/ADMIN
```

Для списка задач сделай такой же фильтр на уровне БД:

```text
PROJECT tasks
OR ASSIGNEES tasks assigned to current user
OR tasks created by current user
OR all tasks for OWNER/ADMIN
```

Используй `distinct`, потому что join с участниками и исполнителями может
дублировать строки.

## 5. ProjectAccessService

Расширь `requireAccessibleProject`.

Порядок проверки:

1. найти membership пользователя в workspace;
2. найти проект строго внутри указанного workspace;
3. если проект `WORKSPACE` — разрешить чтение;
4. если роль `OWNER` или `ADMIN` — разрешить чтение;
5. проверить наличие `ProjectMember`;
6. иначе вернуть `ProjectNotFoundException`.

`requireWritableProject` должен сначала выполнить ту же проверку видимости, а
затем запретить изменение для `VIEWER`.

Не оставляй старый вариант, где любой workspace member автоматически получает
доступ к restricted-проекту.

## 6. TaskAccessService

Создай отдельный:

```text
collabdesk/task/service/TaskAccessService.java
```

Основные методы:

```java
AccessibleTask requireAccessibleTask(
    Long workspaceId,
    Long projectId,
    Long taskId,
    Long currentUserId
);

AccessibleTask requireWritableTask(
    Long workspaceId,
    Long projectId,
    Long taskId,
    Long currentUserId
);
```

Создай record `AccessibleTask`, содержащий:

```text
Task task
AccessibleProject projectAccess
```

Проверка `ASSIGNEES`:

```text
assigned user
OR task creator
OR workspace OWNER/ADMIN
```

`requireWritableTask` дополнительно запрещает изменение пользователю с ролью
`VIEWER`.

После этого `TaskService.changeStatus` и `TaskAssigneeService.replace` должны
использовать `TaskAccessService`, а не самостоятельно повторять проверки.

## 7. DTO

Добавь `visibility` в:

- `ProjectResponse`;
- `TaskResponse`.

Создай:

```text
UpdateProjectVisibilityRequest
UpdateTaskVisibilityRequest
```

Оба поля обязательны через `@NotNull`.

## 8. Изменение видимости через API

Добавь endpoints:

```http
PATCH /api/v1/workspaces/{workspaceId}/projects/{projectId}/visibility
PATCH /api/v1/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}/visibility
```

Политика изменения:

- видимость проекта меняют только `OWNER` и `ADMIN`;
- видимость задачи меняют `OWNER`, `ADMIN` или создатель задачи;
- `VIEWER` не меняет visibility;
- оба запроса требуют CSRF.

Ответом возвращай обновлённый `ProjectResponse` или `TaskResponse`.

## 9. Важные инварианты

Перед переключением задачи в `ASSIGNEES` проверь:

```text
у задачи есть хотя бы один assignee
```

Если исполнителей нет, верни `409 Conflict` с понятным `ProblemDetail`.

При снятии последнего assignee с приватной задачи выбери безопасное поведение:

```text
автоматически изменить visibility обратно на PROJECT
```

Это проще и безопаснее, чем оставлять задачу в неожиданном состоянии.

Добавь исключение:

```text
TaskVisibilityConflictException
```

и handler с HTTP `409`.

## 10. OpenAPI

Задокументируй:

- оба enum;
- новые поля response DTO;
- оба PATCH endpoint;
- ответы `401`, `403`, `404`, `409`;
- CSRF parameter для изменяющих запросов.

Обнови `OpenApiIntegrationTest`, проверив схемы, enum-значения и paths.

## 11. Frontend

### Проекты

На карточке проекта покажи небольшой индикатор:

```text
Workspace
Restricted
```

Для `OWNER` и `ADMIN` добавь компактный выбор видимости в открытом проекте.

Не показывай скрытые проекты через клиентскую фильтрацию — backend уже должен
возвращать только разрешённый список.

### Задачи

На карточке задачи покажи:

```text
Project team
Assignees only
```

Для разрешённых пользователей добавь переключение visibility рядом с Assign.

Если пользователь выбирает `Assignees only` без исполнителей, покажи текст
ошибки из backend, не заменяя его общим сообщением.

После изменения assignees используй `TaskResponse`, возвращённый сервером: если
последний исполнитель удалён, UI сразу увидит новое значение `PROJECT`.

## 12. Tests

### Entity tests

Проверь default visibility и методы `changeVisibility` у `Project` и `Task`.

### Service tests

Проверь:

- обычный workspace member видит `WORKSPACE` project;
- неучастник не видит `RESTRICTED` project;
- project member видит `RESTRICTED` project;
- `OWNER` и `ADMIN` видят restricted project;
- assignee видит приватную задачу;
- создатель видит приватную задачу;
- посторонний project member не видит приватную задачу;
- `VIEWER` не изменяет задачу;
- нельзя включить `ASSIGNEES` без исполнителей;
- удаление последнего assignee возвращает задачу к `PROJECT`.

### Integration tests

Полный HTTP flow:

1. создать workspace с владельцем и двумя участниками;
2. создать проект;
3. переключить проект в `RESTRICTED`;
4. добавить одного пользователя в project team;
5. проверить, что он видит проект, а второй участник workspace — нет;
6. создать задачу и назначить исполнителя;
7. переключить задачу в `ASSIGNEES`;
8. проверить доступ исполнителя и отсутствие доступа у другого project member;
9. проверить административный доступ OWNER/ADMIN;
10. снять последнего исполнителя и проверить возврат visibility в `PROJECT`.

Также проверь `401`, CSRF, неправильный workspace/project scope и нейтральные
ответы `404`.

## 13. Критерии завершения

- [ ] V6 проходит на пустой и существующей базе.
- [ ] Старые проекты и задачи сохраняют прежнюю видимость.
- [ ] Restricted project не появляется в списке постороннего workspace member.
- [ ] Закрытый project нельзя открыть прямым URL.
- [ ] Приватную задачу видят assignees, creator и workspace managers.
- [ ] Списки фильтруются SQL-запросами без N+1.
- [ ] Изменение visibility защищено ролями и CSRF.
- [ ] OpenAPI содержит новые схемы и endpoints.
- [ ] Полный Maven test suite проходит.
- [ ] Frontend lint и production build проходят.

## Что будет позже

Следующим отдельным этапом можно сделать пользовательские роли и permissions:

```text
workspace role templates
project-specific roles
task roles
permission наборы вместо жёстких проверок enum
```

Их лучше строить уже поверх готовых `ProjectMember`, `TaskAssignee` и правил
видимости. Redis-кеш также стоит добавлять позже, когда появятся стабильные
read-heavy запросы и будет понятно, какие данные действительно нужно кешировать.
