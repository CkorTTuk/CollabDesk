# Этап 19C. Личный кабинет, профиль и способы входа — подробный маршрут реализации

Этот файл подробно раскрывает шаги 13–15 из
`19-account-onboarding-github-and-security.md`. Он привязан к текущему состоянию
CollabDesk после 19B и объясняет не только какие файлы создать, но и почему
границы проходят именно так, какие инварианты нужно сохранить и в каком порядке
писать тесты.

Цель 19C — получить самостоятельную страницу `/account`, на которой вошедший и
завершивший onboarding пользователь может:

- увидеть безопасное представление своего аккаунта;
- изменить имя, фамилию и дату рождения;
- выбрать язык `en`, `ru` или `sk`;
- загрузить, заменить и удалить собственный аватар;
- увидеть подключённые способы входа;
- безопасно начать привязку Google или GitHub к текущему аккаунту.

Удаление способа входа проектируется сейчас, но публичный `DELETE` нельзя
включать до шага 17: исходный план требует недавнего step-up подтверждения, а
обычная старая HTTP-сессия для unlink недостаточна.

---

## Текущая точка проекта

На момент начала 19C уже реализованы:

- общая таблица `users` и identities `LOCAL`, `GOOGLE`, `GITHUB`;
- поля `first_name`, `last_name`, `birth_date`, `avatar_key`,
  `email_verified_at`, `onboarding_completed_at` из `V12`;
- `User.changeProfile(...)` и `User.changeAvatar(...)`;
- общий `CollabDeskPrincipal` для LOCAL, Google и GitHub;
- session authentication, CSRF и authority `PROFILE_COMPLETE`;
- `/api/v1/auth/me` и общий onboarding;
- подтверждение локального email из 19B;
- React без router-библиотеки: основной экран пока переключается локальным
  состоянием внутри большого `App.jsx`;
- frontend-тексты сейчас в основном захардкожены на английском.

Перед первым изменением запусти baseline:

```powershell
.\mvnw.cmd test
cd frontend
npm.cmd run lint
npm.cmd run build
```

Если baseline красный, сначала зафиксируй конкретную старую ошибку. В конце 19C
те же команды должны быть зелёными.

---

## 1. Граница этапа

### Входит в 19C

1. Миграция `preferred_locale`.
2. Read model личного кабинета и список providers.
3. Изменение профиля через domain method `User.changeProfile(...)`.
4. Обновление principal текущей session после смены display name.
5. Небольшой frontend i18n-слой без обязательной внешней библиотеки.
6. Синхронизация гостевого locale из `localStorage` с аккаунтом.
7. Storage abstraction для аватаров и локальная реализация.
8. Проверка размера, формата, magic bytes и декодируемости изображения.
9. Безопасная выдача аватара по opaque key, без раскрытия filesystem path.
10. Просмотр подключённых identities.
11. Отдельный OAuth linking intent, привязывающий provider к уже вошедшему User.
12. Unit, repository, MVC/integration и frontend-проверки.

### Не входит в 19C

- смена основного email;
- смена/создание пароля;
- TOTP, recovery codes и phone verification;
- security event log из шага 16;
- step-up challenges из шага 17;
- фактический unlink до появления step-up;
- S3/MinIO/CDN и фоновые image jobs;
- публичные профили других пользователей;
- полный перевод workspace/project/task интерфейса.

Последний пункт важен: на 19C переведи auth, email verification, onboarding,
account и общую навигацию к account. Не превращай этап в переписывание всего
dashboard.

---

## 2. Целевые HTTP-контракты

Используй один канонический account endpoint, чтобы экран не собирал профиль из
`/auth/me`, onboarding и identities тремя несогласованными запросами.

```text
GET    /api/v1/account
PATCH  /api/v1/account/profile
PATCH  /api/v1/account/locale
POST   /api/v1/account/avatar
DELETE /api/v1/account/avatar
GET    /api/v1/avatars/{avatarKey}
GET    /api/v1/account/identities
POST   /api/v1/account/identities/{provider}/link/start

ЗАРЕЗЕРВИРОВАНО ДО ШАГА 17:
DELETE /api/v1/account/identities/{provider}
```

Все account endpoints требуют authenticated session и
`PROFILE_COMPLETE`. Все изменяющие запросы требуют CSRF. Avatar upload использует
`multipart/form-data`; CSRF header всё равно обязателен.

### Рекомендуемый `AccountResponse`

```json
{
  "id": 42,
  "email": "user@example.com",
  "displayName": "Ada Lovelace",
  "firstName": "Ada",
  "lastName": "Lovelace",
  "birthDate": "1815-12-10",
  "avatarUrl": "/api/v1/avatars/2f1a...c9.png",
  "emailVerified": true,
  "preferredLocale": "en",
  "providers": ["LOCAL", "GITHUB"]
}
```

Не включай в response:

- `passwordHash`;
- `providerSubject`;
- OAuth access/refresh token;
- verification challenge id или code hash;
- абсолютный путь к avatar storage;
- будущие TOTP secret и recovery codes;
- JPA entity целиком.

`providers` удобен для первого render account page. Отдельный identities endpoint
оставь, потому что позднее он получит дополнительные безопасные признаки:
`connectedAt`, `canUnlink`, `requiresStepUp`.

### `UpdateProfileRequest`

```java
public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String firstName,
        @Nullable @Size(max = 100) String lastName,
        @Nullable @PastOrPresent LocalDate birthDate
) {
    public UpdateProfileRequest {
        firstName = firstName == null ? null : firstName.strip();
        lastName = lastName == null || lastName.isBlank()
                ? null
                : lastName.strip();
    }
}
```

DTO-валидация даёт понятные field errors, а `User.changeProfile(...)` остаётся
последней защитой доменных правил. Нельзя переносить все правила только в DTO:
entity может вызываться не только из HTTP.

### Статусы ошибок

