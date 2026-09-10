# Этап 19. GitHub-вход, завершение регистрации и безопасность аккаунта

## Как пользоваться этой инструкцией

Этот этап большой, поэтому не пытайся реализовать его одним коммитом. Делай
разделы строго по порядку. После каждого раздела запускай указанные тесты и
проверяй сценарий вручную. Если предыдущий раздел не работает, не переходи к
следующему: почти каждый следующий шаг использует результат предыдущего.

Работу делим на пять самостоятельных частей:

```text
19A  Общая модель аккаунта, onboarding и GitHub OAuth
19B  Повтор пароля и подтверждение локального email
19C  Личный кабинет, профиль и связанные способы входа
19D  Повторная проверка личности, TOTP 2FA и recovery-коды
19E  Телефон, восстановление и удаление аккаунта
```

Сейчас в первую очередь нужно выполнить `19A`. Остальные части подробно описаны,
чтобы при развитии аккаунтов не пришлось переделывать архитектуру заново.

## Общий чек-лист выполнения

Ставь галочку только после кода, тестов и ручной проверки соответствующего шага:

- [x] Шаг 1. Модель профиля, `V12` и поддержка `GITHUB` в MySQL.
- [x] Шаг 2. Общий `ExternalIdentity` и `ExternalAccountService`.
- [x] Шаг 3. Onboarding-состояние в principal и backend security.
- [x] Шаг 4. Onboarding API и обновление текущей сессии.
- [x] Шаг 5. `OnboardingScreen` во frontend.
- [x] Шаг 6. Отдельные OAuth success/failure handlers.
- [x] Шаг 7. GitHub OAuth backend и verified email.
- [x] Шаг 8. GitHub-кнопка и полный ручной OAuth-сценарий.
- [x] Шаг 9. `passwordConfirmation` на backend и frontend.
- [ ] Шаг 10. SMTP abstraction и Mailpit для разработки.
- [ ] Шаг 11. Одноразовые verification challenges.
- [ ] Шаг 12. Подтверждение email локального пользователя.
- [ ] Шаг 13. Account API и страница личного кабинета.
- [ ] Шаг 14. Изменение имени и безопасная загрузка аватара.
- [ ] Шаг 15. Просмотр, linking и unlinking способов входа.
- [ ] Шаг 16. Журнал security-событий.
- [ ] Шаг 17. Step-up подтверждение важных действий.
- [ ] Шаг 18. Создание и смена локального пароля.
- [ ] Шаг 19. TOTP 2FA и recovery-коды.
- [ ] Шаг 20. Подтверждённый телефон.
- [ ] Шаг 21. Восстановление аккаунта.
- [ ] Шаг 22. Отложенное удаление и анонимизация аккаунта.

---

## Что должно получиться в конце

- В CollabDesk можно войти по паролю, через Google или через GitHub.
- Первый OAuth-вход не отправляет нового человека сразу в workspace.
- Новый OAuth-пользователь попадает на `/onboarding` и сам выбирает имя.
- Новый локальный пользователь после создания credentials тоже завершает профиль
  на `/onboarding`.
- Повторный OAuth-вход готового пользователя сразу открывает приложение.
- Локальный пользователь не может войти, пока не подтвердит email кодом.
- Форма регистрации содержит `password` и `passwordConfirmation`.
- В личном кабинете можно изменить профиль и увидеть способы входа.
- TOTP-двухфакторка защищает вход и важные настройки аккаунта.
- Есть recovery-коды и безопасная процедура восстановления.
- Важные операции требуют недавнего подтверждения личности.
- Удаление аккаунта не ломает workspace, проекты, задачи и audit log.

---

# Часть 0. Сначала понять, как уже работает OAuth-код

## Короткий словарь классов

| Термин | Что это значит в нашем проекте |
|---|---|
| Entity | Java-объект, который соответствует данным в таблице MySQL, например `User` |
| Repository | Интерфейс чтения и сохранения entity в БД |
| Service | Место бизнес-правил и транзакций: что и в каком порядке разрешено делать |
| Controller | Принимает HTTP request, валидирует DTO и вызывает service |
| DTO / record | Структура входного request или выходного response без JPA-логики |
| Principal | Снимок текущего вошедшего пользователя внутри Spring Security |
| Authentication | Principal вместе с authorities и состоянием аутентификации |
| SecurityContext | Место, где Spring хранит `Authentication` текущего request/session |
| Authority | Короткое право security-уровня, например `PROFILE_COMPLETE` |
| Handler | Код, который выполняется после успешного или неудачного login |
| Filter chain | Последовательность security-проверок до вызова controller |
| Flyway migration | Неизменяемый SQL-шаг перехода схемы БД на следующую версию |

Controller не должен самостоятельно писать в несколько repositories. Он
принимает request и передаёт управление service. Service выполняет цельную
операцию в транзакции. Principal не заменяет свежий `User` из БД для критических
изменений: он нужен для определения текущего userId и быстрой авторизации.

