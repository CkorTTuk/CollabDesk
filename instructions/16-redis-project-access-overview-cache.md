# Этап 16. Redis-кеш workspace project access overview

## Результат этапа

После этого этапа агрегированный workspace overview из этапа 15 кешируется в
Redis:

- повторное открытие workspace не собирает один и тот же overview из MySQL;
- кеш разделён по workspace и текущему пользователю;
- restricted-проекты не попадают в кеш другого пользователя;
- изменения проектов, участников и кастомных ролей сбрасывают устаревший кеш;
- сброс происходит только после успешного commit транзакции;
- недоступный Redis не ломает основной сценарий — данные читаются из MySQL;
- Redis остаётся ускоряющим слоем, а не источником истины и не частью
  permission-проверок.

На этом этапе кешируется только:

```http
GET /api/v1/workspaces/{workspaceId}/project-access-overview
```

Остальные API и frontend-контракты не меняются.

---

## 1. Зачем здесь Redis

`project-access-overview` объединяет:

```text
Projects
├── Workspace members
├── Project members
├── Custom roles
└── Role assignments
```

Даже при batch-запросах его построение дороже обычного чтения одной entity.
При этом результат часто запрашивается повторно при открытии workspace,
возврате со страницы проекта и обновлении интерфейса.

Redis нужен для повторного использования уже собранного read model в течение
короткого времени. Он не должен заменять MySQL: после истечения TTL или cache
miss сервис заново строит корректный response из основной базы.

## 2. Границы кеша

Кешируй готовый:

```java
WorkspaceProjectAccessOverviewResponse
```

Не кешируй:

- JPA entities и Hibernate proxies;
- `WorkspaceMember`, `ProjectMember` и `AccessRole` по отдельности;
- результат `ProjectAccessService` для permission-проверок;
- authentication, сессии и CSRF tokens;
- task assignees и task status;
- ошибки, `null`, `401`, `403` и `404` responses.

Кеш read model допустим только после того, как текущий пользователь прошёл
обычную проверку membership и доступных проектов. Авторизация остаётся в
существующих сервисах и всегда вычисляется по актуальным данным MySQL.

## 3. Cache name и ключ

Используй cache name:

```text
workspaceProjectAccessOverview
```

Логическая часть ключа:

```text
{workspaceId}:{currentUserId}
```

Полный ключ в Redis должен иметь versioned prefix:

```text
collabdesk:v1:workspaceProjectAccessOverview::{workspaceId}:{currentUserId}
```

`currentUserId` обязателен. Один и тот же workspace даёт разные responses:

```text
OWNER / ADMIN -> полный overview
MEMBER / VIEWER -> только доступные проекты и безопасная member summary
```

Ключ только по `workspaceId` может раскрыть restricted-проект пользователю,
который не должен его видеть.

Не включай access token, email или display name в ключ. Они не нужны для
разделения результата и делают ключи нестабильными или чувствительными.

## 4. Dependencies

Добавь в `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Для integration tests используй Redis Testcontainer. Версию Testcontainers не
задавай отдельно, если она уже управляется Spring Boot dependency management.

Не добавляй Spring Data Redis repositories: в этом этапе используются только
cache abstraction и `RedisCacheManager`.

## 5. Что именно нужно создать

Redis здесь состоит из двух независимых частей:

```text
Redis server
  -> отдельный процесс, локально запускается в Docker

Spring Redis client
  -> часть CollabDesk, подключается к server по host и port
```

`application-redis.properties` не создаёт и не запускает Redis server. Этот файл
только сообщает Spring, куда подключаться. Локальный server описывается в
корневом `docker-compose.yaml`.

В проекте файл уже называется `docker-compose.yaml`, поэтому новый
`compose.yaml` создавать не нужно. В существующий `services` должен входить
такой service:

```yaml
services:
  redis:
    image: redis:8-alpine
    container_name: collabdesk-redis
    ports:
      - "6379:6379"
    command:
      - redis-server
      - --maxmemory
      - 128mb
      - --maxmemory-policy
      - allkeys-lru
      - --save
      - ""
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 10
```

Важно: команда называется именно `redis-server`, через дефис. В текущем
`docker-compose.yaml` написано `redis_server`, и это нужно исправить до первого
запуска: исполняемого файла с подчёркиванием в образе нет.

### Что означает каждый параметр Docker Compose

| Параметр | Что делает | Как выбирать значение |
|---|---|---|
| `image: redis:8-alpine` | Скачивает Redis 8 в компактном Alpine Linux image. | Для проекта фиксируй major/version, не используй плавающий `latest`. |
| `container_name` | Даёт контейнеру понятное локальное имя. | Не обязателен; нужен только для удобной диагностики. |
| `"6379:6379"` | Левый порт — порт Windows/host, правый — порт Redis внутри контейнера. | Оставь так, если порт 6379 свободен. При конфликте можно поставить `"6380:6379"` и затем задать `REDIS_PORT=6380`. |
| `redis-server` | Запускает сервер Redis внутри контейнера. | Это имя команды образа, менять его не нужно. |
| `--maxmemory 128mb` | Ограничивает объём памяти, используемый для хранимых ключей. | `128mb` достаточно для локальной разработки. Production-лимит выбирается по размеру DTO, числу пользователей и метрикам. |
| `--maxmemory-policy allkeys-lru` | При достижении лимита удаляет приблизительно самые давно неиспользуемые ключи. | Подходит для instance, который целиком используется как cache. |
| `--save ""` | Отключает RDB snapshots на диск. | Подходит только потому, что Redis не является source of truth. |
| `healthcheck` | Периодически вызывает `redis-cli ping`; исправный Redis отвечает `PONG`. | Эти локальные интервалы можно оставить без изменения. |
| `interval: 5s` | Запускает healthcheck каждые 5 секунд. | Для local development достаточно. |
| `timeout: 3s` | Считает одну healthcheck-проверку неуспешной через 3 секунды. | Это Docker timeout, не Spring timeout. |
| `retries: 10` | Помечает container unhealthy после 10 неудачных проверок подряд. | Даёт Redis время стартовать на медленной машине. |

Здесь намеренно нет Redis volume. При удалении или пересоздании контейнера кеш
исчезнет, после чего приложение снова соберёт overview из MySQL. MySQL остаётся
единственным source of truth.

Не публикуй Redis port в интернет. `ports` в этом примере нужен для разработки,
потому что Spring Boot запускается на Windows, а Redis — в Docker. В production
Redis должен быть доступен только приложению по приватной сети.

### Первый запуск и проверка Redis

Из корня проекта выполни:

```powershell
docker compose config
docker compose up -d redis
docker compose ps redis
docker compose exec redis redis-cli ping
```

Ожидаемый последний ответ:

```text
PONG
```

Дополнительная проверка реально применённых настроек:

```powershell
docker compose exec redis redis-cli CONFIG GET maxmemory
docker compose exec redis redis-cli CONFIG GET maxmemory-policy
docker compose exec redis redis-cli CONFIG GET save
```

Если container не запустился:

```powershell
docker compose logs redis
```

Частые причины:

- осталось `redis_server` вместо `redis-server`;
- Docker Desktop не запущен;
- port `6379` уже занят другим Redis;
- команда выполнена не из корня, где находится `docker-compose.yaml`.

Остановить Redis, не удаляя остальной compose stack:

```powershell
docker compose stop redis
```

Удалить только его container можно командой:

```powershell
docker compose rm -f redis
```

Для cache-only Redis это безопасно: будут потеряны только пересоздаваемые cache
entries.

## 6. Profiles и properties: настройка с нуля

### Зачем нужен профиль `redis`

В Spring profile — это переключаемый набор настроек. Файл
`application.properties` загружается всегда, а
`application-redis.properties` дополнительно загружается только при активном
profile `redis`.

Нужное поведение:

```text
profile redis выключен
  -> spring.cache.type=none
  -> CollabDesk работает только с MySQL