| Сценарий | HTTP | Stable code |
|---|---:|---|
| session отсутствует | 401 | тело может быть пустым из security filter |
| onboarding не завершён | 403 | доступ блокирует authority |
| validation profile/locale | 400 | `errors` по полям |
| account disabled/not found | 403 | `account_unavailable` |
| avatar больше лимита | 413 | `avatar_too_large` |
| неизвестный/повреждённый image | 422 | `invalid_avatar` |
| provider `LOCAL` в OAuth link | 400 | `provider_not_linkable` |
| provider уже есть у этого User | 409 | `identity_already_connected` |
| внешняя identity принадлежит другому User | 409 | `identity_connection_conflict` |
| link callback без/с просроченным intent | OAuth failure redirect | `link_intent_missing` |

Не возвращай различающиеся подробности, по которым можно узнать чужой provider
subject или состав чужого аккаунта.

---

## 3. Целевая архитектура и файлы

```text
src/main/java/collabdesk/account/
    AccountController.java
    AccountQueryService.java
    AccountProfileService.java
    AccountResponse.java
    UpdateProfileRequest.java
    UpdateLocaleRequest.java
    ConnectedIdentityResponse.java
    AccountUnavailableException.java

src/main/java/collabdesk/account/avatar/
    AvatarController.java
    AvatarService.java
    AvatarStorage.java
    LocalAvatarStorage.java
    AvatarValidator.java
    ValidatedAvatar.java
    StoredAvatar.java
    AvatarContent.java
    AvatarUrlFactory.java
    AvatarProperties.java
    InvalidAvatarException.java
    AvatarTooLargeException.java

src/main/java/collabdesk/account/linking/
    IdentityLinkController.java
    IdentityLinkStartResponse.java
    IdentityLinkIntent.java
    IdentityLinkIntentService.java
    IdentityLinkService.java
    OAuthFlowContextResolver.java
    IdentityLinkOAuth2Principal.java
    IdentityLinkOAuth2SuccessHandler.java
    IdentityLinkException.java

frontend/src/account/
    AccountScreen.jsx
    ProfileSection.jsx
    AvatarSection.jsx
    SignInMethodsSection.jsx
    SecurityPlaceholderSection.jsx
    DangerZonePlaceholderSection.jsx

frontend/src/i18n/
    I18nProvider.jsx
    locale.js
    messages.en.js
    messages.ru.js
    messages.sk.js

frontend/src/api/
    accountApi.js
```

Не обязательно дробить React именно на это количество файлов, но не добавляй
ещё сотни строк account UI прямо в уже большой `App.jsx`. `App.jsx` должен
выбирать экран и владеть session user; account components — своей формой.

Поток слоёв:

```text
AccountController
    -> AccountQueryService -> UserRepository + AuthIdentityRepository
    -> AccountProfileService -> User.changeProfile/changeLocale
                               -> refresh current SecurityContext principal

AvatarController
    -> AvatarService -> AvatarStorage
                     -> User.changeAvatar
                     -> cleanup after commit/rollback

IdentityLinkController
    -> IdentityLinkIntentService -> HTTP session
    -> Spring OAuth authorization endpoint
callback
    -> verified Google/GitHub principal data
    -> IdentityLinkService -> AuthIdentityRepository
    -> dedicated success/failure redirect
```

---

# Шаг 13. Account API, профиль и язык

## 13.1. Добавить `preferred_locale` миграцией V14

Не изменяй `V12`: она уже применялась и её checksum нельзя менять. Создай:

```text
src/main/resources/db/migration/V14__add_user_preferred_locale.sql
```

Минимальная миграция:

```sql
ALTER TABLE users
    ADD COLUMN preferred_locale VARCHAR(10) NOT NULL DEFAULT 'en';

ALTER TABLE users
    ADD CONSTRAINT users_preferred_locale_ck
        CHECK (preferred_locale IN ('en', 'ru', 'sk'));
```

Почему хранить строку, а не ordinal enum:

- `en`, `ru` и `sk` — стандартные стабильные locale tags;
- ordinal меняет смысл при перестановке Java enum;
- frontend и backend говорят на одном контракте;
- позже можно миграцией расширить CHECK до `de`, `sk` и других значений.

Добавь в `User` поле с default для новых Java-объектов и domain method:

```text
preferredLocale = "en"
changePreferredLocale(String locale)
```

В методе разрешай только поддерживаемые канонические значения. Лучше создать
`SupportedLocale` enum с `fromTag(...)`, чем размножать `if ("en"...)` по
controller, entity и frontend.

Проверь миграцию двумя тестами:

1. Старая строка получает `en`.
2. MySQL отклоняет произвольное значение вроде `english`.

## 13.2. Расширить repository только нужными запросами

Для account query нужны:

```java
List<AuthIdentity> findAllByUser_IdOrderByProviderAsc(Long userId);

@Query("select identity.provider from AuthIdentity identity " +
       "where identity.user.id = :userId order by identity.provider")
List<AuthProvider> findProvidersByUserId(@Param("userId") Long userId);

long countByUser_Id(Long userId);
```

Не обращайся к lazy `identity.getUser()` после закрытия транзакции. Для списка
providers сам `User` вообще не требуется.

## 13.3. Создать read model аккаунта

`AccountQueryService.getAccount(userId)` должен:

1. загрузить свежий `User` из БД;
2. убедиться, что status `ACTIVE` и onboarding завершён;
3. загрузить providers отдельным projection query;
4. построить `avatarUrl` только из `avatarKey`;
5. вернуть immutable DTO.

Почему нельзя просто вернуть данные из principal: principal — snapshot на момент
login. После изменения профиля в другой вкладке он может быть старым, а account
page должна читать текущее состояние из БД.

`avatarUrl` строит отдельный `AvatarUrlFactory` или приватный mapper. Никогда не
делай `"file:///" + storagePath` и не возвращай физический путь.

## 13.4. Реализовать PATCH профиля

`AccountProfileService.updateProfile(...)` выполняется в одной транзакции:

```text
load active User by principal.userId
    -> user.changeProfile(firstName, lastName, birthDate)
    -> saveAndFlush
    -> построить свежий AccountResponse
    -> заменить principal текущего Authentication
    -> вернуть response
```