## 0.1. Что делает Spring, а что делает CollabDesk

При нажатии `Continue with Google` frontend не вызывает наш REST-контроллер.
Браузер открывает специальный endpoint Spring Security:

```text
GET /oauth2/authorization/google
```

Дальше происходит такой поток:

```text
1. Spring читает registration.google из application.properties.
2. Spring создаёт authorization request и случайный state.
3. Браузер перенаправляется на Google.
4. Пользователь вводит пароль только на стороне Google.
5. Google возвращает браузер на backend callback.
6. Spring проверяет state и меняет временный code на токены.
7. OidcUserService проверяет подписанный Google ID Token.
8. CollabDeskOidcUserService получает уже проверенного OidcUser.
9. Наш service связывает Google sub с users.id в MySQL.
10. Наш principal становится текущим пользователем Spring Security.
11. SecurityContext сохраняется в HTTP-сессии.
12. Браузер получает cookie JSESSIONID.
13. Success handler решает: /onboarding или основное приложение.
```

Spring отвечает за безопасную протокольную часть: redirect, `state`, callback,
обмен `code`, проверку токена и создание `Authentication`. Наш код отвечает за
бизнес-часть: какой `User` соответствует внешнему аккаунту, разрешён ли ему вход
и завершил ли он регистрацию.

### Роль существующих классов

| Класс | Что он делает |
|---|---|
| `SecurityConfig` | включает form login и OAuth2 login, задаёт URL и handlers |
| `OidcUserService` | стандартный Spring-клиент для Google OIDC |
| `CollabDeskOidcUserService` | получает проверенные Google claims и вызывает наш service |
| `GoogleAccountService` | сейчас ищет/создаёт `User` и `AuthIdentity(GOOGLE)` |
| `GoogleOidcPrincipal` | объединяет OIDC claims и внутренний `users.id` |
| `CollabDeskPrincipal` | общий интерфейс, который используют контроллеры CollabDesk |
| `AuthIdentity` | хранит связь LOCAL/GOOGLE/GITHUB с одним `User` |

Важное правило: Google access token не нужно отдавать в React или сохранять в
`localStorage`. После входа наше приложение работает через собственную
`JSESSIONID`, как и при входе по паролю.

## 0.2. Чем GitHub отличается от Google

Google использует OIDC и возвращает подписанный ID Token с `sub`, `email`,
`email_verified`, `name` и `picture`. Поэтому Google проходит через
`OidcUserService`, а стабильным внешним id является `sub`.

GitHub OAuth App использует обычный OAuth2 login. У него нет такого же OIDC ID
Token, поэтому Spring использует `DefaultOAuth2UserService`. Стабильным внешним
id будет числовой атрибут GitHub `id`.

```text
GOOGLE -> provider_subject = Google sub
GITHUB -> provider_subject = String.valueOf(GitHub id)
```

Нельзя сохранять GitHub `login` как `provider_subject`: пользователь может
переименовать GitHub-аккаунт. Email тоже может измениться.

GitHub может не вернуть email в обычном `/user`, если он скрыт. Поэтому backend
должен временно использовать access token и запросить:

```text
GET https://api.github.com/user/emails
```

Из ответа выбираем адрес с `primary=true` и `verified=true`. Для этого нужен
scope `user:email`. После окончания входа GitHub token нам больше не нужен,
поскольку CollabDesk пока не работает с GitHub-репозиториями.

---

# Часть 19A. Общая модель аккаунта, onboarding и GitHub OAuth

## Шаг 1. Зафиксировать состояния пользователя

### Зачем это нужно

Сейчас `User` имеет только `ACTIVE` или `DISABLED`, а Google-вход сразу создаёт
полностью готового пользователя. Нам необходимо различать:

```text
можно ли использовать аккаунт вообще;
подтверждён ли основной email;
заполнил ли человек профиль CollabDesk.
```

Оставь `status` для блокировки аккаунта, а email и onboarding представь
отдельными timestamps.

### Что добавить в БД

Создай новую миграцию, не изменяя `V1`–`V11`:

```text
src/main/resources/db/migration/
V12__add_account_profile_and_github_identity.sql
```

Старые применённые миграции нельзя редактировать — иначе снова появится Flyway
checksum mismatch.

В `V12` добавь в `users`:

```text
first_name               VARCHAR(100) NULL
last_name                VARCHAR(100) NULL
birth_date               DATE NULL
avatar_key               VARCHAR(500) NULL
email_verified_at        DATETIME(6) NULL
onboarding_completed_at  DATETIME(6) NULL
```

Почему поля nullable:

- старые строки уже существуют;
- новый OAuth-user ещё не выбрал имя;
- фамилия, аватар и дата рождения необязательны;
- `NULL email_verified_at` означает неподтверждённый локальный email;
- `NULL onboarding_completed_at` означает незавершённый профиль.