profile redis включён
  -> spring.cache.type=redis
  -> overview дополнительно кешируется в Redis
```

Добавь в основной `src/main/resources/application.properties`:

```properties
spring.cache.type=none
```

Это значение важно: наличие Redis dependencies в `pom.xml` само по себе не
должно включать Redis cache при обычном запуске. Профиль затем переопределит
`none` на `redis`.

Файл `src/main/resources/application-redis.properties` в проекте уже создан и
содержит:

```properties
spring.cache.type=redis
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.connect-timeout=2s
spring.data.redis.timeout=1s
```

### Что означает каждая строка `application-redis.properties`

| Property | Значение | Практический смысл |
|---|---|---|
| `spring.cache.type=redis` | `redis` | Выбирает Redis как реализацию Spring Cache abstraction. Не запускает server и не означает, что все методы автоматически кешируются. Кешироваться будут только явно настроенные cache operations. |
| `spring.data.redis.host=${REDIS_HOST:localhost}` | environment variable `REDIS_HOST`, иначе `localhost` | Адрес Redis server. При локальном запуске Java на Windows `localhost` правильно указывает на опубликованный Docker port. |
| `spring.data.redis.port=${REDIS_PORT:6379}` | environment variable `REDIS_PORT`, иначе `6379` | TCP port Redis. Это число должно совпадать с левым значением в Compose mapping `"6379:6379"`. |
| `spring.data.redis.connect-timeout=2s` | 2 секунды | Максимальное ожидание установления сетевого соединения. Срабатывает, например, когда host недоступен. |
| `spring.data.redis.timeout=1s` | 1 секунда | Максимальное ожидание ответа на уже отправленную Redis command. Это read timeout, а не TTL cache entry. |

Три времени в этом этапе нельзя смешивать:

```text
connect-timeout = сколько ждать соединения с Redis
timeout         = сколько ждать ответ Redis command
TTL 5 минут     = сколько cache entry может жить в Redis
```

TTL настраивается в `RedisCacheManager`, а не этими двумя properties.

Синтаксис `${REDIS_HOST:localhost}` означает:

```text
если REDIS_HOST задан
  -> использовать его значение
иначе
  -> использовать localhost
```

Так один и тот же artifact работает локально и в разных deployment
environments без изменения файла в Git.

### Как выбрать host и port

| Где запущен CollabDesk | Где запущен Redis | `REDIS_HOST` | `REDIS_PORT` |
|---|---|---|---:|
| На Windows через Maven/IDEA | В этом Docker Compose | `localhost` | `6379` |
| В container того же Compose | Service `redis` | `redis` | `6379` |
| На сервере/VM | Отдельный Redis в приватной сети | DNS name или private IP | Порт сервиса |
| В cloud с managed Redis | У провайдера | Выданный provider endpoint | Выданный provider port |

Внутри Docker `localhost` означает текущий application container, а не соседний
Redis container. Поэтому container-to-container соединение использует service
name `redis`.

Если локальный host port изменён на `"6380:6379"`, Java, запущенная с Windows,
должна использовать `REDIS_PORT=6380`. Внутри Compose по-прежнему используется
port `6379`.

### Локальный запуск CollabDesk с кешем

Если Redis уже запущен в Docker, а Spring Boot запускается из IDEA или Maven на
той же машине, `.env` для Redis действительно не нужен. В
`application-redis.properties` уже есть локальные fallback-значения:

```properties
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
```

При отсутствии environment variables Spring подставит `localhost` и `6379`.
Это совпадает с Compose mapping `"6379:6379"`.

Это утверждение относится только к Redis. Для всего CollabDesk `.env` всё ещё
может быть нужен из-за MySQL: в основном `application.properties` у
`DATABASE_PASSWORD` нет fallback, а `docker-compose.yaml` требует также
`DATABASE_ROOT_PASSWORD`. Если MySQL уже настроен и приложение сейчас
запускается, ничего нового добавлять не нужно. Минимальный local `.env` для
полного compose stack может выглядеть так:

```properties
DATABASE_PASSWORD=локальный-пароль-admin
DATABASE_ROOT_PASSWORD=локальный-пароль-root
```

Redis-переменные туда добавлять не требуется. Не коммить реальные passwords.

Но запущенный container сам по себе не включает кеш в Spring. Нужно различать
два переключателя:

```text
docker compose up -d redis
  -> запускает Redis server на localhost:6379

active Spring profile redis
  -> загружает application-redis.properties
  -> создаёт Redis CacheManager
  -> включает обработку @Cacheable / @CacheEvict