`displayName` не принимай отдельным полем: текущая entity вычисляет его из имени
и фамилии. Иначе появятся три несогласованных имени.

Учитывай optimistic locking через `User.version`. Два параллельных PATCH не
должны молча затереть друг друга. Для первого варианта достаточно преобразовать
`ObjectOptimisticLockingFailureException` в `409 profile_update_conflict` и
предложить frontend перезагрузить данные. Не делай автоматический blind retry.

### Обновление текущей session

После PATCH header/sidebar использует `user.displayName`, а authorities и
security state берутся из principal. Поэтому нужен общий компонент наподобие:

```text
CurrentAuthenticationRefresher.refresh(User, Authentication)
```

Вынеси в него уже существующую логику из `OnboardingService.refreshPrincipal`
и `replaceAuthentication`, чтобы onboarding и account profile не содержали две
копии LOCAL/Google/GitHub branching.

При замене Authentication:

- сохрани тип OAuth token и registration id;
- сохрани details;
- пересчитай authorities из нового principal;
- не восстанавливай стёртый password hash;
- сохрани новый SecurityContext через настроенный repository/session.

После успешного PATCH response уже содержит новое `displayName`; frontend обязан
обновить верхнеуровневый `user`, а не ждать нового login.

## 13.5. Реализовать locale endpoint

Используй отдельный DTO:

```json
{ "locale": "ru" }
```

Отдельный endpoint лучше общего profile PATCH: переключатель языка сохраняется
сразу и не может случайно отправить устаревший draft имени.

Алгоритм:

```text
validate locale
load active User
user.changePreferredLocale(locale)
save
return AccountResponse (или компактный LocaleResponse)
```

Рекомендуется вернуть полный `AccountResponse`: frontend одним действием
синхронизирует account state и глобальный session user.

## 13.6. SecurityConfig

Текущий catch-all уже требует `PROFILE_COMPLETE` для `/api/v1/**`, поэтому
account endpoints автоматически закрыты. Всё равно добавь MVC security tests,
чтобы будущая перестановка matcher-ов не открыла их случайно:

- anonymous GET `/api/v1/account` -> 401;
- authenticated, incomplete onboarding -> 403;
- complete profile -> 200;
- PATCH без CSRF -> 403;
- PATCH с CSRF -> success.

Не добавляй account endpoints в `permitAll()`.

## 13.7. Frontend i18n foundation

На этом проекте достаточно маленького собственного слоя; новая библиотека не
обязательна. Сделай один источник истины:

```javascript
export const SUPPORTED_LOCALES = ['en', 'ru', 'sk']
export const DEFAULT_LOCALE = 'en'

export function normalizeLocale(value) {
  const base = String(value ?? '').trim().toLowerCase().split('-')[0]
  return SUPPORTED_LOCALES.includes(base) ? base : DEFAULT_LOCALE
}
```

`I18nProvider` хранит locale и отдаёт:

```text
locale
setLocale(nextLocale)
t(key, params?)
formatDate(value, options?)
formatNumber(value, options?)
```

Словари должны иметь одинаковую плоскую структуру:

```javascript
export const en = {
  'auth.signIn.title': 'Sign in to CollabDesk',
  'account.profile.title': 'Profile',
}

export const ru = {
  'auth.signIn.title': 'Войти в CollabDesk',
  'account.profile.title': 'Профиль',
}
```

При отсутствующем key в development бросай заметную ошибку или логируй её и
показывай key; в production безопасно fallback на английский. Нельзя молча
получать пустую кнопку.

### Выбор initial locale

Порядок до login:

```text
localStorage collabdesk.locale
    -> base language navigator.language
    -> en
```

После login/account load серверный `preferredLocale` становится источником
истины. Исключение: если сервер всё ещё содержит default `en`, а пользователь до
первого login явно выбрал `ru`, один раз отправь `PATCH /account/locale` и затем
считай сервер авторитетным. Чтобы отличать explicit choice от browser default,
храни рядом флаг `collabdesk.localeExplicit=true`.

Простой и предсказуемый вариант синхронизации:

```text
guest выбирает ru
    -> localStorage locale=ru, explicit=true
login завершён
    -> GET /account возвращает en
    -> PATCH locale=ru
    -> очистить explicit marker
следующие sessions
    -> серверный locale копируется в localStorage
```

Если PATCH не удался, UI остаётся на выбранном языке, показывает ненавязчивую
ошибку и повторяет сохранение только по явному действию пользователя. Не создавай
бесконечный `useEffect` retry loop.

### Форматирование

Используй:

```javascript
new Intl.DateTimeFormat(locale, options).format(date)
new Intl.NumberFormat(locale, options).format(number)
```

Не переводить даты заменой строк и не собирать локализованные числа вручную.
Значение `<input type="date">` остаётся ISO `YYYY-MM-DD`: это transport value,
а не отображаемая локализованная дата.

## 13.8. Создать `/account` без тяжёлого router migration

Текущий frontend не использует React Router. Для 19C можно сделать маленькое
route state решение:

```text
window.location.pathname === '/account' -> AccountScreen
иначе -> Dashboard
```

При переходе используй History API и слушай `popstate`, чтобы Back работал.
Однако не строй второй самодельный router для вложенных workspace routes. Если
сразу планируется несколько URL, тогда отдельно добавь React Router и покрой
navigation tests.

`AccountScreen` содержит полноценные секции:

```text
Profile
Avatar
Language
Sign-in methods
Security (placeholder до 19D)
Danger zone (placeholder до 19E)
```

Для profile form:

- draft и server state хранятся раздельно;
- Save disabled, пока нет изменений или идёт запрос;
- field errors берутся из `ApiError.fieldErrors`;
- после success обновляется account state и `App` user state;
- Cancel возвращает значения последнего успешного response;
- birth date может быть `null`;
- при 409 предложи Reload, не затирай draft без предупреждения.

