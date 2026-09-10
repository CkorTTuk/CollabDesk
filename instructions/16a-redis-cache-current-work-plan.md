# Этап 16A. Что сейчас сделать с Redis-кешем

## Результат этапа

После выполнения этого плана Redis-кеш из этапа 16 будет не просто настроен,
а полностью подключён к приложению:

- повторный запрос `project-access-overview` будет читать готовый DTO из Redis;
- кеш будет разделён по `workspaceId` и `currentUserId`;
- проверка доступа к workspace будет выполняться даже при cache hit;
- изменения проектов, участников и ролей будут публиковать application event;
- кеш будет очищаться только после успешного commit транзакции;
- при rollback кеш останется без изменений;
- недоступный Redis не будет ломать основной сценарий чтения из MySQL;
- поведение будет подтверждено unit- и integration-тестами.

Этот файл является коротким рабочим маршрутом для текущей реализации. Полное
описание требований и причин решений находится в
`16-redis-project-access-overview-cache.md`.

---

## 1. Что уже создано

В проекте уже есть основа Redis-инфраструктуры:

```text
collabdesk.infrastructure.cache
├── CacheConfiguration
├── LenientRedisCacheErrorHandler
├── WorkspaceProjectAccessChangedEvent
├── WorkspaceProjectAccessChangePublisher
└── WorkspaceProjectAccessCacheInvalidator
```

Также уже подготовлены:

- зависимости Spring Cache и Spring Data Redis;
- Redis service в `docker-compose.yaml`;
- профиль `redis` и `application-redis.properties`;
- cache name `workspaceProjectAccessOverview`;
- prefix `collabdesk:v1:`;
- TTL пять минут;
- JSON-сериализация `WorkspaceProjectAccessOverviewResponse`;
- lenient error handler;
- `ApplicationEventPublisher`;
- `@TransactionalEventListener(AFTER_COMMIT)`;
- broad eviction через `@CacheEvict(allEntries = true)`.

Эти классы пока являются только инфраструктурной заготовкой. Главная текущая
задача — подключить их к read path и ко всем mutation flows.

## 2. Сначала пойми две цепочки

### Чтение

```text
HTTP GET
  -> WorkspaceProjectAccessOverviewService
  -> актуальная проверка membership в MySQL
  -> WorkspaceProjectAccessOverviewCacheService
  -> @Cacheable ищет ключ в Redis
     -> HIT: возвращает сохранённый DTO
     -> MISS: вызывает QueryService, записывает DTO и возвращает его
```

### Изменение данных

```text
@Transactional mutation method
  -> изменяет MySQL
  -> publisher.publish(workspaceId)
  -> WorkspaceProjectAccessChangedEvent
  -> успешный COMMIT
  -> @TransactionalEventListener(AFTER_COMMIT)
  -> @CacheEvict(allEntries = true)
  -> Redis-кеш overview очищен
```

`WorkspaceProjectAccessChangePublisher` использует внутренние события Spring.
Это не Redis Pub/Sub и не отправка сообщения в отдельный сервис.

---

## 3. Сделать кешируемое чтение overview

### Что сейчас происходит

Сейчас всю работу выполняет один класс:

```text
WorkspaceProjectAccessOverviewService
```

Его метод `findForWorkspace(...)` каждый раз:

1. проверяет доступ пользователя;
2. загружает проекты из MySQL;
3. загружает участников проектов;
4. загружает назначенные роли;
5. собирает `WorkspaceProjectAccessOverviewResponse`.

Это правильно, но Redis пока не используется. Даже два одинаковых запроса
подряд снова выполняют одинаковую работу с MySQL.

### Что нужно получить

Нужно разделить работу между тремя классами:

```text
WorkspaceProjectAccessOverviewService
  -> охранник: всегда проверяет доступ

WorkspaceProjectAccessOverviewCacheService
  -> кладовщик: ищет и сохраняет готовый response в Redis

WorkspaceProjectAccessOverviewQueryService
  -> сборщик: строит response из MySQL, когда в Redis ничего нет
```

Такое разделение нужно не для красоты. Аннотация `@Cacheable` работает через
Spring proxy. При cache hit Spring возвращает значение сразу и вообще не входит
в тело кешируемого метода. Поэтому проверку доступа нельзя оставлять внутри
этого метода.

### 3.1. Создать QueryService

Создай файл:

```text
src/main/java/collabdesk/workspace/access/service/
    WorkspaceProjectAccessOverviewQueryService.java
```

Его задача очень простая: всегда построить актуальный overview из MySQL. Этот
класс ничего не знает о Redis.

#### Что перенести

Открой существующий `WorkspaceProjectAccessOverviewService`. Перенеси в новый
QueryService:

- поля `projectAccessService`, `projectMemberRepository` и
  `projectMemberRoleService`;