```

Если просто запустить `CollabDeskApplication.main()` без profile, Spring
загрузит только основной `application.properties`, увидит
`spring.cache.type=none` и не будет пользоваться Redis. Container может работать
рядом, но приложение к нему не обратится.

Именно для этого существует параметр запуска profile: он выбирает, нужно ли
данному запуску приложение с Redis или без него. Host и port отвечают на вопрос
«куда подключаться», а profile — на вопрос «подключать ли Redis cache вообще».

### Вариант 1: запуск из PowerShell

В одном PowerShell:

```powershell
docker compose up -d redis
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=redis"
```

`-Dspring-boot.run.profiles=redis` — параметр Maven Spring Boot Plugin. Он
активирует profile только для этого запуска и не изменяет файлы проекта.

То же самое через стандартную Spring environment variable:

```powershell
$env:SPRING_PROFILES_ACTIVE = "redis"
.\mvnw.cmd spring-boot:run
```

После закрытия текущего PowerShell эта переменная исчезнет. Сбросить её раньше:

```powershell
Remove-Item Env:SPRING_PROFILES_ACTIVE
```

Здесь `.env` тоже не создаётся: значение живёт только в текущем процессе
PowerShell и наследуется запущенной Java.

### Вариант 2: запуск `CollabDeskApplication` из IntelliJ IDEA

1. Один раз запусти `docker compose up -d redis` из корня проекта.
2. Открой `Run -> Edit Configurations`.
3. Выбери Spring Boot configuration для `CollabDeskApplication`.
4. В поле `Active profiles` укажи `redis`.
5. Host/port и Redis environment variables оставь пустыми.
6. Запусти application обычной кнопкой Run.

IDEA фактически передаст Spring активный profile. Значения `localhost:6379`
возьмутся из fallback в `application-redis.properties`. Это наиболее удобный
локальный вариант: Docker Redis можно держать запущенным, а profile сохраняется
в локальной Run Configuration.

Если поля `Active profiles` в конкретной конфигурации нет, добавь environment
variable:

```text
SPRING_PROFILES_ACTIVE=redis
```

Не добавляй `REDIS_HOST` и `REDIS_PORT`, пока используются стандартные
`localhost:6379`. Они нужны только для переопределения defaults.

### Можно ли всегда включить `redis` в `application.properties`

Технически можно записать:

```properties
spring.profiles.active=redis
```

Тогда обычный запуск `CollabDeskApplication` всегда загрузит
`application-redis.properties`, и отдельный параметр запуска не понадобится.
Для этого проекта так делать не рекомендуется:

- приложение перестанет иметь очевидный режим запуска без Redis;
- обычные тесты и запуск нового разработчика не должны зависеть от Docker;
- production/staging могут включать разные наборы profiles;
- временно отключить cache будет сложнее.

Оставь Redis явным opt-in profile в Run Configuration. Это не настройка адреса,
а выбор режима работы приложения.

Для нестандартного локального порта:

```powershell
$env:REDIS_PORT = "6380"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=redis"
```

Для обычного запуска без кеша Redis server и profile не нужны:

```powershell
.\mvnw.cmd spring-boot:run
```

Итог для стандартного локального запуска:

```text
.env для Redis             -> не нужен
REDIS_HOST / REDIS_PORT    -> не нужны
Docker Redis               -> должен быть запущен
Spring profile redis       -> должен быть активен
Java cache configuration   -> должна быть Spring bean внутри package collabdesk
```

### Как убедиться, что Spring видит Redis

Проверка состоит из двух уровней:

```text
docker compose exec redis redis-cli ping
  -> проверяет сам Redis server

GET workspace access overview два раза
  -> проверяет Spring Cache, сериализацию и cache key приложения
```

После запроса endpoint посмотри только ключи development-instance:

```powershell
docker compose exec redis redis-cli --scan --pattern "collabdesk:v1:*"
```

Для конкретного найденного ключа:

```powershell
docker compose exec redis redis-cli TTL "полный-ключ"
docker compose exec redis redis-cli TYPE "полный-ключ"
docker compose exec redis redis-cli GET "полный-ключ"
```

Ожидается:

- prefix начинается с `collabdesk:v1:`;
- TTL положительный и не больше 300 секунд;
- type равен `string`;
- value читается как JSON, а не как бинарная JDK serialization.

Не используй `KEYS *` в production: команда просматривает всё keyspace. Для
диагностики используй итеративный `SCAN`, как в примере выше.

### Пароль, username, TLS и production

Локальный Redis из Compose намеренно работает без пароля и доступен только для
разработки. Для production используй Redis, доступный по приватной сети, с
credentials и TLS, если это предоставляет выбранная инфраструктура.

Дополнительные Spring properties:

```properties
spring.data.redis.username=${REDIS_USERNAME}
spring.data.redis.password=${REDIS_PASSWORD}
spring.data.redis.ssl.enabled=true
```

Добавляй их только когда server действительно настроен на соответствующие
username/password/TLS. Иначе клиент не сможет подключиться. Не придумывай эти
значения самостоятельно: Redis provider или инфраструктурная конфигурация
должны выдать endpoint и credentials.

Если provider выдаёт единый connection URL, Spring также поддерживает:

```properties
spring.data.redis.url=${REDIS_URL}
```

`spring.data.redis.url` переопределяет отдельные `host`, `port`, `username`,
`password` и database. Выбери один способ конфигурации и не смешивай два набора
без необходимости.

Secrets нельзя записывать в Git, `application-redis.properties`,
`docker-compose.yaml` или документацию. Передавай их через environment
variables/secret storage deployment-платформы. Не выводи `REDIS_PASSWORD` в
логи и диагностические команды.

Production checklist:

- profile `redis` явно активирован через `SPRING_PROFILES_ACTIVE=redis`;
- `REDIS_HOST` и `REDIS_PORT` указывают на приватный endpoint;
- credentials и TLS соответствуют настройкам provider;
- Redis port не опубликован в интернет;
- memory limit выбран по нагрузке, а не слепо скопирован как `128mb`;
- `CacheErrorHandler` уже обеспечивает fallback в MySQL;
- после deployment проверены health, cache key, JSON value и TTL.

## 7. RedisCacheManager

`CacheManager` — это компонент Spring, который переводит абстрактные операции
`cache.get`, `cache.put` и `cache.evict` в команды конкретного хранилища. Сам
`@Cacheable` не знает ни Redis host, ни JSON serializer, ни TTL. Всё это он
получает через `CacheManager`.

### Сначала исправь package конфигурационного класса

Сейчас в проекте уже есть пустой файл:

```text
src/main/java/cache/RedisCacheConfiguration.java
package cache;
```

Он расположен неправильно. `CollabDeskApplication` находится в package
`collabdesk`, поэтому стандартный component scan видит только:

```text
collabdesk
collabdesk.*
```

Package верхнего уровня `cache` в scan не входит. Даже если добавить туда
`@Configuration`, Spring не создаст bean.

Перемести/замени класс следующим файлом:

```text
src/main/java/collabdesk/infrastructure/cache/CollabDeskRedisCacheConfiguration.java
```

Название `CacheConfiguration` намеренно отличается от Spring
класса `org.springframework.data.redis.cache.RedisCacheConfiguration`. Иначе в
одном файле появятся два класса с одинаковым коротким именем и imports станут
непонятными.

### Полная конфигурация для текущего проекта

Проект использует Spring Boot 4.1 и Spring Data Redis 4.1, то есть Jackson 3.
Для единственного кешируемого DTO используй typed
`JacksonJsonRedisSerializer`. Не копируй старые примеры с
`GenericJackson2JsonRedisSerializer`: класс Jackson 2 deprecated в этой версии.

```java
package collabdesk.infrastructure.cache;