## 13.9. Согласовать `/auth/me` и account bootstrap

Сейчас `App.restoreSession()` вызывает только `/api/v1/auth/me`, а его response
не содержит `preferredLocale` и `avatarUrl`. Не создавай два конкурирующих
формата `user` в React без явного правила.

Рекомендуемый bootstrap:

```text
GET /auth/me
    -> 401: guest UI
    -> onboardingCompleted=false: оставить current-user DTO и открыть onboarding
    -> onboardingCompleted=true: GET /account и сохранить AccountResponse в App
```

После login и email verification применяй ту же функцию `loadSessionAccount()`,
а не копируй два запроса в трёх handlers. После завершения onboarding сначала
получи `/account`, затем открывай dashboard.

Так `/auth/me` остаётся лёгким session-state endpoint, incomplete User не
натыкается на `PROFILE_COMPLETE` у `/account`, а completed UI всегда получает
locale/avatar/providers из канонического response. Альтернатива — расширить все
principal types locale и avatar, но она сильнее увеличивает snapshot и требует
обновлять его после каждой avatar mutation. Для текущего проекта query после
`/auth/me` проще и надёжнее.

Не запускай `/auth/me` и `/account` параллельно: второй запрос нельзя корректно
решить до знания `onboardingCompleted`.

### Готовность шага 13

- `V14` работает на чистой и существующей БД;
- account GET не раскрывает secrets;
- profile PATCH использует domain method и обновляет session principal;
- locale переживает logout/login и имеет безопасный fallback;
- auth/onboarding/account имеют `en`, `ru` и `sk` без неизвестных ключей;
- account URL открывается и Back возвращает dashboard;
- backend tests, lint и build зелёные.

---

# Шаг 14. Безопасный аватар

## 14.1. Сначала определить модель хранения

В `users.avatar_key` хранится только opaque key:

```text
2f1ad5d7-1b2f-4f42-93de-a7379ca64cc9.png
```

Файл хранится вне classpath и вне frontend `public`. Никогда не используй
оригинальное filename пользователя как storage key.

Настройки:

```properties
app.avatar.local-directory=${AVATAR_LOCAL_DIRECTORY:./data/avatars}
app.avatar.max-bytes=${AVATAR_MAX_BYTES:5242880}
app.avatar.max-width=${AVATAR_MAX_WIDTH:4096}
app.avatar.max-height=${AVATAR_MAX_HEIGHT:4096}
```

`./data/avatars` добавь в `.gitignore`. В production directory должен быть
persistent volume, а не ephemeral container filesystem.

`AvatarStorage` не знает о `User` и HTTP:

```java
public interface AvatarStorage {
    StoredAvatar store(ValidatedAvatar avatar);
    AvatarContent load(String key);
    void delete(String key);
}
```

`AvatarService` связывает storage с user transaction. Благодаря interface позже
можно добавить S3 implementation без изменения controller/entity.

## 14.2. Не доверять `Content-Type` и расширению

Проверки выполняй в таком порядке:

1. request содержит ровно один непустой файл;
2. streaming/declared size не больше 5 MiB;
3. первые bytes соответствуют разрешённой сигнатуре;
4. `ImageReader` читает width/height из image stream до выделения полного bitmap;
5. ширина и высота положительны и не больше лимита;
6. `width * height` проверяется через `long`, чтобы избежать overflow/decompression
   bomb;
7. только после этих проверок изображение полностью декодируется и заново
   кодируется backend-ом;
8. storage key генерируется через UUID;
9. extension и served content type выбирает backend.

Минимальные сигнатуры:

```text
JPEG: FF D8 FF
PNG:  89 50 4E 47 0D 0A 1A 0A
WebP: RIFF....WEBP
```

Одной сигнатуры недостаточно: случайные или специально созданные bytes могут
иметь правильный header. Нужен успешный decode.

Стандартный JDK `ImageIO` надёжно покрывает JPEG/PNG, но WebP требует
подключённого ImageIO decoder plugin. Если WebP объявлен разрешённым, добавь
совместимый decoder dependency и integration test с реальным WebP fixture.
Нельзя принять WebP только по magic bytes и затем обнаружить в production, что
JVM не умеет его декодировать.

Самый безопасный результат — re-encode в JPEG или PNG. Это удаляет имя файла,
EXIF/GPS metadata и посторонние хвосты. Для transparency выбирай PNG, для
непрозрачного изображения можно JPEG. Ограничь итоговый размер и dimensions
после re-encode ещё раз.

SVG и GIF на 19C не разрешай:

- SVG — активный документ и требует отдельной sanitization модели;
- GIF может быть анимированным и создавать resource abuse;
- переименование `.svg` в `.png` не должно обходить проверку.

## 14.3. Защитить local storage от path traversal

`LocalAvatarStorage` должен:

1. создать configured root при старте;
2. получить `root.toRealPath()` после создания;
3. принимать только backend-generated key по строгому regex;
4. делать `root.resolve(key).normalize()`;
5. проверять `resolved.startsWith(root)`;
6. записывать во временный файл внутри того же root;
7. завершать запись atomic move, где filesystem поддерживает его;
8. не следовать symlink-ам при чтении/удалении.

Даже если key генерируется backend-ом, проверка нужна на boundary `load/delete`:
когда-нибудь key может прийти из старой БД или административной миграции.

Не логируй file bytes. Допустимо логировать userId, новый opaque key, размер и
нормализованный media type.

## 14.4. Согласовать filesystem и DB transaction

Обычная JPA transaction не откатывает filesystem. Нужен явный lifecycle.

Замена аватара:

```text
validate/decode/re-encode
store new unique file
begin/update DB: user.avatarKey = newKey
commit DB
AFTER_COMMIT: delete oldKey
AFTER_ROLLBACK: delete newKey
```

Удаление аватара:

```text
remember oldKey
DB: user.avatarKey = NULL
commit DB
AFTER_COMMIT: delete oldKey
```