В той же миграции обнови старых пользователей:

```text
first_name = display_name
email_verified_at = created_at
onboarding_completed_at = created_at
```

Это важно: существующие пользователи уже работали в системе и не должны
внезапно попасть на подтверждение почты или onboarding.

### Расширить provider CHECK

В `V1` таблица `auth_identities` разрешает только `LOCAL` и `GOOGLE`. Добавления
`GITHUB` только в Java enum недостаточно — MySQL отклонит INSERT.

В `V12` замени старый CHECK constraint на новый:

```text
provider IN ('LOCAL', 'GOOGLE', 'GITHUB')
```

Проверь точное имя старого constraint:

```sql
SHOW CREATE TABLE auth_identities;
```

На чистой схеме MySQL обычно создаёт имя вроде `auth_identities_chk_1`, но перед
`DROP CHECK` используй имя из реальной схемы. Проверь миграцию и на существующей
БД, и на чистом Testcontainers MySQL.

### Что изменить в `User`

Добавь поля, getters и методы предметной области:

```text
isEmailVerified()
isOnboardingCompleted()
completeOnboarding(firstName, lastName, birthDate)
changeProfile(firstName, lastName, birthDate)
changeAvatar(avatarKey)
markEmailVerified(now)
```

Не добавляй публичные setters. Проверки длины, trim и обновление `updatedAt`
должны находиться внутри `User`.

`completeOnboarding(...)` должен:

1. проверить обязательное имя;
2. обрезать пробелы;
3. сохранить пустую фамилию как `NULL`;
4. обновить `displayName` для совместимости старого UI;
5. поставить `onboardingCompletedAt` после валидных данных;
6. обновить `updatedAt`.

Дату рождения оставь необязательной. Не собирай её, если для неё пока нет
функции внутри CollabDesk.

### Как проверить шаг

- все 12 миграций применяются на пустой MySQL;
- старый пользователь после миграции считается verified и onboarded;
- новый OAuth-user может иметь `onboarding_completed_at = NULL`;
- MySQL принимает `provider='GITHUB'`;
- MySQL отклоняет неизвестный provider;
- `display_name` остаётся заполненным для старого frontend.

Шаг закончен, когда `./mvnw test` поднимает чистую схему без Flyway/Hibernate
validation errors.

---

## Шаг 2. Создать общую модель внешнего аккаунта

### Зачем это нужно

Не пиши отдельно полную linking-логику для Google и GitHub. Иначе один service
проверит disabled-user и конфликты, а другой забудет. Provider-specific код
должен только прочитать данные провайдера, а общий service — работать с БД.

### Какие классы создать

Создай пакет `collabdesk.auth.external` и record:

```text
ExternalIdentity
  AuthProvider provider
  String providerSubject
  String verifiedEmail
  String suggestedFirstName
  String suggestedLastName
  String suggestedAvatarUrl
```

Результат linking:

```text
ExternalAccountResult
  User user
  boolean newlyCreated
  boolean onboardingRequired
```

После этого создай `ExternalAccountService`.

### Алгоритм `ExternalAccountService`

```text
1. Проверить providerSubject и verifiedEmail.
2. Нормализовать email по единой политике приложения.
3. Найти AuthIdentity по (provider, providerSubject).
4. Если identity найдена:
     вернуть связанного активного User;
     не менять его имя;
     ничего не создавать повторно.
5. Если identity не найдена, найти User по verified email.
6. Если User найден:
     убедиться, что нет другой identity того же provider;
     добавить identity к тому же User.
7. Если User не найден:
     создать нового User;
     email сразу считать подтверждённым provider-ом;
     onboardingCompletedAt оставить NULL;
     создать AuthIdentity.
8. Вернуть onboardingRequired.
```

Для временного `display_name` нового OAuth-user можно использовать часть email
до `@`. Внешнее имя передаётся как suggestion, но не считается выбранным.

### Защититься от конфликтов

Если User уже связан с `GOOGLE subject=111`, а приходит другой Google
`subject=222` с тем же email, не добавляй вторую identity автоматически. Верни
`external_identity_conflict` и запиши warning без токенов и полных claims.

### Переделать Google-код

`CollabDeskOidcUserService` должен делать только это:

```text
OidcUserService.loadUser(request)
  -> проверить registrationId == google
  -> проверить sub, email и email_verified=true
  -> создать ExternalIdentity(GOOGLE, ...)
  -> вызвать ExternalAccountService
  -> создать GoogleOidcPrincipal
```

Существующий `GoogleAccountService` после переноса общей логики удали либо оставь
тонким mapper без repository-операций. Не держи две версии linking.

### Тесты

- новый OAuth-user и identity создаются один раз;
- повторный subject возвращает того же User;
- GOOGLE связывается с LOCAL user по verified email;
- выбранное имя существующего пользователя не перезаписывается;
- disabled user отклоняется;
- второй subject того же provider отклоняется;
- новый OAuth-user verified, но onboarding не завершён;
- транзакция откатывается при ошибке сохранения identity.

