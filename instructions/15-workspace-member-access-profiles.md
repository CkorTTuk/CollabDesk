# Этап 15. Компактные профили участников и workspace access overview

## Результат этапа

После этого этапа список участников workspace остаётся компактным:

- в строке постоянно видны только avatar, имя, email и системная роль;
- дата вступления и другие второстепенные сведения не занимают место в таблице;
- клик по avatar или имени открывает компактный профиль участника;
- в профиле видны системная роль, проекты, кастомные роли и прямые назначения;
- владелец или администратор может перейти из профиля к редактированию project
  access;
- карточки проектов получают назначения одним агрегированным backend-запросом,
  а не отдельным HTTP-запросом для каждого проекта.

Этап не добавляет новые permissions и не меняет модель ролей из этапа 14.

---

## 1. Границы модели

Сохраняй существующее разделение:

```text
WorkspaceMember
└── системная WorkspaceRole

ProjectMember
└── назначения AccessRole внутри конкретного Project
```

Компактный профиль — это read model для интерфейса. Не создавай отдельную
`MemberProfile` entity и не дублируй данные пользователя в новой таблице.

Не связывай `AccessRole` напрямую с `WorkspaceMember`. Источником назначения
по-прежнему остаётся:

```text
WorkspaceMember -> ProjectMember -> ProjectMemberRole -> AccessRole
```

## 2. Что показывать постоянно

В строке участника workspace оставь:

- avatar или initials;
- display name;
- email;
- отцентрированный badge `WorkspaceRole`.

Не показывай постоянно:

- `joinedAt`;
- user id;
- project member id;
- полный список permissions;
- технические timestamps;
- длинное описание каждого permission.
- названия проектов и project access chips;
- кнопки управления участником или project access.

Эти сведения не должны увеличивать высоту каждой строки.
Управление проектами, ролями участника и удалением открывай только из его
компактного профиля.

## 3. Компактный профиль

Клик по avatar или display name открывает popover на desktop и bottom sheet или
dialog на узких экранах.

Профиль содержит:

```text
Avatar
Display name
Email
Workspace role

Project access
├── CollabDesk MVP
│   ├── Backend Developer
│   └── Reviewer
├── Marketing Site
│   └── Direct access
└── Internal Docs
    └── No access
```

Проекты без доступа можно скрыть из обычного просмотра. Для менеджера при
переходе в режим редактирования показывай все проекты.

Если у `ProjectMember` нет кастомных ролей, отображай:

```text
Direct access
```

Для `MEMBER` это означает fallback на базовый набор permissions. Для
`OWNER`/`ADMIN` продолжает действовать системный override, для `VIEWER` —
read-only ceiling.

## 4. Поведение профиля

- одновременно открыт только один профиль;
- Escape закрывает профиль;
- клик вне popover закрывает его;
- после закрытия focus возвращается на avatar или имя;
- все интерактивные элементы доступны с клавиатуры;
- dialog имеет понятные `aria-labelledby` и `aria-describedby`;
- фон страницы блокируется только для мобильного modal-варианта.

Не добавляй отдельную страницу пользователя на этом этапе.

## 5. Workspace access overview API

Добавь агрегированный endpoint:

```http
GET /api/v1/workspaces/{workspaceId}/project-access-overview
```

Endpoint возвращает проекты workspace и назначения их участников одним
response. Он нужен, чтобы frontend не выполнял:

```text
GET projects
GET project 1 members
GET project 2 members
GET project 3 members
...
```

Доступ:

```text
OWNER  -> полный overview
ADMIN  -> полный overview
MEMBER -> только доступные ему проекты и безопасная member summary
VIEWER -> только доступные ему проекты и безопасная member summary
```

Не раскрывай назначения restricted-проектов пользователю, которому проект
недоступен.

## 6. DTO

Создай DTO без timestamps:

```java
public record WorkspaceProjectAccessOverviewResponse(
        List<ProjectAccessOverviewResponse> projects
) {
}
```

```java
public record ProjectAccessOverviewResponse(
        Long projectId,
        String name,
        String description,
        ProjectStatus status,
        ProjectVisibility visibility,
        List<ProjectMemberAccessResponse> members
) {
}
```

```java
public record ProjectMemberAccessResponse(
        Long projectMemberId,
        Long workspaceMemberId,
        Long userId,
        String displayName,
        String email,
        WorkspaceRole workspaceRole,
        List<AccessRoleSummaryResponse> roles
) {
}
```

Не возвращай в overview:

- `joinedAt`;
- `createdAt`;
- `updatedAt`;
- `assignedAt`;
- `createdBy`;
- полный `effectivePermissions`, если он не используется данным экраном.

Permissions выбранной роли уже доступны через workspace role API.

## 7. Repository

Добавь batch-query для всех доступных проектов:

```java
List<ProjectMember> findByProject_IdInOrderByProject_IdAscJoinedAtAsc(
        Collection<Long> projectIds
);
```

Используй `@EntityGraph` или явный fetch plan для:

```text
ProjectMember.workspaceMember
ProjectMember.workspaceMember.user
ProjectMember.project
```

Назначенные роли и permissions загружай существующим batch-механизмом
`ProjectMemberRoleSnapshot`.

Не выполняй repository-запрос отдельно для каждого проекта или участника.

## 8. Service

Создай:

```text
workspace/access/service/WorkspaceProjectAccessOverviewService.java
```

Service должен:

1. проверить membership текущего пользователя;
2. получить доступные проекты существующим `ProjectAccessService`;
3. одним batch-query загрузить `ProjectMember`;
4. одним batch-flow загрузить назначенные роли;
5. сгруппировать участников по `projectId`;
6. вернуть стабильный порядок проектов и участников.