Почему старый файл удаляется после commit: если DB update упадёт, старый avatar
остаётся доступным. Почему новый удаляется после rollback: иначе неуспешные
upload создают orphan files.

Для регистрации callback используй `TransactionSynchronizationManager` либо
публикуй domain event и обрабатывай его через
`@TransactionalEventListener(phase = AFTER_COMMIT)`. Rollback cleanup удобнее
делать synchronization callback. Ошибка удаления старого файла не должна
откатывать уже завершившийся commit; её логируй и убирай периодическим orphan
cleanup job позднее.

## 14.5. Avatar HTTP API

Upload:

```text
POST /api/v1/account/avatar
Content-Type: multipart/form-data
field name: file
response: 200 AccountResponse
```

Delete:

```text
DELETE /api/v1/account/avatar
response: 200 AccountResponse
```

Read:

```text
GET /api/v1/avatars/{avatarKey}
Content-Type: image/png или image/jpeg
Cache-Control: private, max-age=31536000, immutable
X-Content-Type-Options: nosniff
```

Immutable cache безопасен, потому что каждая замена создаёт новый key/URL. Не
переиспользуй key, иначе браузер продолжит показывать старое изображение.

Реши privacy явно: в текущем CollabDesk avatar endpoint доступен только
аутентифицированным пользователям с completed profile. Не делай storage directory
статическим публичным ресурсом.

При отсутствующем key возвращай 404 без filesystem details. Range requests на
маленьких avatar files не обязательны.

## 14.6. Frontend upload UX

`AvatarSection` показывает:

- текущий avatar или initials fallback;
- `Choose image`;
- подсказку `JPEG, PNG or WebP, up to 5 MB` только если backend действительно
  поддерживает все три;
- preview через object URL;
- Save/Cancel;
- Remove только при существующем avatar;
- progress state и доступную ошибку.

Frontend `accept="image/jpeg,image/png,image/webp"` — только UX-фильтр, не
security boundary.

После выбора:

```javascript
const previewUrl = URL.createObjectURL(file)
```

Обязательно вызывай `URL.revokeObjectURL` при выборе другого файла, unmount и
после завершения upload. Иначе вкладка удерживает память.

Upload через общий API helper:

```javascript
const body = new FormData()
body.append('file', file)
// Content-Type вручную не задавать: browser добавит boundary.
await withCsrf({ method: 'POST', body })
```

После ответа обнови и account state, и верхнеуровневый `user`, чтобы avatar в
sidebar поменялся сразу.

## 14.7. Тесты аватара

Unit:

- JPEG/PNG проходят decode и re-encode;
- WebP проходит только с реальным decoder;
- spoofed `Content-Type` отклоняется;
- correct header + broken body отклоняется;
- нулевые/слишком большие dimensions отклоняются;
- файл больше 5 MiB -> `AvatarTooLargeException`;
- generated key не содержит client filename.

Storage tests во временной директории:

- store/load/delete;
- два upload не перезаписывают друг друга;
- `../secret` и absolute path отклоняются;
- временный файл не остаётся после failed write.

Integration:

- anonymous upload -> 401;
- upload без CSRF -> 403;
- valid upload -> avatar key в БД и bytes доступны;
- replacement -> новый key, старый удалён после commit;
- forced DB rollback -> новый файл удалён, старый сохранён;
- DELETE очищает key и после commit удаляет файл;
- чужой filesystem path никогда не попадает в response.

### Готовность шага 14

- database содержит только opaque `avatar_key`;
- content проверяется decoder-ом, а не header-ом;
- metadata удаляется re-encode;
- DB rollback не оставляет новый orphan;
- replacement не теряет старый avatar до commit;
- account/sidebar обновляются без повторного login.

---

# Шаг 15. Просмотр и linking способов входа

## 15.1. Сначала различить login и linking

Обычный OAuth login отвечает на вопрос:

```text
какой User соответствует внешней identity?
```

OAuth linking отвечает на другой вопрос:

```text
можно ли добавить эту проверенную внешнюю identity к уже вошедшему User?
```

Нельзя передавать `userId` query parameter и доверять ему в callback. Нельзя
использовать обычный `ExternalAccountService.findOrCreate(...)`: он умеет искать
по email и создавать User, что для linking является неправильным поведением.

Для linking текущий userId берётся только из authenticated session до redirect и
сохраняется в серверной session как одноразовый intent.

## 15.2. Safe identities response

```json
[
  {
    "provider": "LOCAL",
    "connected": true,
    "connectedAt": "2026-09-09T10:00:00Z",
    "canUnlink": false,
    "requiresStepUp": true
  },
  {
    "provider": "GOOGLE",
    "connected": false,
    "connectedAt": null,
    "canUnlink": false,
    "requiresStepUp": true
  }
]
```

Верни все поддерживаемые providers в фиксированном порядке, а не только строки
БД: frontend тогда легко рисует `Connect` для отсутствующих способов.

`canUnlink` в 19C всегда `false`, даже при двух identities, потому что step-up
ещё отсутствует. После 19D значение станет результатом двух условий:

```text
identityCount > 1 && recentStepUpAllows(UNLINK_IDENTITY)
```

Не возвращай `providerSubject`. Даже собственному frontend он не нужен.

## 15.3. Создать одноразовый link intent

Session object содержит:

```text
random intent id
current userId
target provider GOOGLE/GITHUB
createdAt/expiresAt (например 5 минут)
return path строго из allowlist, обычно /account
```

Храни intent server-side в `HttpSession`, не в localStorage и не в доверенном
query JSON. Spring OAuth уже проверяет protocol `state`; link intent добавляет
бизнес-контекст, но не заменяет OAuth `state`.

Правила:

- только один активный link intent на session;
- новый start заменяет старый;
- intent одноразовый и удаляется до выполнения link;
- provider callback обязан совпасть с target provider;
- intent истекает через короткий срок;
- logout/session invalidation автоматически удаляет intent;
- return path выбирается сервером, чтобы не создать open redirect.

## 15.4. Start endpoint

```text
POST /api/v1/account/identities/{provider}/link/start
```