---

## Шаг 3. Добавить состояние onboarding в security

### Зачем это нужно

Одного frontend redirect недостаточно: незавершённый пользователь может вручную
вызвать workspace API. Ограничение обязательно должно работать на backend.

### Изменить principal и `/auth/me`

Добавь в `CollabDeskPrincipal`:

```text
boolean isEmailVerified()
boolean isOnboardingCompleted()
```

Реализуй их в local, Google и будущем GitHub principal. Значения берутся из
нашего `User`, не из frontend.

В `CurrentUserResponse` добавь:

```json
{
  "emailVerified": true,
  "onboardingCompleted": false
}
```

### Закрыть основное API

Добавь authority `PROFILE_COMPLETE`. Готовый principal получает её,
незавершённый — нет.

В `SecurityConfig` порядок правил:

```text
public auth endpoints                         permitAll
/api/v1/auth/me и logout                      authenticated
/api/v1/account/onboarding/**                 authenticated
остальные /api/v1/**                          PROFILE_COMPLETE
```

После завершения onboarding principal в сессии останется старым. Поэтому endpoint
должен обновить `Authentication` в `SecurityContext` свежим principal. Иначе
пользователь сохранит `onboardingCompleted=false` до повторного входа.

### Тесты

- незавершённый principal получает `/auth/me` и onboarding API;
- workspace/project/task возвращают ему `403`;
- готовый principal использует старые API;
- анонимный пользователь получает `401`.

---

## Шаг 4. Реализовать backend onboarding

Создай пакет `collabdesk.account.onboarding`:

```text
OnboardingController
OnboardingService
OnboardingResponse
CompleteOnboardingRequest
```

Endpoints:

```text
GET  /api/v1/account/onboarding
POST /api/v1/account/onboarding/complete
```

`POST` принимает:

```json
{
  "firstName": "Alex",
  "lastName": "Morgan",
  "birthDate": null
}
```

Provider suggestions не нужно записывать как окончательное имя. Сохрани их в
OAuth principal/session как `ExternalProfileSuggestion`, чтобы `GET onboarding`
мог предложить их форме. После завершения onboarding suggestion больше не нужен.
Если session потерялась, следующий OAuth login снова получит suggestion от
провайдера.

Правила:

- firstName обязателен, 1–100 символов после trim;
- lastName необязателен, максимум 100;
- birthDate необязательна и не может быть в будущем;
- userId берётся только из principal;
- request не может менять email, status и id.

Service:

```text
1. Загружает текущего User по principal.userId.
2. Проверяет ACTIVE status.
3. Идемпотентно обрабатывает уже готовый профиль.
4. Вызывает User.completeOnboarding(...).
5. Сохраняет User.
6. Обновляет principal в SecurityContext.
7. Возвращает AccountResponse.
```

Тесты: validation, trim, пустая фамилия, будущая дата, чужой userId невозможен,
повторный запрос безопасен, та же сессия получает `PROFILE_COMPLETE`.

---

## Шаг 5. Сделать frontend onboarding

Создай отдельный `OnboardingScreen`, не смешивая его поля с `AuthShell`.

После `getCurrentUser()`:

```text
нет сессии                       -> AuthShell
есть сессия, onboarding=false    -> OnboardingScreen
есть сессия, onboarding=true     -> основной App
```

OAuth success handler может redirect-ить на `/onboarding`, но frontend всё равно
доверяет `/auth/me`, а не URL.

Поля:

- email read-only;
- имя обязательное;
- фамилия необязательная;
- дата рождения необязательная или пока скрытая;
- provider avatar только как предложение;
- `Finish registration`;
- `Log out`.

После POST повторно вызови `/auth/me`, обнови account state и только потом открой
dashboard. До окончания проверки не показывай основной UI даже на мгновение.

Ручная проверка:

1. Новый Google account открывает onboarding.
2. Refresh оставляет onboarding.
3. Workspace API вручную даёт `403`.
4. Сохранение имени открывает dashboard в той же сессии.
5. Повторный login onboarding больше не показывает.

---

## Шаг 6. Вынести OAuth handlers

Вынеси lambdas из `SecurityConfig` в:

```text
CollabDeskOAuth2SuccessHandler
CollabDeskOAuth2FailureHandler
```

Success handler:

```text
disabled account          -> удалить session, redirect ?oauth=disabled
onboarding не завершён    -> redirect /onboarding
позже требуется MFA       -> redirect /auth/mfa
иначе                     -> redirect /
```

Failure handler передаёт только безопасные коды: `oauth=failed`,
`oauth=email_missing`, `oauth=identity_conflict`. Не передавай stack trace,
access token, authorization code или provider exception.

Redirect строится только из `APP_FRONTEND_URL`, не из query-параметра — это
защищает от open redirect.