- соответствующие параметры конструктора;
- текущее тело метода `findForWorkspace(...)`;
- приватный метод `toProjectResponse(...)`;
- приватный метод `toMemberResponse(...)`;
- необходимые imports.

В результате новый класс должен иметь примерно такую форму:

```java
@Service
public class WorkspaceProjectAccessOverviewQueryService {

    private final ProjectAccessService projectAccessService;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectMemberRoleService projectMemberRoleService;

    public WorkspaceProjectAccessOverviewQueryService(
            ProjectAccessService projectAccessService,
            ProjectMemberRepository projectMemberRepository,
            ProjectMemberRoleService projectMemberRoleService
    ) {
        this.projectAccessService = projectAccessService;
        this.projectMemberRepository = projectMemberRepository;
        this.projectMemberRoleService = projectMemberRoleService;
    }

    @Transactional(readOnly = true)
    public WorkspaceProjectAccessOverviewResponse findForWorkspace(
            Long workspaceId,
            Long currentUserId
    ) {
        // Сюда переносится существующий код построения overview.
    }

    // Сюда переносятся два существующих mapping-метода.
}
```

Не переписывай алгоритм построения response. На этом шаге ты только переносишь
уже работающий код в отдельный класс.

#### Зачем это нужно

Когда Redis пустой, CacheService должен обратиться к кому-то за актуальными
данными. Этим источником будет QueryService:

```text
Redis пуст
  -> CacheService вызывает QueryService
  -> QueryService читает MySQL
  -> CacheService сохраняет полученный response в Redis
```

#### Как проверить этот маленький шаг

После переноса проект может временно не компилироваться, пока старый Service ещё
не подключён к новому. Это нормально. Сразу переходи к пунктам 3.2 и 3.3, затем
запусти тесты.

### 3.2. Создать CacheService

Создай рядом второй файл:

```text
WorkspaceProjectAccessOverviewCacheService.java
```

Его единственная задача — применить `@Cacheable` и при необходимости вызвать
QueryService.

```java
package collabdesk.workspace.access.service;

import collabdesk.project.dto.WorkspaceProjectAccessOverviewResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import static collabdesk.infrastructure.cache.CacheConfiguration
        .WORKSPACE_PROJECT_ACCESS_OVERVIEW;

@Service
public class WorkspaceProjectAccessOverviewCacheService {

    private final WorkspaceProjectAccessOverviewQueryService queryService;

    public WorkspaceProjectAccessOverviewCacheService(
            WorkspaceProjectAccessOverviewQueryService queryService
    ) {
        this.queryService = queryService;
    }

    @Cacheable(
            cacheNames = WORKSPACE_PROJECT_ACCESS_OVERVIEW,
            key = "#workspaceId + ':' + #currentUserId",
            unless = "#result == null"
    )
    public WorkspaceProjectAccessOverviewResponse findForWorkspace(
            Long workspaceId,
            Long currentUserId
    ) {
        return queryService.findForWorkspace(workspaceId, currentUserId);
    }
}
```

#### Что делает `@Cacheable` простыми словами

Представим вызов:

```java
cacheService.findForWorkspace(42L, 7L);
```

Spring выполняет следующую последовательность:

1. Берёт cache name `workspaceProjectAccessOverview`.
2. По выражению `key` строит логический ключ `42:7`.
3. Ищет этот ключ в Redis.
4. Если ключ найден, возвращает готовый DTO. Строка с вызовом QueryService не
   выполняется.
5. Если ключ не найден, выполняет метод и вызывает QueryService.
6. Полученный DTO сохраняет в Redis под ключом `42:7`.
7. Возвращает DTO вызывающему коду.

С prefix из `CacheConfiguration` полный ключ станет таким:

```text
collabdesk:v1:workspaceProjectAccessOverview::42:7
```

#### Зачем в ключе два id

`workspaceId` отвечает на вопрос «для какого workspace построен response».
`currentUserId` отвечает на вопрос «что именно имеет право видеть этот
пользователь».

Например:

```text
collabdesk:v1:workspaceProjectAccessOverview::42:7
collabdesk:v1:workspaceProjectAccessOverview::42:9
```

Это два разных ключа. Пользователь `7` может быть OWNER и видеть все проекты, а
пользователь `9` может быть MEMBER и не видеть restricted-проект.

Если убрать `currentUserId`, оба пользователя начнут разделять один response.
Это уже не просто ошибка кеша, а возможная утечка данных.

#### Что означает `unless`

```java
unless = "#result == null"
```

Если метод вернул `null`, Spring не станет сохранять его в Redis. Сейчас сервис
обычно возвращает DTO, а не `null`, но эта защита явно фиксирует нужное
поведение.

### 3.3. Переделать старый Service в простой facade

Теперь вернись в:

```text
WorkspaceProjectAccessOverviewService.java
```

После переноса старого кода этот класс должен стать маленьким. Оставь в нём две
зависимости:

```java
private final WorkspaceAccessService workspaceAccessService;
private final WorkspaceProjectAccessOverviewCacheService cacheService;
```

Полная идея класса:

```java
@Service
public class WorkspaceProjectAccessOverviewService {

    private final WorkspaceAccessService workspaceAccessService;
    private final WorkspaceProjectAccessOverviewCacheService cacheService;

    public WorkspaceProjectAccessOverviewService(
            WorkspaceAccessService workspaceAccessService,
            WorkspaceProjectAccessOverviewCacheService cacheService
    ) {
        this.workspaceAccessService = workspaceAccessService;
        this.cacheService = cacheService;
    }

    @Transactional(readOnly = true)
    public WorkspaceProjectAccessOverviewResponse findForWorkspace(
            Long workspaceId,
            Long currentUserId
    ) {
        workspaceAccessService.requireMember(workspaceId, currentUserId);
        return cacheService.findForWorkspace(workspaceId, currentUserId);
    }
}
```

#### Зачем нужна отдельная проверка перед кешем

Рассмотрим опасный сценарий:

```text
1. Пользователь состоял в workspace и получил overview.
2. Overview сохранился в Redis на пять минут.
3. Пользователя удалили из workspace.
4. Пользователь повторил запрос до истечения TTL.
```

Если `@Cacheable` стоит на методе с проверкой доступа, Spring может найти кеш и
вернуть его раньше, чем выполнится `requireMember(...)`.

В нашей схеме сначала всегда вызывается facade:

```text
requireMember(...)
  -> пользователь удалён
  -> выбрасывается ошибка доступа
  -> CacheService вообще не вызывается
```

На cache miss проверка membership сейчас может выполниться ещё раз внутри
`ProjectAccessService.findAccessibleProjects(...)`. Это лишний запрос, но для
первой версии он допустим: сначала важнее получить понятную и безопасную схему.

### 3.4. Почему нельзя оставить всё в одном классе

Так делать не нужно:

```java
public Response findForWorkspace(...) {
    requireMember(...);
    return this.findCached(...);
}

@Cacheable(...)
private Response findCached(...) {
    // ...
}
```

Причины:

- private-метод не проходит через Spring proxy;
- вызов через `this` остаётся внутри того же объекта;
- `@Cacheable` в таком варианте может вообще не сработать.

Вызов должен перейти из одного Spring bean в другой:

```text
OverviewService bean -> CacheService bean
```

### 3.5. Что проверить после шага 3

Сначала запусти обычные тесты без Redis:

```powershell
./mvnw.cmd test
```

Без профиля `redis` CacheService будет работать как обычный Java delegate:

```text
OverviewService -> CacheService -> QueryService -> MySQL
```

Это ожидаемо. Приложение должно уметь запускаться без Redis.

После этого проверь код глазами:

- [ ] QueryService содержит старый алгоритм сборки overview.
- [ ] CacheService содержит `@Cacheable`.
- [ ] Старый OverviewService сначала вызывает `requireMember`.
- [ ] Controller по-прежнему вызывает OverviewService, а не CacheService.
- [ ] В controller не появилось кода Redis.

### Что посмотреть по этому шагу

```text
Spring Boot Redis @Cacheable example
Spring Cache abstraction cache hit cache miss
Spring @Cacheable self invocation proxy not working
Spring Cache SpEL key multiple parameters
cache aside pattern Redis Java
```

---

## 4. Сообщать кешу об изменениях данных

### Какая сейчас проблема

После шага 3 чтение будет кешироваться на пять минут. Но представь:

```text
1. Пользователь открыл overview.
2. Response попал в Redis.
3. Администратор изменил visibility проекта.
4. Пользователь снова открыл overview.
```

Если ничего не сделать, Redis вернёт старый response. TTL исправит ситуацию
только через несколько минут. Поэтому после изменения данных старый кеш нужно
удалить сразу.

### Кто за что отвечает

В проекте уже есть три маленькие части:

```text
WorkspaceProjectAccessChangePublisher
  -> создаёт и публикует событие

WorkspaceProjectAccessChangedEvent
  -> переносит workspaceId

WorkspaceProjectAccessCacheInvalidator
  -> слушает событие и удаляет кеш
```

Mutation service не должен самостоятельно знать Redis-команды. Он только
говорит: «данные overview для workspace 42 изменились».

### 4.1. Как добавить publisher в один service

Разберём на примере `ProjectService`.

Добавь import:

```java
import collabdesk.infrastructure.cache
        .WorkspaceProjectAccessChangePublisher;
```

Добавь поле:

```java
private final WorkspaceProjectAccessChangePublisher accessChangePublisher;
```

Добавь параметр конструктора и присвой его полю:

```java
public ProjectService(
        ProjectRepository projectRepository,
        WorkspaceAccessService workspaceAccessService,
        ProjectMemberRepository projectMemberRepository,
        ProjectAccessService projectAccessService,
        ProjectPermissionService projectPermissionService,
        WorkspaceProjectAccessChangePublisher accessChangePublisher
) {
    // Существующие присваивания.
    this.accessChangePublisher = accessChangePublisher;
}
```

Затем в изменяющем методе добавь:

```java
accessChangePublisher.publish(workspaceId);
```

Пример для `changeVisibility(...)`:

```java
Project project = access.project();
project.changeVisibility(visibility);
accessChangePublisher.publish(workspaceId);
return toResponse(project);
```

Publisher вызывается после того, как бизнес-проверки прошли и изменение было
сделано. Но он всё ещё вызывается внутри метода с `@Transactional`.

### 4.2. Куда именно добавить publisher

#### `ProjectService`

Вызови `publish(workspaceId)` в:

- `create(...)` — новый проект должен появиться в overview;
- `changeVisibility(...)` — изменение visibility влияет на то, кто видит
  проект.

В будущем то же потребуется после изменения name, description или status.

#### `ProjectMemberService`

Вызови publisher в:

- `add(...)` — в overview появился участник проекта;
- `replaceRoles(...)` — у участника изменился список ролей;
- `remove(...)` — участник исчез из проекта.

В `add(...)` сначала должны успешно сохраниться `ProjectMember` и его роли,
потом публикуется событие, потом возвращается response.

В `replaceRoles(...)` сначала вызывается
`projectMemberRoleService.replace(...)`, затем publisher.

В `remove(...)` сначала вызывается `projectMemberRepository.delete(member)`,
затем publisher.

#### `WorkspaceMemberService`

Вызови publisher в:

- `changeRole(...)` — OWNER, ADMIN, MEMBER и VIEWER видят разный набор данных;
- `remove(...)` — удалённый участник не должен продолжать использовать старый
  персональный кеш.

Для текущего response добавление обычного workspace member ещё не обязательно
влияет на overview. Если в будущем overview будет содержать всех доступных для
назначения workspace members, publisher понадобится и в `add(...)`.

#### `AccessRoleService`

Для текущего DTO обязательно вызови publisher только в:

- `update(...)` — назначенная роль хранится в overview как `id`, `name` и
  `color`, поэтому изменение имени или цвета делает cached response устаревшим.

В `create(...)` publisher сейчас не нужен: новая роль ещё никому не назначена и
не входит в `WorkspaceProjectAccessOverviewResponse`.

В `delete(...)` publisher сейчас тоже не нужен. Service запрещает удалить роль,
если она кому-то назначена. Значит успешно удалиться может только роль, которой
и так нет в overview.

Если позднее overview начнёт содержать полный список доступных ролей, тогда
publisher понадобится и в `create(...)`, и в `delete(...)`.

### 4.3. Почему вызов publisher находится внутри транзакции

Метод `publish(...)` не удаляет кеш немедленно. Он создаёт Spring event.
Invalidator подписан так:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
```

Это означает:

```text
service вызвал publish(...)
  -> Spring запомнил событие до конца текущей транзакции
  -> MySQL commit успешен
  -> Spring вызывает invalidator
  -> invalidator удаляет старый кеш
```

Если сохранение в MySQL завершилось ошибкой:

```text
service вызвал publish(...)
  -> транзакция откатилась
  -> AFTER_COMMIT не наступил
  -> invalidator не вызван
  -> актуальный кеш не удалён