Алгоритм:

1. provider парсится case-insensitive, но canonical response — enum uppercase;
2. `LOCAL` отклоняется: его создание относится к смене пароля в шаге 18;
3. проверяется, что у User ещё нет этого provider;
4. создаётся session intent;
5. response возвращает только внутренний authorization URL, например
   `/oauth2/authorization/google`;
6. frontend делает full-page navigation на этот URL.

```json
{ "authorizationUrl": "/oauth2/authorization/google" }
```

Не принимай произвольный callback/return URL от клиента.

## 15.5. Получить проверенную external identity в callback

Переиспользуй provider-specific извлечение уже проверенных данных:

```text
Google: registrationId + OIDC sub + verified email
GitHub: registrationId + numeric id + verified primary email
```

Но не вызывай обычный account reconciliation до проверки intent. Удобно вынести
из существующих provider services mapper:

```text
VerifiedExternalIdentityFactory.from(authentication)
```

Он возвращает `ExternalIdentity(provider, providerSubject, verifiedEmail, ...)`,
но ничего не сохраняет.

Важное ограничение текущей архитектуры: стандартный `OAuth2UserService` обычно
создаёт/находит User ещё до success handler. Для linking это слишком рано.
Поэтому custom Google/GitHub user services должны в начале распознавать link
intent из session/request context и в link mode:

- валидировать provider data;
- загрузить target User строго по `intent.userId`;
- создать отдельный `IdentityLinkOAuth2Principal`, в котором
  `CollabDeskPrincipal.userId` — это target User, а проверенные provider subject
  и claims доступны только linking handler-у;
- **не** вызывать `ExternalAccountService.findOrCreate`;
- передать управление dedicated linking success handler.

Это существенно: OAuth filter установит созданный principal в
`SecurityContext` ещё до success handler. Если link principal будет представлять
внешний аккаунт вместо `intent.userId`, текущая session на время callback может
переключиться на другого пользователя. Dedicated principal обязан иметь
authorities и account fields target User. После успешного commit handler строит
обычный свежий principal target User и сохраняет его в session; provider subject
в нём больше не остаётся.

Для текущего session-based приложения сделай один
`OAuthFlowContextResolver`: он читает server-side intent из `HttpSession` на
callback request, сверяет expiry и `registrationId`, но не consume-ит intent.
Google/GitHub user services используют только этот resolver. Окончательно
consume intent должен success handler непосредственно перед транзакцией link.
Если позже authorization requests будут вынесены из session, этот resolver можно
заменить custom `AuthorizationRequestRepository` без изменения link service.

Не читай session напрямую в двух provider services, не используй глобальную
переменную/ThreadLocal без request cleanup и не определяй link mode по frontend
cookie, которую клиент может подделать.

## 15.6. Транзакционный `IdentityLinkService`

После успешного OAuth callback:

```text
consume and validate intent
load current target User by intent.userId
require ACTIVE + completed onboarding
reject LOCAL
if same provider already belongs to same User -> idempotent success
if (provider, subject) belongs to another User -> conflict
if target User already has provider with another subject -> conflict
save new AuthIdentity(target User, provider, subject)
commit
redirect /account?link=success&provider=github
```

Email совпадение для linking не является основанием выбрать другого User.
Пользователь уже аутентифицирован в CollabDesk и отдельно доказал владение новым
provider account. Verified email всё равно проверяй согласно provider policy,
но не запускай `findOrCreate by email`.

### Конкурентность

Database unique `(provider, provider_subject)` уже защищает один внешний аккаунт
от привязки к двум Users. Нужен также инвариант «не более одной identity данного
provider на User». В текущей `V1` его нет.

Добавь миграцию `V15__enforce_one_identity_per_provider_per_user.sql`:

```sql
ALTER TABLE auth_identities
    ADD CONSTRAINT auth_identities_user_provider_uk
        UNIQUE (user_id, provider);
```

Перед миграцией проверь, что исторических дублей нет. На test fixture обязательно
проверь оба unique constraint. Service-level `exists...` даёт красивую ошибку,
но только БД закрывает race между двумя callback.

`DataIntegrityViolationException` преобразуй в общий безопасный
`identity_connection_conflict`; не отправляй имя constraint клиенту.

## 15.7. Отдельные success/failure handlers

Обычный OAuth success продолжает вести в onboarding/dashboard. Linking success
ведёт только на allowlisted frontend path:

```text
/account?link=success&provider=github
```

Failure:

```text
/account?link=failed&code=identity_connection_failed
```

Не помещай exception message, subject, email, OAuth code или token в URL. Query
нужен только для одноразового UI banner; после чтения frontend должен очистить
его через `history.replaceState`, чтобы refresh не показывал success повторно.

После callback сделай session fixation protection так же внимательно, как при
login. Не заменяй CollabDesk authentication внешним principal привязываемого
аккаунта: пользователь должен остаться тем же внутренним User.

## 15.8. Почему unlink пока закрыт

Даже если у пользователя два способа входа, stolen session может удалить
надежный provider и ослабить аккаунт. Исходный план правильно требует:

1. recent step-up;
2. принадлежность identity текущему User;
3. минимум один оставшийся sign-in method;
4. security event.

Пункты 1 и 4 появляются в шагах 16–17. Поэтому в 19C:

- кнопка `Disconnect` disabled с пояснением;
- `canUnlink=false`;
- DELETE route не регистрируется либо стабильно возвращает `409/428` с
  `step_up_required`;
- service-level алгоритм и tests можно подготовить, но mutation не публиковать.

После 19D endpoint активируется без изменения response model.

Не делай временный небезопасный unlink «потом усилим».

## 15.9. Frontend sign-in methods

Для каждого provider покажи:

- понятное имя и icon;
- `Connected`/`Not connected`;
- безопасную дату подключения, если есть;
- кнопку `Connect` для Google/GitHub;
- пояснение для LOCAL, что пароль управляется в Security на следующем этапе;
- disabled `Disconnect` с текстом про дополнительную проверку.