---

## Шаг 7. Реализовать GitHub OAuth backend

### Конфигурация

GitHub client id/secret и строки `registration.github` в текущем проекте уже
добавлены. Здесь их не надо добавлять второй раз: проверь названия переменных,
callback и scopes, а затем переходи к Java-реализации.

В `.env`:

```text
GITHUB_CLIENT_ID=...
GITHUB_CLIENT_SECRET=...
```

GitHub callback:

```text
http://localhost:8080/login/oauth2/code/github
```

В `application.properties`:

```properties
spring.security.oauth2.client.registration.github.client-id=${GITHUB_CLIENT_ID}
spring.security.oauth2.client.registration.github.client-secret=${GITHUB_CLIENT_SECRET}
spring.security.oauth2.client.registration.github.scope=read:user,user:email
```

Не запрашивай `repo` или write scopes. Добавь фиктивные GitHub env values в
Surefire для тестов, но не реальные secrets.

### Доменная модель

Добавь `GITHUB` в `AuthProvider` и фабрику
`AuthIdentity.github(user, providerSubject)`. У неё `passwordHash=NULL`.

### GitHub email client

Создай:

```text
collabdesk.auth.github.GitHubEmailClient
collabdesk.auth.github.GitHubEmailResponse
```

Client получает token аргументом, вызывает `/user/emails` и выбирает только
`primary=true && verified=true`. Token не сохраняется в поле, БД или лог.

Используй Spring `RestClient` с Bearer header, GitHub Accept/API-version headers,
timeouts и безопасной обработкой 401/403/rate-limit/5xx.

### GitHub user service

`GitHubOAuth2UserService` реализует
`OAuth2UserService<OAuth2UserRequest, OAuth2User>`:

```text
1. Проверить registrationId == github.
2. Вызвать DefaultOAuth2UserService.loadUser(request).
3. Прочитать id, login, name, avatar_url.
4. Проверить id.
5. Получить verified primary email через GitHubEmailClient.
6. Собрать ExternalIdentity(GITHUB, String.valueOf(id), ...).
7. Вызвать ExternalAccountService.
8. Вернуть GitHubOAuth2Principal.
```

`GitHubOAuth2Principal` наследует `DefaultOAuth2User`, реализует
`CollabDeskPrincipal` и возвращает данные нашего `User`.

### Подключение к Spring

Оставь обе ветки:

```java
.userInfoEndpoint(userInfo -> userInfo
    .oidcUserService(collabDeskOidcUserService) // Google
    .userService(gitHubOAuth2UserService)       // GitHub
)
```

Не удаляй `formLogin` и не заменяй Google `oidcUserService` обычным
`userService`.

### Тесты

- GitHub id становится provider subject;
- выбирается только primary+verified email;
- скрытый email дополняется `/user/emails`;
- отсутствие verified email отклоняет вход;
- GitHub API errors превращаются в безопасную OAuth error;
- token отсутствует в exception;
- principal содержит внутренний userId;
- MySQL хранит GITHUB с `password_hash=NULL`;
- один User может иметь LOCAL+GOOGLE+GITHUB;
- повторный login не создаёт новую identity.

---

## Шаг 8. Добавить GitHub-кнопку

Добавь кнопку `Continue with GitHub` на login и registration экраны:

```text
/oauth2/authorization/github
```

Это один flow: backend сам определяет, login это или первая регистрация.
`/oauth2` уже есть в Vite proxy, отдельный proxy не нужен.

Критерии готовности `19A`:

- Google и GitHub работают;
- новый OAuth-user видит onboarding;
- provider name только предлагается;
- незавершённый user не читает рабочее API;
- после onboarding та же session открывает приложение;
- повторный login идёт сразу в dashboard;
- LOCAL login не сломан;
- backend tests, frontend lint/build проходят.

---

# Часть 19B. Повтор пароля и подтверждение локального email

## Шаг 9. Добавить повтор пароля

Frontend проверяет опечатку для удобства, backend повторяет проверку, потому что
request можно отправить без React.

Измени `RegisterRequest`:

```json
{
  "email": "member@example.com",
  "password": "long-password",
  "passwordConfirmation": "long-password"
}
```

До вызова service сравни поля. При несовпадении верни validation code
`passwords_do_not_match`. Не логируй DTO и пароли.

Во frontend добавь второе `type=password`, `autocomplete=new-password`, локальную
ошибку и очистку обоих полей после успеха.

Тесты: одинаковые проходят, разные дают `400`, service при несовпадении не
вызывается, пароли отсутствуют в response/log.

---

## Шаг 10. Подключить email

Добавь `spring-boot-starter-mail`. Spring предоставляет `JavaMailSender`, но
реальную доставку выполняет SMTP. Для разработки добавь Mailpit в Docker Compose,
для production потом поменяй только env.

Создай интерфейс:

```text
AccountMailService
  sendEmailVerification(...)
  sendPasswordReset(...)
  sendSecurityNotification(...)
```

`SmtpAccountMailService` использует `JavaMailSender`. Остальные services зависят
от интерфейса, чтобы тесты использовали mock.

Env:

```text
MAIL_HOST MAIL_PORT MAIL_USERNAME MAIL_PASSWORD MAIL_FROM MAIL_STARTTLS
```

Обязательно настрой connection/read/write timeouts. Production secrets остаются
в `.env`/secret manager.

---

## Шаг 11. Создать verification challenges

Следующая миграция, например `V13__create_account_verification_challenges.sql`:

```text
verification_challenges
  id
  user_id
  purpose
  channel
  destination
  code_hash
  expires_at
  consumed_at
  failed_attempts
  created_at
```

Назначения: `EMAIL_VERIFICATION`, `PASSWORD_RESET`, `SENSITIVE_ACTION`.

`VerificationChallengeService`:

1. создаёт 6 цифр через `SecureRandom`;
2. сохраняет только hash/HMAC;
3. ставит expiration, например 10 минут;
4. инвалидирует старый код того же purpose;
5. отправляет исходный код только через mail service;
6. никогда не логирует код.

Ограничения:

- resend не чаще раза в 60 секунд;
- лимит отправок на email/IP;
- максимум 5 неверных попыток;
- consumed/expired code не работает;
- новый код инвалидирует старый.

MySQL отвечает за attempts/expiration/consumed. Redis можно использовать как
дополнительный быстрый rate limit, но не как единственный источник correctness.

---

## Шаг 12. Переделать локальную регистрацию

Новый `RegistrationService`:

```text
1. Валидирует email и пароли; имя будет заполнено в общем onboarding.
2. Нормализует email общей функцией.
3. Создаёт User с emailVerifiedAt=NULL.
4. Создаёт AuthIdentity(LOCAL) с BCrypt hash.
5. Создаёт EMAIL_VERIFICATION challenge.
6. После commit отправляет письмо.
7. Возвращает verificationRequired=true.
```

`LocalUserDetailsService` отклоняет пользователя с `emailVerifiedAt=NULL` даже
при правильном пароле.

API:

```text
POST /api/v1/auth/register
POST /api/v1/auth/email-verification/confirm
POST /api/v1/auth/email-verification/resend
```

После правильного code отметь challenge consumed и email verified. Удобный UX —
автоматически создать session и открыть общий onboarding, а после него приложение.

Когда подтверждение email будет реализовано, frontend после регистрации сначала
показывает `EmailVerificationScreen`, затем общий onboarding, а не login:

- маскированный email;
- цифровое поле кода;
- expiration;
- resend countdown;
- invalid/expired/too-many-attempts errors.

Тесты:

- вход до verification запрещён;
- правильный код работает один раз;
- неверные попытки считаются;
- resend инвалидирует старый код;
- expired code отклоняется;
- письмо отправляется после commit;
- SMTP failure не создаёт дубликат User, resend доступен;
- OAuth verified email не требует локального кода.

---

# Часть 19C. Личный кабинет и профиль

Подробный самостоятельный маршрут для шагов 13–15, включая миграции, API
contracts, i18n, безопасное хранение аватаров, OAuth linking, TDD-очередь и
ручную проверку, вынесен в `19C-account-profile-detailed-guide.md`.

Важно: unlink способа входа в 19C только проектируется и остаётся закрытым до
реализации security events и step-up из шагов 16–17.

## Шаг 13. Создать Account API и страницу

Пакет `collabdesk.account`:

```text
AccountController
AccountQueryService
AccountProfileService
AccountResponse
UpdateProfileRequest
ConnectedIdentityResponse
```

Endpoints:

```text
GET   /api/v1/account
PATCH /api/v1/account/profile
GET   /api/v1/account/identities
```

Возвращай id, имя, фамилию, displayName, avatarUrl, email verification,
birthDate, providers и позже mfaEnabled. Не возвращай password hash, provider
subject, OAuth token, TOTP secret или challenge ids.

Frontend `/account` раздели на полноценные секции, не одно тесное модальное окно:

```text
Profile
Sign-in methods
Security
Danger zone
```

### Язык интерфейса

В этом же шаге добавь пользовательскую настройку языка. Начни с `English` и
`Русский`, но используй стандартные locale-коды (`en`, `ru`), чтобы позже можно
было добавлять языки без изменения API.

- вынеси frontend-тексты в словари переводов, не дублируй условия по компонентам;
- добавь переключатель языка на публичные auth/onboarding экраны и в `/account`;
- до входа сохраняй выбор в `localStorage`, после входа синхронизируй его с
  `preferred_locale` пользователя в MySQL;
- возвращай locale в `GET /api/v1/account` и изменяй через отдельный profile
  endpoint с session + CSRF;