Для `OWNER` и `ADMIN` разрешено возвращать полный workspace overview.

Не вычисляй доступ по названию роли.

## 9. Controller

Создай controller в:

```text
workspace/access/controller/WorkspaceProjectAccessOverviewController.java
```

Endpoint:

```http
GET /api/v1/workspaces/{workspaceId}/project-access-overview
```

Ответы:

```text
200 -> overview
401 -> пользователь не аутентифицирован
403 -> нет доступа к workspace
404 -> workspace не найден
```

GET endpoint не требует CSRF.

## 10. Frontend data flow

На workspace-экране загружай параллельно:

```text
GET /workspaces/{workspaceId}/members
GET /workspaces/{workspaceId}/roles
GET /workspaces/{workspaceId}/project-access-overview
```

Не вызывай `getProjectMembers` для каждой карточки проекта.

Храни overview на уровне workspace page и передавай вниз:

```text
Workspace page
├── Project cards
├── Custom role manager
└── Workspace members
    ├── compact row
    ├── member profile
    └── project access editor
```

После add/replace/remove project membership локально обновляй overview из
response существующего endpoint. При частичной ошибке разрешён повторный fetch
overview.

## 11. Project cards

Карточка проекта показывает:

- status;
- visibility;
- name;
- description;
- уникальные назначенные custom roles;
- людей с direct access;
- количество остальных назначений, если все не помещаются.

Не показывай каждого пользователя, если он представлен кастомной ролью.

Рекомендуемый компактный вид:

```text
Roles   Developer  QA  Reviewer
People  [AS] [MK] +2
```

У role chip должны быть name и workspace color. Не используй цвет как
единственный способ различения.

## 12. Member list

Минимальный читаемый размер основного текста — `12px`. Для display name
используй не меньше `14px`.

Не используй `7px`, `8px` и `9px` для содержательного текста. Такие размеры
допустимы только для декоративных элементов, но не для email, role names,
project names, permissions и кнопок.

На desktop колонка участников может занимать:

```css
minmax(390px, 450px)
```

На ширине, где основная колонка становится слишком узкой, layout переходит в
одну колонку.

## 13. Project access editor

Редактор остаётся на workspace-экране внутри выбранного участника:

1. пользователь выбирает проекты;
2. внутри выбранного проекта назначает custom roles;
3. пустой набор ролей явно обозначается `Direct access`;
4. Save синхронизирует add, replace roles и remove;
5. Cancel не отправляет запросы.

Не возвращай управление участниками на страницу задач.

Страница проекта загружает project members только для:

- вычисления permissions текущего пользователя;
- выбора единственного task assignee;
- отображения task assignee.

## 14. OpenAPI

Задокументируй:

- overview endpoint;
- все overview DTO;
- правила видимости restricted projects;
- отсутствие timestamps;
- примеры custom role и direct access;
- ответы `401`, `403`, `404`.

Обнови `OpenApiIntegrationTest`.

## 15. Tests

### Repository

Проверь:

- batch-загрузку участников нескольких проектов;
- отсутствие участников другого workspace;
- стабильный порядок;
- отсутствие N+1 при загрузке user identity.

### Service

Проверь:

- manager получает полный overview;
- MEMBER не видит недоступный restricted project;
- VIEWER не получает write-возможности;
- custom roles сгруппированы у правильного участника и проекта;
- direct access возвращается как пустой список roles;
- пустой workspace возвращает пустой список projects.

### Integration

Полный HTTP flow:

1. создать workspace;
2. создать public и restricted проекты;
3. создать custom roles;
4. добавить участников с custom role и direct access;
5. запросить overview владельцем;
6. проверить проекты, роли и людей;
7. запросить overview обычным участником;
8. проверить отсутствие недоступного restricted project;
9. проверить `401`, `403` и отсутствие CSRF на GET.

### Frontend

Проверь:

- открытие профиля кликом и клавиатурой;
- закрытие Escape и кликом вне;
- возврат focus;
- отображение custom roles и direct access;
- отсутствие joined date в постоянной строке;
- отсутствие per-project member requests;
- обновление карточек после сохранения access;
- desktop popover и mobile dialog.

## 16. Критерии завершения

- [x] В строке участника нет постоянно отображаемой даты вступления.
- [x] В строке постоянно видны только avatar, имя, email и системная роль.
- [x] Системная роль отцентрирована и имеет читаемый размер.
- [x] Названия проектов и кнопки управления перенесены в профиль.
- [x] Клик по avatar или имени открывает компактный профиль.
- [x] Профиль доступен с клавиатуры.
- [x] Профиль показывает проекты, custom roles и direct access.
- [x] Project access редактируется только на workspace-экране.
- [x] Карточки проектов показывают назначенные роли и direct people.
- [x] Frontend не делает отдельный member request для каждого проекта.
- [x] Restricted projects не раскрываются посторонним участникам.
- [x] Overview не содержит ненужных timestamps.
- [x] OpenAPI обновлён.
- [x] Полный Maven suite проходит.
- [x] Frontend lint и production build проходят.

## Что пока не делать

- не создавать отдельную profile entity;
- не добавлять avatar upload;
- не добавлять presence или online status;
- не показывать last seen;
- не добавлять audit log;
- не добавлять комментарии или private notes о пользователе;
- не добавлять direct `WorkspaceMember -> AccessRole`;
- не добавлять task roles;
- не переносить project access editor обратно внутрь проекта;
- не кешировать overview в Redis до стабилизации response.

Audit log, avatar upload и расширенная страница пользователя могут быть
отдельными будущими этапами.