При `Connect`:

```text
POST start с CSRF
получить relative authorizationUrl
проверить, что URL начинается с /oauth2/authorization/
window.location.assign(url)
```

Client-side prefix check — дополнительная защита от случайного backend bug, но
не замена server allowlist.

## 15.10. Тесты linking

Unit:

- intent создаётся для current user и expires;
- consume одноразовый;
- provider mismatch отклоняется;
- LOCAL link отклоняется;
- return path только allowlisted;
- service создаёт identity именно intent.userId;
- чужая `(provider, subject)` даёт conflict;
- второй subject того же provider для User даёт conflict;
- same identity retry идемпотентен.

MVC/security:

- anonymous start -> 401;
- start без CSRF -> 403;
- incomplete profile -> 403;
- already connected -> 409;
- valid start -> только внутренний authorization URL.

OAuth integration:

- normal login без intent работает как раньше;
- link callback не создаёт новый User;
- link callback не объединяет аккаунты по email;
- GitHub identity привязывается к текущему LOCAL User;
- Google identity другого User не может быть украдена;
- expired/missing intent не вызывает normal login fallback;
- intent provider mismatch отклоняется;
- callback сохраняет исходную CollabDesk session identity;
- failure redirect не содержит secrets.

### Готовность шага 15

- список identities не содержит subjects и secrets;
- start использует authenticated session + CSRF;
- callback различает LOGIN и LINK до изменения БД;
- linking никогда не создаёт User;
- оба unique constraints закрывают race;
- обычные Google/GitHub login regression tests зелёные;
- unlink остаётся закрыт до step-up.

---

# 4. TDD-очередь без поломки компиляции

Добавляй production skeleton и соответствующий тест маленькими парами. Не клади
сразу десяток тестовых классов, которые импортируют ещё не созданные types.

## TDD-0. Baseline

```powershell
.\mvnw.cmd test
cd frontend
npm.cmd run lint
npm.cmd run build
```

## TDD-1. Locale domain

Сначала тесты `SupportedLocale` и `User.changePreferredLocale`:

- `en`, `ru`, `sk` принимаются;
- `EN-us` либо канонизируется в `en` на boundary, либо отклоняется domain layer;
- неизвестное значение не сохраняется;
- change вызывает `updatedAt` только при фактическом изменении.

Затем `V14` migration test.

## TDD-2. Account query

Создай DTO и repository projection, затем проверь:

- все profile fields;
- sorted distinct providers;
- `avatarUrl=null` без key;
- никаких secret fields в JSON.

## TDD-3. Profile update

Проверь normalization, field validation, display name, birth date и disabled
account. Отдельно проверь, что principal после PATCH содержит новое имя.

## TDD-4. Account security boundary

401/403/CSRF tests должны появиться до frontend. Так API contract уже стабилен.

## TDD-5. Frontend locale primitives

Даже без test runner вынеси pure functions и проверь хотя бы build/lint. Лучше
добавить Vitest отдельным маленьким commit и покрыть:

- locale normalization;
- fallback;
- parity ключей `en`/`ru`/`sk`;
- interpolation;
- explicit guest preference selection.

Не пытайся тестировать это случайными console logs.

## TDD-6. Account screen

После API helper создай screen и ручной/компонентный сценарий load-edit-save.
Если добавлен Testing Library, проверь field errors и disabled Save.

## TDD-7. Avatar validator

Начни с fixtures размером в несколько pixels. Тесты broken/spoofed bytes должны
упасть до реализации decoder validation и стать зелёными после неё.

## TDD-8. LocalAvatarStorage

Используй `@TempDir`; никогда не пиши test files в настоящий `./data/avatars`.

## TDD-9. Avatar transaction

Проверь after commit и after rollback. Это важнее простого happy-path controller
test, потому что именно здесь возможна потеря файла.

## TDD-10. Avatar MVC

Multipart + CSRF, limits, safe headers, replacement/delete и response update.

## TDD-11. Identity projection и V15

Проверь фиксированный список providers и оба database uniqueness invariants.

## TDD-12. Link intent

Проверь expiry, single use, provider binding и allowlisted redirect.

## TDD-13. Linking service

Покрой idempotency, conflicts и отсутствие создания User.

## TDD-14. OAuth login/link regressions

Последними включай end-to-end callback tests для обоих режимов. Все старые OAuth
tests должны остаться зелёными.

---

# 5. Рекомендуемый порядок маленьких коммитов

```text
1.  test(account): cover preferred locale domain and migration
2.  feat(account): add V14 and supported locale model
3.  test(account): cover account query and safe response
4.  feat(account): add account GET and identity projection
5.  test(account): cover profile update and principal refresh
6.  feat(account): add profile and locale PATCH endpoints
7.  refactor(auth): share current authentication refresher
8.  feat(i18n): add locale provider and en/ru/sk dictionaries
9.  feat(account-ui): add account route and profile/language sections
10. test(avatar): cover validation and local storage
11. feat(avatar): add storage abstraction and secure codec
12. test(avatar): cover transaction lifecycle and HTTP API
13. feat(avatar): add upload/read/delete and account UI
14. test(identity): cover user-provider uniqueness and projections
15. feat(identity): add V15 and safe sign-in methods response
16. test(oauth): cover one-time link intent and link conflicts
17. feat(oauth): add authenticated Google/GitHub linking flow
18. feat(account-ui): add sign-in methods states and callback banners
19. docs(account): record manual verification and production storage settings
```

Не смешивай locale migration, image codec и OAuth callback в одном коммите:
ошибку или security regression тогда трудно локализовать.

---

# 6. Полная ручная проверка

## 6.1. Account и профиль

1. Запусти MySQL/backend/frontend.
2. Войди LOCAL пользователем с завершённым onboarding.
3. Открой `/account` через user menu.
4. Проверь email, имя, verification status и providers.
5. Измени имя/фамилию/date of birth.
6. Убедись, что sidebar обновился сразу.
7. Обнови страницу: данные сохранились.
8. Отправь future birth date: получи field error без потери draft.
9. Открой `/account` anonymous: приложение должно показать login, API — 401.