import collabdesk.project.dto.WorkspaceProjectAccessOverviewResponse;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableCaching
@Profile("redis")
public class CollabDeskRedisCacheConfiguration {

    public static final String WORKSPACE_PROJECT_ACCESS_OVERVIEW =
            "workspaceProjectAccessOverview";

    private static final String KEY_PREFIX = "collabdesk:v1:";
    private static final Duration OVERVIEW_TTL = Duration.ofMinutes(5);

    @Bean
    RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory
    ) {
        var keySerializer = RedisSerializationContext
                .SerializationPair
                .fromSerializer(new StringRedisSerializer());

        var valueSerializer = RedisSerializationContext
                .SerializationPair
                .fromSerializer(new JacksonJsonRedisSerializer<>(
                        WorkspaceProjectAccessOverviewResponse.class
                ));

        var overviewConfiguration =
                org.springframework.data.redis.cache.RedisCacheConfiguration
                        .defaultCacheConfig()
                        .entryTtl(OVERVIEW_TTL)
                        .disableCachingNullValues()
                        .computePrefixWith(
                                cacheName -> KEY_PREFIX + cacheName + "::"
                        )
                        .serializeKeysWith(keySerializer)
                        .serializeValuesWith(valueSerializer);

        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(
                        WORKSPACE_PROJECT_ACCESS_OVERVIEW,
                        overviewConfiguration
                )
                .disableCreateOnMissingCache()
                .build();
    }
}
```

Здесь не нужен вручную написанный `RedisConnectionFactory`. Starter читает
`spring.data.redis.*` и Spring Boot автоматически создаёт connection factory
для `localhost:6379` или переданного environment. Spring затем передаёт готовый
bean в параметр метода `cacheManager(...)`.

### Что происходит при старте приложения

При активном profile `redis` последовательность такая:

```text
1. Spring загружает application.properties
2. Spring поверх него загружает application-redis.properties
3. Redis auto-configuration читает spring.data.redis.*
4. создаётся RedisConnectionFactory
5. component scan находит CollabDeskRedisCacheConfiguration
6. @Bean создаёт RedisCacheManager
7. @EnableCaching включает cache interceptor/proxy
8. вызов public @Cacheable метода проходит через этот proxy
```

Создание `RedisConnectionFactory` не означает, что Spring запускает Redis
server. Server уже должен работать в Docker. Кроме того, клиент может открыть
фактическое сетевое соединение лениво — при первой cache operation, поэтому один
успешный startup log ещё не доказывает, что Redis доступен. Проверяй `PING` и
реальный вызов кешируемого endpoint.

Без profile `redis` аннотация `@Profile("redis")` исключает весь configuration
class, `@EnableCaching` из него не активируется, а основной
`spring.cache.type=none` сохраняет режим без кеша.

### Redis простыми словами

Redis — это отдельный server, который преимущественно хранит данные в оперативной
памяти. Он не знает о JPA entities, repositories, workspace и permissions. Для
него данные выглядят как пары:

```text
key   -> уникальное имя записи
value -> набор bytes, лежащий под этим именем
```

В CollabDesk одна запись будет выглядеть концептуально так:

```text
key:
collabdesk:v1:workspaceProjectAccessOverview::42:7

value:
{"projects":[...]}
```

Части `42:7` означают:

```text
42 -> workspaceId
7  -> currentUserId
```

Другой пользователь того же workspace получает другой key:

```text
collabdesk:v1:workspaceProjectAccessOverview::42:8
```

Это две независимые записи. Redis не понимает, что они относятся к одному
workspace. Эту структуру определяет CollabDesk.

У Redis нет таблицы `workspace_project_access_overview`. Cache name в Spring —
это логическая группа ключей, а не Redis table. Prefix позволяет увидеть эту
группу и не смешивать её с ключами другого приложения или другой версии.

### Что Spring делает с Redis при запросе

Для Redis основными действиями этого этапа являются:

```text
GET key       -> прочитать value
SET key value -> записать value
DEL key       -> удалить value
EXPIRE/TTL    -> задать или проверить время жизни
```

Spring Data Redis выполняет команды сам. Вызывать `redis-cli SET` из application
code не нужно.

Первый запрос:

```text
1. Spring формирует key 42:7
2. Redis не находит полный key
3. это cache MISS
4. query service строит response из MySQL
5. JSON serializer превращает response в bytes
6. Spring записывает key + JSON value + TTL в Redis
7. response возвращается frontend
```

Повторный запрос:

```text
1. Spring формирует тот же key 42:7
2. Redis возвращает сохранённые bytes
3. это cache HIT
4. JSON serializer восстанавливает Java response
5. тяжёлая сборка overview из MySQL не выполняется
```

Базовая membership-проверка из раздела 8 всё равно обращается к актуальным
данным. Кеш пропускает только повторную сборку большого overview.

| Термин | Что означает |
|---|---|
| Cache entry | Одна пара key/value вместе с её TTL. |
| Cache hit | Key найден: можно вернуть сохранённый response. |
| Cache miss | Key отсутствует или истёк: response нужно собрать заново. |
| Cache put | Запись нового/обновлённого value по key. |
| Cache evict | Явное удаление entry приложением. |
| Cache clear | Удаление всех entries логического cache. |

Redis здесь не является резервной копией MySQL. Если Redis container удалить,
CollabDesk потеряет только ускоряющие копии responses. Первый последующий запрос
будет медленнее и снова заполнит cache.

Ускорение появляется потому, что без кеша backend загружает несколько связанных
наборов из MySQL и собирает DTO, а при HIT выполняет адресное чтение уже готового
результата по одному key из памяти Redis. Redis всё равно является отдельным
сетевым процессом, поэтому кешировать имеет смысл дорогой и повторяемый read
flow, а не каждую простую repository operation.

### Разбор каждой части Java-конфигурации

#### `@Configuration(proxyBeanMethods = false)`

Сообщает Spring, что класс объявляет beans. `proxyBeanMethods = false` подходит,
потому что методы этого класса не вызывают друг друга для получения beans; это
избавляет от ненужного CGLIB proxy самого configuration class.

#### `@EnableCaching`

Включает обработку `@Cacheable`, `@CacheEvict` и `@CachePut`. Без неё аннотации
на service останутся обычными metadata и ничего кешировать не будут.

Spring использует AOP proxy. Поэтому кешируемый метод должен быть `public` и
вызываться из другого Spring bean. Вызов `this.findForWorkspace(...)` внутри того
же объекта proxy не пересекает и кеш не включает.

#### `@Profile("redis")`

Создаёт configuration class только при активном profile `redis`. Это связывает
Java-конфигурацию с `application-redis.properties` и не позволяет случайно
создать Redis manager в обычном режиме.

#### Cache name constant

```java
public static final String WORKSPACE_PROJECT_ACCESS_OVERVIEW =
        "workspaceProjectAccessOverview";