- при неизвестном или отсутствующем locale безопасно используй `en`;
- даты, числа и сообщения форматируй через `Intl`, а не вручную;
- тесты должны проверять сохранение выбора, fallback и отсутствие untranslated
  translation keys на login, onboarding и account страницах.

---

## Шаг 14. Реализовать имя и аватар

Смена имени требует session + CSRF. После PATCH обнови current user state.

Binary avatar не храни в `users`: в MySQL только `avatar_key`, файл — в storage.
Сделай `AvatarStorage` interface и сначала `LocalAvatarStorage`, позже его можно
заменить S3 implementation.

```text
POST   /api/v1/account/avatar   multipart/form-data
DELETE /api/v1/account/avatar
```

Проверяй размер (например 5 MB), JPEG/PNG/WebP, реальные magic bytes и размеры
изображения. Имя файла генерирует backend. Старый файл удаляется только после
успешного сохранения нового. Provider avatar можно скопировать в наш storage по
явному выбору пользователя, но нельзя бесконтрольно hotlink-ить его навсегда.

---

## Шаг 15. Управлять способами входа

`GET /account/identities` показывает только provider и connected, без subject.

Для linking уже авторизованного пользователя перед OAuth redirect сохрани в
session намерение `LINK_PROVIDER`. Callback должен добавить identity текущему
User, а не случайно войти/создать другой аккаунт.

Перед unlink:

1. потребовать недавнюю проверку личности;
2. проверить принадлежность identity;
3. оставить хотя бы один способ входа;
4. записать security event.

```text
POST   /api/v1/account/identities/{provider}/link/start
DELETE /api/v1/account/identities/{provider}
```

---

# Часть 19D. Важные действия и 2FA

## Шаг 16. Добавить security event log

Таблица `account_security_events`: userId, eventType, createdAt, безопасная
информация об IP/user-agent и metadata без secrets.

События: login, email verified, password changed, identity linked/unlinked,
MFA enabled/disabled, recovery used, phone changed, deletion requested/cancelled.

Никогда не логируй password, verification/TOTP code, OAuth code/token, TOTP
secret или recovery code.

---

## Шаг 17. Создать step-up flow для важных действий

Старая session не является достаточным подтверждением для удаления или смены
защиты. Создай `SensitiveActionService`, `SensitiveActionType`, challenge и
одноразовый approval.

```text
1. Frontend начинает конкретное действие.
2. Backend выбирает способ подтверждения.
3. Пользователь подтверждает password/TOTP/email/SMS/recovery.
4. Backend выдаёт approval для user + action на 5 минут.
5. Изменяющий endpoint проверяет и уничтожает approval.
```

Approval привязан к одному user/action, одноразовый и инвалидируется при logout,
reset или security change. Не используй frontend boolean `confirmed=true`.

| Действие | Подтверждение |
|---|---|
| имя | session + CSRF |
| пароль | текущий password/OAuth re-login + TOTP, если включён |
| включить 2FA | re-auth + первый TOTP |
| отключить 2FA | re-auth + TOTP/recovery |
| сменить email | re-auth + новый email code, уведомить старый |
| сменить телефон | re-auth + new phone code, уведомить старые контакты |
| удалить account | re-auth + TOTP, если включён + текстовое подтверждение |

У OAuth-only пользователя нет CollabDesk password: для него re-auth — повторный
provider flow, TOTP или разрешённый recovery-канал.

---

## Шаг 18. Реализовать смену пароля

```text
POST /api/v1/account/security/password/change
POST /api/v1/account/security/password/create
```

`create` позволяет OAuth-only user добавить LOCAL identity после сильного
подтверждения.

После смены password: сохранить BCrypt hash, инвалидировать остальные sessions и
reset challenges, отправить уведомление и записать security event.

---

## Шаг 19. Реализовать TOTP 2FA

Таблицы:

```text
user_totp_factors(user_id, encrypted_secret, enabled_at, created_at)
user_recovery_codes(id, user_id, code_hash, used_at, created_at)
```

TOTP secret шифруется отдельным server key из env, потому что backend должен его
прочитать для проверки. Recovery codes хранятся только как hashes.

Endpoints:

```text
POST /api/v1/account/security/mfa/totp/setup
POST /api/v1/account/security/mfa/totp/confirm
POST /api/v1/account/security/mfa/totp/disable
POST /api/v1/account/security/recovery-codes/regenerate
```

Flow setup:

```text
re-auth -> temporary secret -> QR -> первый TOTP -> включить factor
        -> создать recovery codes -> показать их один раз
```

Если confirm не выполнен, factor не активен. Ограничь попытки; secret/code не
логируются. Regenerate инвалидирует старые recovery codes.

Login:

```text
LOCAL: password -> MFA pending -> TOTP/recovery -> full session
OAuth: provider -> MFA pending -> TOTP/recovery -> full session
```