```

Поэтому нормально вызвать publisher до фактического commit. Commit происходит
после выхода из `@Transactional` метода, а listener ждёт его результата.

### 4.4. Почему не вызывать publisher из controller

Controller знает только про HTTP. Он не должен решать, какие таблицы влияют на
кеш. Кроме того, service может быть вызван из теста, другого service или будущей
фоновой задачи без controller. Тогда invalidation потеряется.

Правильное место — service, который выполняет изменение данных.

### 4.5. Когда событие не нужно

Не публикуй `WorkspaceProjectAccessChangedEvent` после:

- обычного GET;
- чтения профиля;
- изменения task status;
- изменения task assignee;
- любых изменений, данные которых не входят в
  `WorkspaceProjectAccessOverviewResponse`.

### 4.6. Что проверить после шага 4

Поиск по проекту должен показать publisher во всех нужных mutation services:

```powershell
rg -n "accessChangePublisher|\.publish\(workspaceId\)" src/main/java
```

Проверь:

- [ ] publisher добавлен через constructor injection;
- [ ] используется `workspaceId` изменяемого workspace;
- [ ] publisher находится в `@Transactional` методе;
- [ ] publisher вызывается после бизнес-проверок;
- [ ] controller не публикует события;
- [ ] read-only методы не публикуют события.

### Что посмотреть по этому шагу

```text
Spring ApplicationEventPublisher custom event example
Spring Boot application events service layer
Spring TransactionalEventListener AFTER_COMMIT example
EventListener vs TransactionalEventListener Spring
```

---

## 5. Понять и проверить invalidator

### Что делает существующий класс

Сейчас invalidator выглядит примерно так:

```java
@CacheEvict(
        cacheNames = WORKSPACE_PROJECT_ACCESS_OVERVIEW,
        allEntries = true
)
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void invalidate(WorkspaceProjectAccessChangedEvent event) {
    // Тело пустое.
}
```

Пустое тело не является ошибкой. Здесь работу выполняют аннотации:

- `@TransactionalEventListener` говорит, когда вызвать метод;
- `@CacheEvict` говорит, что сделать с кешем после вызова.

Метод нужен как точка, на которую Spring может повесить эти два поведения.

### Что означает `allEntries = true`

В Redis могут находиться такие ключи:

```text
...::42:7
...::42:9
...::51:7
```

Один workspace имеет отдельные записи для разных пользователей. Spring Cache
не умеет переносимо сказать «удали все ключи, которые начинаются с `42:`».
Поэтому первая версия удаляет все записи cache name.

Событие workspace 42 временно удалит и запись workspace 51. Это лишняя работа,
но после следующего GET запись будет построена заново. Зато ни один пользователь
не увидит устаревшие данные.

### Почему event содержит `workspaceId`, если он пока не используется

`workspaceId` показывает смысл события и пригодится для будущей точечной
инвалидации. Сейчас `allEntries = true` не использует его для выбора ключей.
Удалять поле из event не нужно.

### Почему удалять кеш надо после commit

Если удалить кеш до commit, возможна гонка:

```text
1. Транзакция A начала изменять проект.
2. Транзакция A удалила кеш, но ещё не сделала commit.
3. Запрос B увидел в MySQL старые данные.
4. Запрос B снова положил старые данные в Redis.
5. Транзакция A сделала commit.
6. В Redis остался старый response.
```

При `AFTER_COMMIT` порядок другой:

```text
1. MySQL сохранил новые данные.
2. Commit завершился успешно.
3. Старый кеш удалился.
4. Следующий GET читает уже новые данные из MySQL.
```

### Важное ограничение

Если `publish(...)` вызвать без активной Spring-транзакции, listener с обычными
настройками не выполнится. Поэтому publisher должен вызываться внутри методов,
которые действительно имеют `@Transactional` и вызываются через Spring bean.

### Что нужно менять в invalidator сейчас

Если текущие аннотации и cache name уже правильные, код invalidator менять не
нужно. На этом шаге задача — подключить publisher и затем доказать тестами, что:

```text
commit   -> кеш удалён
rollback -> кеш остался
```

### Что посмотреть по этому шагу

```text
Spring CacheEvict allEntries true Redis
Spring cache invalidation after transaction commit
TransactionalEventListener rollback AFTER_COMMIT
Spring CacheEvict proxy how it works
```

---

## 6. Понять ключ, TTL и настройки CacheConfiguration

### Как получается полный ключ

Ключ собирается из двух частей.

Первая часть задаётся в `CacheConfiguration`:

```text
collabdesk:v1:workspaceProjectAccessOverview::
```

Вторая часть задаётся в `@Cacheable`:

```text
42:7
```

Итог:

```text
collabdesk:v1:workspaceProjectAccessOverview::42:7
```

Разбор:

| Часть | Значение |
|---|---|
| `collabdesk` | Ключ принадлежит приложению CollabDesk. |
| `v1` | Версия формата кеша. |
| `workspaceProjectAccessOverview` | Имя конкретного cache. |
| `42` | `workspaceId`. |
| `7` | `currentUserId`. |

Версия `v1` пригодится, если формат DTO или ключа сильно изменится. Тогда можно
перейти на `v2`, и приложение перестанет читать старые несовместимые записи.

### Что означает TTL пять минут

TTL — максимальное время жизни записи:

```text
запись создана
  -> проходит до пяти минут
  -> Redis автоматически удаляет запись
```

TTL является страховкой. Основной способ убрать устаревшие данные — событие
после изменения MySQL. Если какое-то редкое mutation-событие забыли добавить,
ошибка не будет жить бесконечно.

### TTL, invalidation и memory eviction — не одно и то же

```text
TTL
  -> удаляет запись по времени

Application event + @CacheEvict
  -> удаляет запись после изменения бизнес-данных

allkeys-lru
  -> удаляет редко используемые ключи, когда Redis не хватает памяти
```

### Зачем JSON-сериализация

В Java находится объект:

```java
WorkspaceProjectAccessOverviewResponse
```

Redis хранит байты. `JacksonJsonRedisSerializer` превращает DTO в JSON перед
записью и собирает DTO обратно при чтении.

JSON удобнее диагностировать, чем стандартная бинарная Java-сериализация. Не
нужно самостоятельно вызывать `ObjectMapper` в service: это уже делает cache
manager.

### Зачем `LenientRedisCacheErrorHandler`

Redis здесь ускоритель, а MySQL — источник истины. Если Redis недоступен:

```text
ошибка cache get
  -> записать warning
  -> выполнить QueryService
  -> вернуть данные из MySQL