```

Одинаковая константа должна использоваться в manager и в `@Cacheable`. Если в
строках сделать опечатку, `.disableCreateOnMissingCache()` не создаст новый кеш
с ошибочным именем, а ошибка будет заметна сразу.

#### `RedisConnectionFactory`

Это фабрика сетевых соединений с Redis. Она знает host, port, timeout,
credentials и TLS из properties. `CacheManager` не должен повторно читать эти
значения и самостоятельно создавать Redis client.

#### `StringRedisSerializer`

Преобразует Java cache key в обычную UTF-8 строку. Поэтому ключ можно прочитать
через `redis-cli`:

```text
collabdesk:v1:workspaceProjectAccessOverview::42:7
```

Без явного/стандартного String serializer ключ мог бы оказаться в неудобном
бинарном формате.

#### `JacksonJsonRedisSerializer<WorkspaceProjectAccessOverviewResponse>`

Преобразует конкретный response record в JSON и обратно. Typed serializer здесь
лучше generic serializer, потому что этот cache по архитектуре содержит ровно
один тип DTO:

- при чтении заранее известен ожидаемый Java type;
- не требуется unsafe polymorphic default typing;
- в JSON не нужно разрешать произвольные имена Java classes;
- ошибка записи другого типа обнаруживается раньше.

Serializer создаёт собственный mapper для Redis и не меняет HTTP
`ObjectMapper`, которым controllers формируют API responses. Если позже в Redis
появится cache с другим DTO, дай ему отдельную cache configuration и typed
serializer.

#### `defaultCacheConfig()`

Создаёт immutable базовую конфигурацию Spring Data Redis. Затем каждый chained
method возвращает её изменённую копию. Defaults сами по себе для этого этапа не
подходят: у них нет TTL, разрешён `null`, а value serializer использует JDK
serialization. Именно поэтому следующие строки обязательны.

#### `.entryTtl(Duration.ofMinutes(5))`

TTL расшифровывается как **Time To Live** — оставшееся время жизни конкретного
key. Значение `Duration.ofMinutes(5)` означает 300 секунд.

При записи Spring говорит Redis по смыслу:

```text
сохрани этот key и value
считай key недействительным через 300 секунд
```

У каждого key собственный таймер. Если overview пользователя 7 записан в 12:00,
а overview пользователя 8 — в 12:02, они истекут примерно в 12:05 и 12:07
соответственно.

Пример жизни одной entry:

```text
12:00:00  cache MISS, запись в Redis, TTL = 300
12:01:00  cache HIT, TTL примерно 240
12:03:00  cache HIT, TTL примерно 120
12:04:59  cache HIT, TTL примерно 1
12:05:00  key считается отсутствующим
12:05:01  новый request получает MISS
12:05:01  response строится из MySQL и записывается с новым TTL = 300
```

Обычный cache hit **не возвращает TTL обратно к 300 секундам**. Это TTL «с
момента записи», а не «с момента последнего чтения». Поведение, при котором
чтение продлевает жизнь, называется TTI — Time To Idle. На этом этапе TTI не
включаем.

Проверить оставшееся время:

```powershell
docker compose exec redis redis-cli TTL "полный-ключ"
```

Команда возвращает:

```text
положительное число -> осталось столько секунд
-1                  -> key существует, но expiration не задан
-2                  -> key отсутствует или уже истёк
```

Для этого cache значение `-1` является ошибкой конфигурации: overview не должен
жить бесконечно. Сразу после записи ожидается число от `1` до `300`.

Истёкший key больше не возвращается через `GET`. Внутренняя физическая очистка
памяти может выполняться Redis не в ту же микросекунду, но для клиента entry уже
считается отсутствующей.

#### Зачем нужен TTL, если есть инвалидация

В нормальном сценарии изменение проекта или ролей должно удалить cache entry
сразу после успешного DB commit:

```text
данные изменились
  -> AFTER_COMMIT event
  -> cache evict
  -> следующий GET строит свежий overview