Используй factor authorities Spring Security 7 либо ограниченный pending-auth
flow. Workspace API закрыт до второго фактора. Frontend показывает `MfaScreen`.

Тесты: setup требует re-auth, неверный первый code не включает MFA, recovery code
одноразовый, brute-force ограничен, один первый фактор не открывает API, secrets
не попадают в responses/logs.

---

# Часть 19E. Телефон, восстановление и удаление

## Шаг 20. Добавить телефон

Делай после email challenges и step-up. Нужен внешний SMS provider.

Храни encrypted phone, lookup hash и `phone_verified_at`. Нормализуй E.164,
показывай маску, не логируй полный номер.

```text
re-auth -> новый номер -> SMS code -> verification -> заменить основной номер
```

Уведоми старый email/номер. SMS не должен быть единственным сильным фактором из-за
SIM-swap; предпочтительнее TOTP и recovery codes.

---

## Шаг 21. Реализовать восстановление

Ответ запроса reset всегда одинаковый: не раскрывай существование email.

```text
request -> rate limit -> PASSWORD_RESET challenge -> email link/code
-> проверить expiry -> при MFA потребовать recovery factor
-> новый password -> invalid all sessions/challenges -> notification
```

Recovery не должен быть лёгким обходом 2FA.

---

## Шаг 22. Безопасно удалять аккаунт

Нельзя сразу удалить `users`: на него ссылаются workspace/project/task creators,
members и task activity actors.

Сначала пользователь передаёт ownership всех workspace, где он единственный
OWNER, либо отдельно удаляет эти workspace. Backend не назначает случайного owner.

Таблица `account_deletion_requests`: userId, requestedAt, scheduledFor,
cancelTokenHash, cancelledAt, completedAt.

```text
re-auth -> TOTP -> ввести email/DELETE -> проверить ownership
-> request на 7 дней -> закрыть другие sessions -> email отмены
-> scheduled job -> анонимизация
```

При окончании: удалить identities/password/TOTP/recovery/phone/challenges/avatar,
инвалидировать sessions, анонимизировать email и имя, поставить disabled/deleted
status. Созданные проекты, задачи и audit остаются и показывают `Deleted user`.

Тесты: owner blocker, re-auth и MFA обязательны, cancel одноразовый, job не
срабатывает раньше, login после удаления невозможен, рабочие данные остаются,
персональные данные очищаются.

---

# Порядок коммитов

```text
1.  feat(account): add profile and onboarding persistence
2.  refactor(auth): unify external account linking
3.  feat(auth): add OAuth onboarding flow
4.  feat(auth): add GitHub OAuth login
5.  feat(auth): require password confirmation
6.  feat(auth): add local email verification
7.  feat(account): add account settings and avatar
8.  feat(security): add sensitive action verification
9.  feat(security): add TOTP MFA and recovery codes
10. feat(account): add phone recovery and account deletion
```

Не объединяй миграции, OAuth, email, 2FA и deletion в один коммит.

---

# Проверка после каждого подэтапа

```powershell
.\mvnw.cmd test
```

```powershell
cd frontend
npm.cmd run lint
npm.cmd run build
```

Вручную проверяй LOCAL login, первый/повторный Google и GitHub login, refresh на
onboarding/MFA, logout, CSRF, отсутствие secrets в Network/logs и применение
Flyway на существующей и чистой БД.

---

# Что не делать

- Не редактировать применённые `V1`–`V11`.
- Не сохранять OAuth token в `localStorage`.
- Не использовать provider email/login вместо stable subject/id.
- Не считать provider name окончательным именем CollabDesk.
- Не защищать onboarding только frontend redirect-ом.
- Не активировать local account до email verification.
- Не хранить verification/TOTP/recovery codes обычным текстом.
- Не считать email/SMS полноценной заменой TOTP.
- Не отключать последний способ входа.
- Не удалять `users` каскадом с командными данными.
- Не принимать изменяемый userId из frontend — брать из principal.
- Не возвращать security exception и secrets клиенту.

---

# Официальные материалы

- [Spring Security OAuth2 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/index.html)
- [Spring Security OAuth2/OIDC user services](https://docs.spring.io/spring-security/reference/7.0/servlet/oauth2/login/advanced.html)
- [GitHub OAuth web application flow](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps)
- [GitHub API: authenticated user emails](https://docs.github.com/en/rest/users/emails#list-email-addresses-for-the-authenticated-user)
- [Spring Boot email sending](https://docs.spring.io/spring-boot/reference/io/email.html)
- [Spring Security MFA](https://docs.spring.io/spring-security/reference/servlet/authentication/mfa.html)
- [OWASP email verification](https://cheatsheetseries.owasp.org/cheatsheets/Email_Validation_and_Verification_Cheat_Sheet.html)
- [OWASP Multifactor Authentication](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html)
- [NIST account recovery guidance](https://pages.nist.gov/800-63-4/sp800-63b.html#account-recovery)