```

Основной запрос не должен падать только из-за кеша. При этом ошибки MySQL и
ошибки прав доступа обработчик Redis не скрывает.

### Что означают остальные настройки

| Настройка | Простое объяснение |
|---|---|
| `@EnableCaching` | Просит Spring обрабатывать `@Cacheable` и `@CacheEvict`. |
| `@Profile("redis")` | Включает эту конфигурацию только с профилем `redis`. |
| `disableCachingNullValues()` | Не сохраняет бесполезный `null`. |
| `StringRedisSerializer` | Делает Redis-ключ обычной строкой. |
| `JacksonJsonRedisSerializer` | Сохраняет response в JSON. |
| `nonLockingRedisCacheWriter` | Выполняет обычные cache operations без отдельного Redis lock. |
| `BatchStrategies.scan(1000)` | При очистке перебирает ключи порциями через SCAN. |
| `disableCreateOnMissingCache()` | Ошибка в cache name не создаст случайный новый cache. |
| Без `transactionAware()` | Полученная после commit команда очистки выполняется сразу. |

Здесь ожидание успешной транзакции уже делает `AFTER_COMMIT` event. Если ещё и
включить `transactionAware()`, cache manager попробует повторно отложить очистку
в момент, когда commit уже завершён. Поэтому event отвечает за момент запуска,
а cache manager — только за непосредственную операцию с Redis.

### Что посмотреть по этому шагу

```text
Spring RedisCacheManager TTL serializer example
Spring RedisCacheConfiguration computePrefixWith
Redis key naming convention namespace version
Redis TTL vs eviction policy
Redis SCAN vs KEYS command
```

---

## 7. Проверить работу вручную

Ручная проверка нужна до написания сложных integration-тестов. Так проще понять,
какая часть цепочки не работает.

### 7.1. Запустить Redis

```powershell
docker compose up -d redis
docker compose ps
docker compose exec redis redis-cli ping
```

Ожидаемый ответ последней команды:

```text
PONG
```

Если `PONG` нет, пока не запускай проверку кеша: сначала исправь запуск
контейнера.

### 7.2. Запустить приложение с профилем `redis`

В PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "redis"
./mvnw.cmd spring-boot:run
```

Или добавь `redis` в Active profiles Run Configuration IntelliJ IDEA.

Без этого профиля `CacheConfiguration` не создаётся, поэтому Redis-кеш не будет
включён. Это не ошибка: обычный запуск специально должен работать без Redis.

### 7.3. Выполнить первый GET

Выполни:

```http
GET /api/v1/workspaces/{workspaceId}/project-access-overview
```

При первом запросе нужного ключа ещё нет:

```text
CacheService проверяет Redis
  -> MISS
  -> QueryService читает MySQL
  -> response сохраняется в Redis
```

### 7.4. Найти созданный ключ

Используй безопасное сканирование:

```powershell
docker compose exec redis redis-cli --scan --pattern "collabdesk:v1:*"
```

Ожидаемый вид:

```text
collabdesk:v1:workspaceProjectAccessOverview::42:7
```

Не используй `KEYS *` в application flow. На большой базе он может надолго
заблокировать Redis.

### 7.5. Проверить TTL

Подставь реальный ключ:

```powershell
docker compose exec redis redis-cli TTL "collabdesk:v1:workspaceProjectAccessOverview::42:7"
```

Сразу после создания ожидается положительное число не больше `300`.

Типичные ответы:

```text
250  -> ключ существует, осталось 250 секунд
-1   -> ключ существует, но TTL не установлен: это ошибка конфигурации
-2   -> такого ключа нет
```

### 7.6. Выполнить второй такой же GET

Повтори запрос с тем же пользователем и workspace.

Теперь ожидается:

```text
CacheService проверяет Redis
  -> HIT
  -> готовый response сразу возвращается
  -> QueryService не вызывается
```

На первом этапе это можно увидеть с debugger breakpoint в QueryService. Первый
GET остановится на breakpoint, второй одинаковый GET — нет.

### 7.7. Проверить разные пользовательские ключи

Выполни GET от имени второго пользователя того же workspace. После этого SCAN
должен показать два разных окончания ключа:

```text
...::42:7
...::42:9
```

Если используется один ключ, остановись и исправь выражение `key` в
`@Cacheable`: продолжать небезопасно.

### 7.8. Проверить удаление кеша после изменения

1. Создай ключ обычным GET.
2. Измени visibility проекта или роли участника.
3. Повтори команду `--scan`.
4. Старые overview entries должны исчезнуть.
5. Снова выполни GET.
6. Новый response должен построиться из актуальных данных MySQL.