```

TTL — второй уровень защиты. Например, разработчик добавил новую mutation, но
забыл опубликовать invalidation event, либо Redis был недоступен во время
`evict`. Без TTL старый response мог бы храниться бесконечно. С TTL он исчезнет
не позднее чем примерно через пять минут.

Следовательно:

```text
invalidation -> старается убрать устаревший кеш немедленно
TTL          -> ограничивает максимальную жизнь случайно оставшегося кеша
```

TTL не означает, что пользователь всегда ждёт пять минут обновления. При
правильной invalidation свежие данные появятся на следующем запросе.

#### TTL, eviction policy и persistence — разные вещи

Эти механизмы легко перепутать:

| Механизм | Почему entry исчезает | Где настроен |
|---|---|---|
| TTL expiration | Закончились её 300 секунд. | Java `RedisCacheConfiguration` |
| Explicit eviction | CollabDesk изменил данные и вызвал `@CacheEvict`. | Application events/cache annotations |
| `allkeys-lru` eviction | Redis достиг `maxmemory` и освобождает память. | `docker-compose.yaml` Redis server |
| Потеря container | Локальный cache-only Redis перезапущен/удалён без persistence. | Docker lifecycle |

`allkeys-lru` может удалить entry раньше её TTL, если Redis заполнен. Это не
ошибка: следующий запрос получит MISS и восстановит value из MySQL.

Отключённая persistence (`--save ""`) тоже не связана с TTL. Persistence
отвечает за восстановление данных после перезапуска server, а TTL — за срок
жизни key в работающем server.

#### Почему выбрано пять минут

Пять минут — стартовый компромисс:

- достаточно долго, чтобы повторные открытия workspace чаще давали HIT;
- достаточно коротко, чтобы ошибка invalidation не оставляла старый overview
  надолго;
- cache entries не накапливаются бесконечно.

Это не универсальная константа Redis. После появления метрик значение можно
изменить: меньший TTL даёт более свежую страховку, но больше запросов к MySQL;
больший TTL повышает hit rate, но увеличивает возможный период устаревания при
ошибке invalidation.

#### `.disableCachingNullValues()`

Запрещает сохранять `null`. Временный `null` не должен превращаться в
пятиминутный ложный ответ. На `@Cacheable` дополнительно остаётся
`unless = "#result == null"`; это защита на двух уровнях.

#### `.computePrefixWith(...)`

Полный Redis key собирается из трёх частей:

```text
collabdesk:v1:                       application/version prefix
workspaceProjectAccessOverview::    cache name
42:7                                @Cacheable key
```

Version `v1` позволяет в будущем изменить JSON schema или правила ключей и
перейти на `v2`, не пытаясь десериализовать старые entries.

Не используй только `.prefixCacheNameWith("collabdesk:v1:")`, не понимая
результат: всегда проверь фактический ключ через `SCAN`, чтобы разделители не
получились двойными или отсутствующими.

#### `.serializeKeysWith(...)` и `.serializeValuesWith(...)`

Первая строка выбирает кодирование логического ключа, вторая — всего DTO. Redis
в конечном счёте хранит bytes; serializers определяют, как превратить Java
objects в эти bytes и восстановить их при cache hit.

#### `.withCacheConfiguration(...)`

Регистрирует настройки именно для cache
`workspaceProjectAccessOverview`. TTL и typed value serializer не становятся
случайными глобальными defaults для будущих кешей.

#### `.disableCreateOnMissingCache()`

Запрещает автоматически создавать неизвестные cache names во время работы. Это
полезно сейчас, когда разрешён ровно один cache: опечатка в `@Cacheable` не
создаст молча второй cache с JDK defaults.

#### Почему здесь нет `.transactionAware()`

Очистка кеша уже запускается обработчиком
`@TransactionalEventListener(AFTER_COMMIT)`, то есть только после успешного
commit MySQL-транзакции. Если добавить `.transactionAware()`, cache manager
попытается ещё раз отложить `clear`, хотя commit уже завершён. В результате
очистка может не выполниться в нужный момент. Поэтому ответственность разделена
просто: событие отвечает за ожидание commit, а cache manager сразу выполняет
полученную после commit команду очистки.

#### `.build()`

Создаёт единственный `RedisCacheManager` bean. Когда cache interceptor встречает
`@Cacheable`, он получает cache по имени у этого manager и выполняет lookup,
put или evict через `RedisConnectionFactory`.

### Что настраивается где

| Уровень | Отвечает за | Примеры |
|---|---|---|
| Docker Redis server | Память и поведение процесса Redis | `maxmemory`, `allkeys-lru`, persistence, port mapping |
| `application-redis.properties` | Сетевое подключение Spring к Redis | host, port, connect/read timeout, password, TLS |
| Java `RedisCacheManager` | Правила конкретного application cache | cache name, TTL, prefix, serializers, null policy, transaction awareness |
| `@Cacheable` на service | Что именно и под каким логическим ключом кешировать | method result, `workspaceId:currentUserId` |

Ни один из этих уровней не заменяет остальные. Docker может исправно отвечать
`PONG`, но без profile/configuration/`@Cacheable` приложение ничего туда не
запишет.

## 8. Архитектура read path

Раздели ответственность на три Spring beans:

```text
WorkspaceProjectAccessOverviewService
├── всегда проверяет актуальный workspace membership
└── после проверки вызывает кешируемый bean

WorkspaceProjectAccessOverviewCacheService
├── содержит @Cacheable
└── при cache miss вызывает query service

WorkspaceProjectAccessOverviewQueryService
└── собирает актуальный response из MySQL существующим batch-flow
```

Три класса нужны не ради названий, а из-за поведения Spring proxy. На cache hit
тело `@Cacheable` метода не исполняется вообще. Если membership-проверка будет
внутри этого тела, кеш позволит её пропустить. Некешируемый facade гарантирует
проверку перед каждым lookup.

### 1. Публичный facade с обязательной проверкой доступа

```java
package collabdesk.workspace.access.service;

import collabdesk.project.dto.WorkspaceProjectAccessOverviewResponse;
import collabdesk.workspace.service.WorkspaceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

Controller продолжает вызывать этот service. Даже при cache hit facade сначала
читает актуальный membership из MySQL. Удалённый участник не получит старый
персональный response.

### 2. Отдельный кешируемый bean