## 6.2. Locale

1. На login выбери русский.
2. Обнови страницу: язык сохранился.
3. Войди и проверь синхронизацию с account.
4. Переключи на English в account.
5. Logout/login: English восстановился с сервера.
6. Вручную положи `collabdesk.locale=xx`: UI безопасно выбрал `en`.
7. Проверь login, verification, onboarding и account: нет видимых translation
   keys и смешанных языков в одной секции.

## 6.3. Avatar

1. Загрузи маленький PNG — preview/sidebar меняются.
2. Обнови страницу — avatar доступен.
3. Замени на JPEG — URL/key изменился.
4. Проверь, что старый файл удалён только после success.
5. Попробуй файл больше 5 MiB.
6. Переименуй `.txt` в `.png` — backend отклоняет.
7. Переименуй SVG в `.png` — backend отклоняет.
8. Попробуй повреждённый JPEG header — backend отклоняет.
9. Удали avatar — initials fallback появляется сразу.
10. Проверь response headers `nosniff` и immutable cache.

## 6.4. Identities и linking

1. В LOCAL account открой Sign-in methods.
2. Проверь `LOCAL Connected`, Google/GitHub доступны для connect.
3. Начни GitHub link и успешно заверши OAuth.
4. Убедись, что количество users не увеличилось.
5. Logout и login через GitHub должны открыть тот же User/workspaces.
6. Повтори link — UI показывает already connected без дубля.
7. Попробуй provider identity, уже принадлежащую другому User — safe conflict.
8. Начни link, подожди expiry/удали session cookie, заверши callback — linking не
   происходит и не превращается в обычный login.
9. Проверь Back/refresh после success banner: сообщение не повторяется.
10. Убедись, что Disconnect недоступен до шага 17.

## 6.5. Финальный regression

```powershell
.\mvnw.cmd test
cd frontend
npm.cmd run lint
npm.cmd run build
```

Повтори обычную регистрацию LOCAL, email verification, onboarding, Google login
и GitHub login. 19C не должен изменить их смысл.

---

# 7. Типичные ошибки

## Возвращать JPA entity из controller

Так легко раскрыть `passwordHash`, provider subject или lazy graph. Всегда
используй явные response records.

## Читать account только из principal

Principal — snapshot. Свежий профиль читается из БД; после mutation snapshot
обновляется отдельно.

## Принимать `displayName` вместе с first/last name

Три поля начинают расходиться. В текущей domain model display name вычисляется.

## Хранить locale только в localStorage

Настройка теряется между устройствами и не является частью аккаунта. После login
источник истины — сервер.

## Делать `t(key) || key`

Пустые и неизвестные переводы становятся незаметными. Нужны parity tests и
явный English fallback.

## Доверять multipart Content-Type

Его задаёт клиент. Проверяй bytes и реальный decode.

## Хранить оригинальный avatar filename

Это даёт collisions, path traversal и утечку пользовательских данных. Key
генерирует backend.

## Удалять старый avatar до DB commit

При rollback профиль указывает на уже удалённый файл. Cleanup выполняется после
commit.

## Считать filesystem частью JPA transaction

Он не откатывается автоматически. Явно обработай after commit/rollback.

## Разрешить SVG «потому что это картинка»

SVG — активный XML-документ. Для него нужен отдельный sanitizer и content policy;
в 19C он запрещён.

## Использовать normal login service для linking

Он может найти/создать другого User по email. Linking всегда привязывает identity
только к userId одноразового authenticated intent.

## Передавать userId или returnUrl от frontend

Это создаёт account takeover/open redirect. userId и redirect allowlist задаёт
backend.

## При missing intent продолжать как обычный login

Так expired link callback неожиданно войдёт или создаст аккаунт. Link flow при
missing/invalid intent завершается ошибкой.

## Полагаться только на `exists` перед INSERT

Два callback могут пройти проверку одновременно. Нужны database unique
constraints и безопасная обработка race.

## Включить unlink до step-up

CSRF защищает от чужого сайта, но не от украденной/оставленной открытой session.
Unlink остаётся закрыт до шагов 16–17.

---

# 8. Финальный Definition of Done для 19C

- [ ] Есть неизменяемые Flyway migrations `V14` и `V15`.
- [ ] `preferred_locale` поддерживает `en`/`ru`/`sk` и fallback `en`.
- [ ] `GET /api/v1/account` возвращает только safe DTO.
- [ ] Profile PATCH валидируется, использует `User.changeProfile(...)` и
      обновляет текущий principal.
- [ ] Locale синхронизируется между account и гостевым localStorage без loop.
- [ ] Auth, verification, onboarding и account покрыты словарями `en`/`ru`/`sk`.
- [ ] `/account` имеет Profile, Avatar, Language, Sign-in methods, Security и
      Danger zone sections.
- [ ] Avatar bytes проходят size, magic-byte, decoder и dimension validation.
- [ ] Изображение re-encode-ится, metadata не сохраняется.
- [ ] Filesystem path и оригинальный filename не попадают в БД/API.
- [ ] Replacement/delete согласованы с DB commit/rollback.
- [ ] Identities response не раскрывает provider subjects.
- [ ] OAuth link intent одноразовый, короткоживущий и привязан к session/provider.
- [ ] Linking не вызывает обычный find-or-create и никогда не создаёт User.
- [ ] Уникальность `(provider, subject)` и `(user, provider)` защищена MySQL.
- [ ] Normal Google/GitHub login продолжает работать.
- [ ] Unlink не опубликован до step-up/security events из 19D.
- [ ] Все backend tests, frontend lint и frontend build зелёные.
- [ ] Пройдена ручная проверка профиля, locale, avatar и linking.

После выполнения этого списка можно переходить к 19D: security event log,
step-up, пароль и TOTP. Именно 19D безопасно разблокирует Disconnect и наполнит
пока пустую секцию Security.