Если ключ не удалился, проверь по порядку:

1. был ли вызван `accessChangePublisher.publish(workspaceId)`;
2. выполнялся ли метод внутри Spring-транзакции;
3. завершилась ли транзакция commit;
4. активен ли профиль `redis`;
5. создался ли bean invalidator;
6. совпадает ли cache name в `@Cacheable` и `@CacheEvict`.

### 7.9. Проверить работу без Redis

Останови только Redis:

```powershell
docker compose stop redis
```

MySQL и приложение оставь запущенными. Затем снова вызови GET overview.

Ожидаемое поведение:

- запрос может стать медленнее;
- в логах появится warning об ошибке кеша;
- response всё равно придёт из MySQL;
- приложение не должно отвечать ошибкой только из-за Redis.

После проверки верни Redis:

```powershell
docker compose start redis
```

---

## 8. Добавить тесты

### Зачем нужны тесты, если ручная проверка уже работает

Ручная проверка доказывает, что схема работает сейчас. Автоматические тесты не
дадут случайно сломать её следующим изменением constructor, service или cache
key.

### 8.1. Тест facade

Проверь порядок:

```text
OverviewService.findForWorkspace(...)
  -> workspaceAccessService.requireMember(...)
  -> cacheService.findForWorkspace(...)
```

Также проверь: если `requireMember(...)` выбросил ошибку, CacheService не был
вызван. Это главный security-смысл facade.

### 8.2. Тест CacheService

Обычный unit-тест без Spring не проверит работу `@Cacheable`, потому что в нём
нет Spring proxy. Для настоящего cache hit нужен Spring integration test.

Unit-тест может проверить только делегирование в QueryService. Integration-тест
должен вызвать Spring bean два раза и убедиться, что QueryService выполнился
один раз.

### 8.3. Тесты publisher в mutation services

Для каждого влияющего метода проверь:

```text
успешное изменение
  -> publisher.publish(правильный workspaceId) вызван один раз

ошибка бизнес-проверки
  -> publisher не вызван
```

Особенно проверь:

- `ProjectService.create` и `changeVisibility`;
- `ProjectMemberService.add`, `replaceRoles` и `remove`;
- `WorkspaceMemberService.changeRole` и `remove`;
- `AccessRoleService.update`.

### 8.4. Redis integration tests

Используй настоящий Redis через Testcontainers. Проверь по одному поведению на
тест:

1. Первый вызов создаёт cache entry.
2. Второй одинаковый вызов не обращается к QueryService.
3. Ключ содержит workspace id и user id.
4. Два пользователя имеют разные keys.
5. Value записано как JSON.
6. У ключа есть TTL не больше пяти минут.
7. Успешный mutation commit очищает кеш.
8. Transaction rollback не очищает кеш.
9. Удалённый workspace member не получает старый response.

Не заменяй Redis на `Map` в этих тестах. `Map` не проверяет Redis prefix,
serialization, TTL, SCAN и реальный `RedisCacheManager`.

### 8.5. Полная проверка проекта

```powershell
./mvnw.cmd test
npm.cmd run lint --prefix frontend
npm.cmd run build --prefix frontend
```

Frontend не меняется, но его проверки подтверждают, что backend-рефакторинг не
повлиял на существующий API-контракт.

---

## 9. Похожие cache-методы

### `@Cacheable`

Используется для чтения:

```text
ключ есть   -> вернуть кеш, метод не выполнять
ключа нет   -> выполнить метод и сохранить результат
```

Именно это нужно для overview.

### `@CachePut`

Всегда выполняет метод, а затем кладёт его результат в кеш. Такой подход удобен,
когда mutation возвращает полностью готовое значение для одного точно известного
ключа.

Для overview он неудобен: после одного изменения нужно обновить персональные
responses многих пользователей, а mutation method не строит их все.

### `@CacheEvict(key = ...)`

Удаляет один точный ключ. Например, это удобно для кеша объекта, где ключом
является только `projectId`.

В текущем overview одного `workspaceId` недостаточно: у workspace много ключей
с разными `currentUserId`.

### `@CacheEvict(allEntries = true)`

Удаляет все entries одного cache name. Это текущий вариант:

- простой;
- надёжный;
- может удалить больше записей, чем требуется;
- подходит для первой версии до измерения реальной нагрузки.

### Прямой `CacheManager`

Можно внедрить `CacheManager` в service и вызвать:

```java
cache.evict(key);
cache.clear();
```

Это даёт ручной контроль, но связывает business service с инфраструктурой кеша.
Событие делает связь слабее и ясно отделяет mutation от invalidation.

### Только TTL без событий

Самый простой вариант — ничего не очищать и ждать пять минут. Он требует меньше
кода, но пользователи до истечения TTL видят старые роли и проекты. Для access
overview это нежелательно.