```java
package collabdesk.workspace.access.service;

import collabdesk.project.dto.WorkspaceProjectAccessOverviewResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import static collabdesk.infrastructure.cache.CacheConfiguration.WORKSPACE_PROJECT_ACCESS_OVERVIEW;

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

Разбор `@Cacheable`:

| Параметр | Что означает |
|---|---|
| `cacheNames` | Просит `CacheManager` использовать заранее зарегистрированный cache. |
| `key` | SpEL собирает логический ключ, например `42:7`. Оба id обязательны для изоляции пользовательских результатов. |
| `unless` | Проверяется после выполнения метода и запрещает записать `null`. На cache hit метод вообще не выполняется. |

При первом вызове proxy ищет ключ. Если ключа нет, вызывается query service и
результат записывается. При втором вызове с теми же аргументами proxy возвращает
десериализованный DTO без вызова query service.

`WorkspaceProjectAccessOverviewCacheService` должен быть отдельным `@Service`.
Если facade вызовет собственный `this.cachedMethod(...)`, это будет
self-invocation без пересечения Spring proxy, и `@Cacheable` не сработает.

### 3. Query service

Создай:

```text
collabdesk.workspace.access.service.WorkspaceProjectAccessOverviewQueryService
```

Перенеси в него текущее тело
`WorkspaceProjectAccessOverviewService.findForWorkspace()`: загрузку доступных
projects, batch-загрузку members/roles и mapping response. Метод оставь
`@Transactional(readOnly = true)`. Query service ничего не знает о Redis и
всегда строит актуальный DTO из MySQL.

В режиме без profile `redis` `@EnableCaching` не активирован. Поэтому тот же
cache service становится обычным delegate: каждый вызов доходит до query
service. Не нужны `if (redisEnabled)` в application code.

Порядок чтения:

```text
HTTP request
  -> authentication
  -> workspace membership validation
  -> cache lookup by workspaceId + currentUserId
     -> HIT: вернуть DTO
     -> MISS: собрать DTO из MySQL, записать в Redis, вернуть DTO
```

Даже cache hit не должен позволять обойти базовую проверку существования
workspace membership. Это защищает сценарий, когда участника удалили, а его
старый персональный cache entry ещё существует.

## 9. Поведение при недоступном Redis

По умолчанию Spring использует `SimpleCacheErrorHandler`: cache exception
возвращается вызывающему коду и может сломать HTTP request. Для cache-aside
CollabDesk нужно другое поведение — записать warning и не пробрасывать Redis
exception.

Создай:

```text
collabdesk.infrastructure.cache.LenientRedisCacheErrorHandler
```

```java
package collabdesk.infrastructure.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

public class LenientRedisCacheErrorHandler implements CacheErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(
            LenientRedisCacheErrorHandler.class
    );

    @Override
    public void handleCacheGetError(
            RuntimeException exception,
            Cache cache,
            Object key
    ) {
        warn("get", cache, key, exception);
    }

    @Override
    public void handleCachePutError(
            RuntimeException exception,
            Cache cache,
            Object key,
            Object value
    ) {
        warn("put", cache, key, exception);
    }

    @Override
    public void handleCacheEvictError(
            RuntimeException exception,
            Cache cache,
            Object key
    ) {
        warn("evict", cache, key, exception);
    }

    @Override
    public void handleCacheClearError(
            RuntimeException exception,
            Cache cache
    ) {
        warn("clear", cache, null, exception);
    }

    private void warn(
            String operation,
            Cache cache,
            Object key,
            RuntimeException exception
    ) {
        log.warn(
                "Redis cache operation failed: operation={}, cache={}, "
                        + "key={}, exception={}",
                operation,
                cache.getName(),
                key,
                exception.getClass().getSimpleName()
        );
        log.debug("Redis cache failure details", exception);
    }
}
```

Здесь методы намеренно не делают `throw exception`. Последствия:

- на ошибке `get` пишет warning и продолжает как при cache miss;
- на ошибке `put` возвращает уже построенный MySQL response;
- на ошибке `evict` пишет warning, не откатывая основную бизнес-транзакцию;
- не скрывает ошибки MySQL, authorization и основного application service.

Не логируй аргумент `value`: там находится полный overview с данными
пользователей. Логический key содержит только ids и допустим для диагностики.
Stack trace оставлен на `debug`, чтобы обычный outage не заполнял production log
одинаковыми большими traces.

Одного класса недостаточно: cache infrastructure должна выбрать его вместо
default handler. В `CacheConfiguration`:

1. добавь `implements CachingConfigurer`;
2. добавь imports;
3. объяви handler через override method.

```java
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;

public class CollabDeskRedisCacheConfiguration
        implements CachingConfigurer {

    // constants и cacheManager(...) остаются как выше

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new LenientRedisCacheErrorHandler();
    }
}
```

`CachingConfigurer` сообщает interceptor, какой handler использовать. Просто
создать произвольный объект через `new` в service или ловить Redis exceptions в
controller не нужно.

Результат:

```text
Redis доступен    -> используется кеш
Redis недоступен  -> endpoint медленнее, но работает через MySQL
MySQL недоступен  -> обычная ошибка приложения, кеш её не маскирует
```

Не делай бесконечные retry внутри HTTP request. Connect/read timeouts должны
оставаться короткими, чтобы сбой Redis не превращался в долгую зависшую загрузку
workspace.

## 10. Инвалидация

Любое изменение данных, входящих в overview или влияющих на его видимость,
публикует событие:

```java
public record WorkspaceProjectAccessChangedEvent(Long workspaceId) {
}
```

Обработчик слушает событие после успешного commit:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
```

В первой версии очищай весь cache name:

```java
@CacheEvict(
        cacheNames = "workspaceProjectAccessOverview",
        allEntries = true
)
```

Это шире, чем очистка одного workspace, но безопасно и предсказуемо. В cache
есть персональные ключи всех участников, а стандартная Spring Cache abstraction
не предоставляет portable eviction по prefix. Не используй Redis `KEYS` в
request flow и не добавляй сложный индекс ключей на первом этапе.

Позже broad eviction можно заменить versioned workspace keys или отдельным
индексом, когда метрики покажут, что это действительно необходимо.

## 11. Когда публиковать invalidation event

Публикуй `WorkspaceProjectAccessChangedEvent` после операций:

### Projects

- создание проекта;
- изменение name или description;
- изменение status;
- изменение visibility;
- архивирование или удаление проекта, если эти операции появятся.

### Project access

- добавление `ProjectMember`;
- удаление `ProjectMember`;
- replace назначенных custom roles;
- переход между custom role и direct access.

### Workspace members

- удаление участника из workspace;
- изменение его системной `WorkspaceRole`;
- удаление workspace, когда такая операция будет реализована.

### Custom roles

- создание роли, если frontend сразу показывает её в доступных назначениях;
- изменение name, color или permissions;
- удаление роли;
- изменение назначений роли в проектах.

Не инвалидируй overview после:

- изменения задачи;
- смены task assignee;
- изменения task status;
- чтения профиля;
- повторного GET overview.

Task data не входит в response этапа 15.

## 12. Почему invalidation выполняется after commit

Не очищай кеш до завершения транзакции:

```text
1. DB update начинается
2. кеш очищается
3. параллельный GET читает старые DB-данные и снова заполняет кеш
4. DB transaction commit
5. в кеше остаётся старый response
```

При `AFTER_COMMIT` invalidation выполняется только когда новые данные уже видны
другим транзакциям. Если transaction rollback, актуальный cache entry не
очищается без необходимости.

Transaction-aware `RedisCacheManager` оставь дополнительной защитой для cache
operations, выполненных внутри транзакций, но не используй его как замену
понятному application event.

## 13. Frontend

Frontend продолжает вызывать тот же endpoint и получать тот же DTO. На этом
этапе не добавляй в React:

- ручное знание Redis keys;
- отдельный Redis status indicator;
- cache-busting query parameters;
- логику permissions на основе возраста кеша.

После сохранения project access frontend по-прежнему локально обновляет
полученный response или повторно загружает overview. Backend invalidation нужна
для других вкладок, пользователей и последующих запросов.

## 14. OpenAPI

Схема endpoint и DTO не меняется, поэтому Redis не должен появляться в OpenAPI
как часть публичного контракта.

Не добавляй обязательные cache headers. Это server-side cache, а не обещание
клиенту хранить response определённое время.

Существующий `OpenApiIntegrationTest` должен продолжить проходить без изменения
response schema.

## 15. Tests

### Unit

Проверь:

- cache key содержит `workspaceId` и `currentUserId`;
- разные пользователи одного workspace получают разные ключи;
- `null` не кешируется;
- mutation service публикует событие правильного workspace;
- rollback не запускает after-commit invalidation handler;
- Redis error handler не скрывает ошибку query service.

### Redis integration

Запускай настоящий Redis через Testcontainers. Не подменяй Redis H2, Map cache
или mock-классом в тестах сериализации и TTL.

Проверь:

1. первый запрос создаёт cache entry;
2. второй идентичный запрос возвращается из кеша;
3. ключи двух пользователей различаются;
4. MEMBER не получает cached overview владельца;
5. restricted project не появляется у пользователя без доступа;
6. value записывается в JSON, а не JDK binary serialization;
7. у ключа установлен положительный TTL не больше configured TTL;
8. project mutation очищает кеш после commit;
9. project access mutation очищает кеш после commit;
10. custom role update очищает кеш после commit;
11. rollback сохраняет прежний актуальный cache entry;
12. при остановленном Redis endpoint возвращает данные из MySQL.

Для проверки cache hit отдели кешируемый facade от query builder: тогда тест
может посчитать вызовы builder без подсчёта Hibernate SQL и без хрупкой привязки
к внутренним repository queries.

### Existing suites

Проверь:

```powershell
./mvnw.cmd test
npm.cmd run lint
npm.cmd run build
```

Обычный Maven test suite не должен требовать установленный на машине Redis.
Redis integration tests сами поднимают Testcontainer и могут быть пропущены с
понятной причиной только когда Docker недоступен в окружении.

## 16. Диагностика

Логируй cache failures без Redis credentials и без полного содержимого DTO:

```text
cache name
operation: get / put / evict
workspace id, если он доступен
exception type
```

Не логируй:

- access token;
- Redis password;
- email всех участников;
- полный cached response.

На первом этапе достаточно структурированных warning logs для ошибок. Hit/miss
metrics и dashboard можно добавить позже вместе со Spring Boot Actuator, если
появится задача на observability.

## 17. Критерии завершения

- [ ] Redis подключён через Spring Cache abstraction.
- [ ] Приложение по умолчанию запускается без Redis.
- [ ] Профиль `redis` включает Redis cache configuration.
- [ ] Локальный Redis запускается через Docker Compose.
- [ ] Кешируется только `WorkspaceProjectAccessOverviewResponse`.
- [ ] Cache key содержит workspace id и current user id.
- [ ] Values сериализуются в JSON.
- [ ] `null` и ошибки не кешируются.
- [ ] TTL для overview равен 5 минутам.
- [ ] Redis имеет ограничение памяти и eviction policy.
- [ ] Permission-проверки не зависят от кеша.
- [ ] Удалённый workspace member не может использовать старый cache entry.
- [ ] Все влияющие mutation flows публикуют invalidation event.
- [ ] Инвалидация выполняется после успешного transaction commit.
- [ ] Redis outage не ломает overview endpoint.
- [ ] Restricted projects не раскрываются через кеш.
- [ ] OpenAPI response не изменился.
- [ ] Redis integration tests проходят через Testcontainers.
- [ ] Полный Maven suite проходит.
- [ ] Frontend lint и production build проходят.

## Что пока не делать

- не переносить source of truth из MySQL в Redis;
- не принимать authorization-решения по cached DTO;
- не кешировать JPA entities;
- не добавлять Redis repositories;
- не кешировать все endpoints сразу;
- не хранить authentication sessions в Redis;
- не добавлять distributed locks;
- не добавлять Redis Pub/Sub;
- не включать AOF или RDB persistence для локального cache-only Redis;
- не выполнять `KEYS`, `FLUSHALL` или `FLUSHDB` из приложения;
- не добавлять ручной cache state во frontend;
- не усложнять первую версию targeted eviction без подтверждённой проблемы.

Следующим отдельным этапом после стабилизации кеша можно сделать observability:
Actuator, cache hit/miss metrics, latency overview до и после Redis и решение,
нужна ли точечная инвалидация по workspace.

## Справочные материалы

- [Spring Data Redis Cache](https://docs.spring.io/spring-data/redis/reference/redis/redis-cache.html)
- [Spring Data Redis JSON serializers](https://docs.spring.io/spring-data/redis/docs/current/api/org/springframework/data/redis/serializer/package-summary.html)
- [Spring Boot Caching](https://docs.spring.io/spring-boot/reference/io/caching.html)
- [Spring Framework cache annotations](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html)
- [Spring Boot Profiles](https://docs.spring.io/spring-boot/reference/features/profiles.html)
- [Spring Boot Common Application Properties](https://docs.spring.io/spring-boot/appendix/application-properties/)
- [Redis configuration](https://redis.io/docs/latest/operate/oss_and_stack/management/config/)
- [Redis eviction policies](https://redis.io/docs/latest/develop/reference/eviction/)
- [Redis with Docker](https://redis.io/docs/latest/operate/oss_and_stack/install/install-stack/docker/)