### Versioned keys

В будущем можно хранить версию workspace:

```text
workspace 42 version = 8
key = workspace:42:version:8:user:7
```

После mutation версия станет `9`, и старые keys перестанут читаться. Это
позволит не очищать весь cache name, но добавит ещё одну Redis operation и более
сложные тесты. Сейчас это делать не нужно.

### Redis Pub/Sub

Redis Pub/Sub передаёт сообщения между разными процессами. Текущий
`ApplicationEventPublisher` работает внутри одного Spring-приложения.

Pub/Sub сейчас не нужен, потому что все экземпляры приложения используют общий
Redis, а `@CacheEvict` удаляет entries непосредственно из него.

---

## 10. Точный порядок работы

Не пытайся сделать весь Redis-этап одним большим изменением. Выполняй маленькими
проверяемыми блоками.

### Блок A — подключить чтение

- [ ] Создать `WorkspaceProjectAccessOverviewQueryService`.
- [ ] Перенести в него существующий алгоритм построения overview.
- [ ] Создать `WorkspaceProjectAccessOverviewCacheService`.
- [ ] Добавить в CacheService `@Cacheable` с ключом из двух id.
- [ ] Переделать старый OverviewService в facade.
- [ ] Проверить, что facade сначала вызывает `requireMember`.
- [ ] Запустить `./mvnw.cmd test` без профиля Redis.

Остановись и исправь ошибки, если обычные тесты не проходят. Publisher пока не
подключай, пока read path не стал понятным и рабочим.

### Блок B — подключить изменения

- [ ] Добавить publisher в constructor `ProjectService`.
- [ ] Публиковать событие после create и changeVisibility.
- [ ] Добавить publisher в `ProjectMemberService`.
- [ ] Публиковать событие после add, replaceRoles и remove.
- [ ] Добавить publisher в `WorkspaceMemberService`.
- [ ] Публиковать событие после changeRole и remove.
- [ ] Добавить publisher в `AccessRoleService`.
- [ ] Публиковать событие после `AccessRoleService.update`.
- [ ] Запустить unit-тесты изменённых services.

### Блок C — проверить Redis руками

- [ ] Запустить Redis и получить `PONG`.
- [ ] Запустить приложение с профилем `redis`.
- [ ] Первым GET создать cache entry.
- [ ] Проверить полный key.
- [ ] Проверить TTL.
- [ ] Вторым GET подтвердить cache hit.
- [ ] Выполнить GET вторым пользователем и увидеть второй key.
- [ ] Выполнить mutation и увидеть удаление кеша.
- [ ] Остановить Redis и проверить fallback на MySQL.

### Блок D — закрепить тестами

- [ ] Добавить тест facade security-порядка.
- [ ] Добавить тесты publisher calls.
- [ ] Добавить Redis Testcontainer.
- [ ] Проверить cache hit, key, JSON и TTL.
- [ ] Проверить commit и rollback.
- [ ] Запустить полный backend suite.
- [ ] Запустить frontend lint и build.

---

## 11. Как понять, что задача закончена

- [ ] Первый GET строит overview из MySQL и записывает его в Redis.
- [ ] Второй одинаковый GET не выполняет QueryService.
- [ ] В Redis key присутствуют `workspaceId` и `currentUserId`.
- [ ] Разные пользователи не разделяют один cached response.
- [ ] Проверка membership выполняется перед каждым cache lookup.
- [ ] Изменения проектов, участников и ролей публикуют event.
- [ ] После commit старый кеш удаляется.
- [ ] После rollback кеш не удаляется.
- [ ] TTL равен пяти минутам.
- [ ] DTO хранится в JSON.
- [ ] При выключенном Redis endpoint отвечает из MySQL.
- [ ] Backend-тесты проходят.
- [ ] Frontend lint и build проходят.

## 12. Что пока не делать

- не добавлять Redis Pub/Sub;
- не писать Redis-команды в controller;
- не использовать Redis как основную базу данных;
- не кешировать JPA entities;
- не кешировать решение «пускать пользователя или нет»;
- не убирать актуальную membership-проверку перед кешем;
- не использовать `KEYS *`, `FLUSHALL` или `FLUSHDB` из приложения;
- не добавлять distributed locks;
- не помещать email, token или display name в key;
- не оптимизировать `allEntries = true`, пока простая версия не заработала и не
  покрыта тестами.

## Справочные материалы

- [Spring Framework cache annotations](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html)
- [Spring Data Redis Cache](https://docs.spring.io/spring-data/redis/reference/redis/redis-cache.html)
- [Spring transaction-bound events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
- [Spring application events](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)
- [Redis keyspace](https://redis.io/docs/latest/develop/use/keyspace/)
- [Redis TTL command](https://redis.io/docs/latest/commands/ttl/)
