# Этап 18. Вход и регистрация через Google

## Что должно получиться

После этого этапа на экране авторизации появится кнопка `Continue with Google`.

Пользователь нажимает её, выбирает Google-аккаунт и возвращается в CollabDesk уже
с обычной HTTP-сессией. После этого приложение работает так же, как после входа
по email и паролю: `/api/v1/auth/me`, workspace, проекты, задачи и CSRF остаются
без отдельной логики для Google.

Итоговый сценарий:

```text
React -> GET /oauth2/authorization/google
      -> Google login and consent
      -> GET /login/oauth2/code/google
      -> Spring проверяет ответ Google
      -> находит или создаёт User и AuthIdentity(GOOGLE)
      -> создаёт обычную JSESSIONID-сессию
      -> возвращает пользователя во frontend
      -> frontend вызывает GET /api/v1/auth/me
```

Здесь не нужно писать собственную проверку Google-токена и не нужно отдавать
Google access token в React. Authorization Code flow полностью обрабатывает
Spring Security на backend.

---

## 0. Что означают OAuth2, OIDC и остальные слова

### Самая простая модель

Google не получает доступ к workspace и задачам CollabDesk. CollabDesk также не
получает пароль от Google. Google только говорит backend примерно следующее:

```text
Я, Google, проверил этого человека.
Вот его постоянный Google id.
Вот подтверждённый email и имя, которые он разрешил передать.
```

После этого backend сопоставляет Google id со своим `users.id`. Все дальнейшие
решения принимает уже CollabDesk:

```text
Google sub -> AuthIdentity -> User -> WorkspaceMember -> роли и права
```

То есть Google отвечает на вопрос «кто это?», а CollabDesk — «что ему можно?».

### OAuth2

`OAuth 2.0` — протокол безопасной выдачи ограниченного доступа. В нашем случае
он описывает переходы браузера и обмен временного authorization code на токены.

Почему это лучше передачи Google-пароля:

- пароль вводится только на настоящей странице Google;
- CollabDesk никогда его не видит и не хранит;
- Google показывает пользователю, какие данные запрашивает CollabDesk;
- временный code нельзя использовать как постоянный пароль;
- Client Secret остаётся на backend.

Сам OAuth2 исторически говорит в основном о разрешении доступа, но не полностью
описывает стандартизированную личность пользователя. Поэтому поверх него нужен
OIDC.

### OIDC

`OIDC`, или `OpenID Connect`, — слой аутентификации поверх OAuth2. Он добавляет
стандартный способ сказать приложению, кто вошёл.

OIDC включается scope `openid`. В ответ появляется `ID Token` с claims:

```text
sub             постоянный id Google-аккаунта
email           email
email_verified  подтвердил ли Google этот email
name            имя профиля
iss             кто выпустил токен
aud             для какого OAuth Client он выпущен
exp             когда токен перестанет действовать
```

Spring Security проверяет подпись, `iss`, `aud`, срок действия и служебные поля.
Наш код получает уже проверенный `OidcUser`; вручную декодировать JWT или верить
данным из React нельзя.

### OAuth2 и OIDC — не одно и то же

Коротко:

```text
OAuth2: можно ли приложению получить разрешённый доступ?
OIDC:   кто именно вошёл в приложение?
```

Google login использует оба: OAuth2 выполняет безопасный flow, OIDC передаёт
проверенную личность.

### Authorization code

После согласия Google не кладёт постоянный токен прямо в URL. Он возвращает
короткоживущий одноразовый `code` на backend callback. Spring backend сам
обменивает его на токены, используя Client Secret.

Это важно: секрет находится только на сервере, а чувствительные токены не
попадают в React, browser history и `localStorage`.

### Callback и redirect URI

Callback — адрес backend, куда Google возвращает браузер:

```text
http://localhost:8080/login/oauth2/code/google
```

Google разрешит возврат только на URI, заранее записанный в Google Console.
Так злоумышленник не сможет подменить callback своим сайтом и забрать code.

### Scope

Scope — разрешённый объём данных. Мы запрашиваем только:

```text
openid  включить OIDC
profile получить обычное имя профиля
email   получить email и признак его подтверждения
```

Это не даёт доступа к Gmail, Drive или Calendar. Для входа они не нужны.

### `sub`

`sub`, или subject, — постоянный идентификатор Google-аккаунта для нашего
OAuth-клиента. Его мы сохраняем в `auth_identities.provider_subject`.

Email нельзя использовать вместо `sub` как основной ключ: адрес может
измениться. При повторном входе сначала ищется `GOOGLE + sub`, а email нужен для
первичного создания или безопасного linking с локальным account.

### User и AuthIdentity

Это разные сущности:

```text
User
  -> профиль и внутренний userId CollabDesk

AuthIdentity
  -> способ доказать, что это данный User
```

У одного User могут быть две identity:

```text
LOCAL  + email + password hash
GOOGLE + sub   + password_hash = NULL
```

Поэтому вход разными способами может вести в те же workspace и проекты.

### Principal

Principal — объект текущего вошедшего пользователя внутри Spring Security.

При local login Spring создаёт `AuthenticatedUserPrincipal`, а при Google login
— `GoogleOidcPrincipal`. Оба реализуют `CollabDeskPrincipal`, поэтому контроллеру
не важно, каким способом человек подтвердил личность:

```java
@AuthenticationPrincipal CollabDeskPrincipal principal
```

Контроллер берёт одинаковый `principal.getUserId()` и запускает существующие
проверки прав.

### HTTP session и JSESSIONID

Google token не используется frontend при каждом запросе. После успешного OIDC
login Spring создаёт обычную серверную сессию и отправляет cookie `JSESSIONID`.

Дальше происходит привычное:

```text
browser отправляет JSESSIONID
-> Spring находит principal в session
-> controller получает CollabDeskPrincipal
-> service проверяет роли и доступ
```

Logout завершает эту локальную сессию CollabDesk. Он не удаляет Google account и
не выходит из Google во всех других вкладках.

### CSRF и state

Это две похожие по цели, но разные защиты:

- OAuth `state` связывает начало Google flow с callback и защищает сам вход;
- существующий CSRF token защищает изменяющие запросы CollabDesk после входа.

Отключать CSRF ради Google login не нужно.

### Что делает каждый новый класс

```text
CollabDeskOidcUserService
  получает проверенного OidcUser от стандартного Spring OidcUserService

GoogleAccountService
  ищет или создаёт User и сохраняет AuthIdentity(GOOGLE, sub)

GoogleOidcPrincipal
  объединяет OIDC-данные и внутренний userId CollabDesk

CollabDeskPrincipal
  общий контракт для local и Google login

SecurityConfig
  включает flow, success redirect, failure redirect и HTTP session
```

Frontend-кнопка ничего не проверяет сама. Она только начинает полную навигацию
на `/oauth2/authorization/google`.

---

## 1. Что уже готово в проекте

В `AuthProvider` уже существует:

```java
public enum AuthProvider {
    LOCAL,
    GOOGLE
}
```

В `AuthIdentity` уже есть фабрика:

```java
AuthIdentity.google(user, providerSubject)
```

А первая миграция уже разрешает Google identity:

```text
provider = GOOGLE
provider_subject = постоянный Google subject (claim sub)
password_hash = NULL
```

Поэтому новую Flyway-миграцию для этого этапа не создавай. Не изменяй V1 и не
трогай её checksum. Текущая структура базы уже подходит.

Важно различать два значения:

- `users.email` — адрес для отображения, приглашений и поиска пользователя;
- `auth_identities.provider_subject` — постоянный идентификатор Google `sub`.

После первого входа пользователя всегда нужно искать по сочетанию
`GOOGLE + sub`, а не только по email. Email может измениться, а `sub` является
идентификатором аккаунта у провайдера.

---

## 2. Создай OAuth-клиент в Google Cloud

Открой [Google Cloud Console](https://console.cloud.google.com/) и создай либо
выбери проект для CollabDesk.

В разделе `Google Auth Platform` последовательно настрой:

1. `Branding` — название приложения, support email и contact email.
2. `Audience` — для обычной разработки выбери `External`.
3. Пока приложение в режиме `Testing`, добавь свой Google email в `Test users`.
4. В `Data Access` оставь только минимальные scopes:

```text
openid
profile
email
```

5. В `Clients` создай OAuth Client с типом `Web application`.
6. Добавь точный redirect URI для локального backend:

```text
http://localhost:8080/login/oauth2/code/google
```

URI должен совпадать полностью: схема, hostname, port, path и наличие завершающего
слеша имеют значение. `localhost` разрешён для локальной разработки без HTTPS.

Если Google показывает `redirect_uri_mismatch`, сначала посмотри фактический
`redirect_uri` в адресной строке и сравни его с записью в Google Console.

Для production добавь отдельно:

```text
https://your-domain.example/login/oauth2/code/google
```

Production должен использовать HTTPS и домен, которым ты управляешь. Не добавляй
маски вида `https://*.example.com`: Google требует конкретные URI.

После создания клиента Google покажет:

```text
Client ID
Client Secret
```

Secret нельзя добавлять в Git, JavaScript или `VITE_*` переменные. Он используется
только Spring backend.

---

## 3. Добавь backend-зависимость

Проект использует Spring Boot 4, поэтому в `pom.xml` добавь Boot 4 starter:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security-oauth2-client</artifactId>
</dependency>
```

Это OAuth2 client, а не resource server и не authorization server:

- Google является authorization server;
- CollabDesk является OAuth2/OIDC client;
- CollabDesk API по-прежнему защищён `JSESSIONID`, а не bearer JWT.

После изменения проверь, что зависимости разрешаются:

```powershell
.\mvnw.cmd -DskipTests compile
```

---

## 4. Добавь переменные окружения

В локальный `.env` добавь:

```properties
GOOGLE_CLIENT_ID=replace-with-google-client-id
GOOGLE_CLIENT_SECRET=replace-with-google-client-secret
APP_FRONTEND_URL=http://localhost:5173
```

В `application.properties` добавь:

```properties
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
spring.security.oauth2.client.registration.google.scope=openid,profile,email

app.frontend-url=${APP_FRONTEND_URL:http://localhost:5173}
```

Для Google не нужно вручную задавать authorization URI, token URI, JWK URI и
user-info URI. Регистрация называется `google`, поэтому Spring Boot применит
встроенные настройки Google.

Стандартный callback Spring:

```text
{baseUrl}/login/oauth2/code/{registrationId}
```

В локальном запуске это:

```text
http://localhost:8080/login/oauth2/code/google
```

Если production работает за reverse proxy, дополнительно настрой корректную
передачу `X-Forwarded-Proto` и `X-Forwarded-Host`, а в Spring включи:

```properties
server.forward-headers-strategy=framework
```

Иначе Spring может сформировать callback с `http` или внутренним адресом proxy.

---

## 5. Сделай общий principal для LOCAL и GOOGLE

Сейчас контроллеры получают конкретный `AuthenticatedUserPrincipal`. После
Google login Spring создаёт OIDC principal другого типа. Если ничего не менять,
`@AuthenticationPrincipal AuthenticatedUserPrincipal principal` окажется `null`.

Сделай маленький общий интерфейс, например:

```text
auth/security/CollabDeskPrincipal.java
```

```java
public interface CollabDeskPrincipal {
    Long getUserId();
    String getEmail();
    String getDisplayName();
    UserStatus getStatus();
}
```

Дальше:

1. `AuthenticatedUserPrincipal` продолжает реализовывать `UserDetails` и
   дополнительно реализует `CollabDeskPrincipal`.
2. Создай `GoogleOidcPrincipal`, который реализует `OidcUser` и
   `CollabDeskPrincipal`.
3. Во всех контроллерах замени тип аргумента:

```java
@AuthenticationPrincipal AuthenticatedUserPrincipal principal
```

на:

```java
@AuthenticationPrincipal CollabDeskPrincipal principal
```

Сервисы продолжат получать `principal.getUserId()`, поэтому бизнес-права
workspace, проектов и задач менять не придётся.

### Пример Google principal

Удобнее не реализовывать все OIDC-методы вручную, а унаследоваться от
`DefaultOidcUser`:

```java
public final class GoogleOidcPrincipal
        extends DefaultOidcUser
        implements CollabDeskPrincipal {

    private final Long userId;
    private final String email;
    private final String displayName;
    private final UserStatus status;

    public GoogleOidcPrincipal(User user, OidcUser oidcUser) {
        super(
                oidcUser.getAuthorities(),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                "sub"
        );
        this.userId = user.getId();
        this.email = user.getEmail();
        this.displayName = user.getDisplayName();
        this.status = user.getStatus();
    }

    @Override
    public Long getUserId() {
        return userId;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public UserStatus getStatus() {
        return status;
    }
}
```

Если твоя версия `DefaultOidcUser` не принимает nullable `userInfo`, используй
подходящий конструктор: с `userInfo`, когда он присутствует, и без него в
остальных случаях.

Не используй Google access token как пароль и не сохраняй его в
`auth_identities.password_hash`.

---

## 6. Загружай User вместе с AuthIdentity

`spring.jpa.open-in-view=false`, а связь `AuthIdentity.user` является lazy.
Поэтому repository-метод для Google identity должен сразу загрузить пользователя.

В `AuthIdentityRepository` добавь отдельный метод:

```java
@EntityGraph(attributePaths = "user")
Optional<AuthIdentity> findWithUserByProviderAndProviderSubject(
        AuthProvider provider,
        String providerSubject
);

boolean existsByUser_IdAndProvider(Long userId, AuthProvider provider);
```

Также оставь существующий уникальный индекс:

```text
UNIQUE(provider, provider_subject)
```

Он защищает от создания двух identity для одного Google-аккаунта.

---

## 7. Реализуй создание и поиск Google-пользователя

Создай сервис:

```text
auth/google/GoogleAccountService.java
```

Он получает уже проверенные Spring Security данные:

```text
sub
email
email_verified
name
```

Логика должна быть такой:

```text
email отсутствует или email_verified != true
-> отказать во входе

найдена AuthIdentity(GOOGLE, sub)
-> проверить, что User ACTIVE
-> вернуть существующего User

identity не найдена, но найден User с таким подтверждённым email
-> привязать к нему новую AuthIdentity(GOOGLE, sub)
-> вернуть существующего User

не найдены ни identity, ни User
-> создать User(email, displayName)
-> создать AuthIdentity.google(user, sub)
-> вернуть нового User
```

Для этого проекта выбираем автоматическую привязку по подтверждённому Google
email. Это означает, что пользователь, который раньше зарегистрировался локально
с `alex@example.com`, после Google login с подтверждённым `alex@example.com`
попадёт в тот же CollabDesk account, а не получит дубликат.

Обязательно проверяй `email_verified`. Без этой проверки нельзя безопасно
привязывать Google identity к существующему email.

Пример основы сервиса:

```java
@Service
public class GoogleAccountService {
    private final AuthIdentityRepository authIdentityRepository;
    private final UserRepository userRepository;

    @Transactional
    public User findOrCreate(OidcUser oidcUser) {
        String subject = requireText(oidcUser.getSubject(), "Google sub");
        String email = requireText(oidcUser.getEmail(), "Google email")
                .trim()
                .toLowerCase(Locale.ROOT);

        if (!Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            throw new OAuth2AuthenticationException("google_email_not_verified");
        }

        Optional<AuthIdentity> existingIdentity = authIdentityRepository
                .findWithUserByProviderAndProviderSubject(
                        AuthProvider.GOOGLE,
                        subject
                );

        if (existingIdentity.isPresent()) {
            return requireActive(existingIdentity.get().getUser());
        }

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(
                        new User(email, googleDisplayName(oidcUser, email))
                ));

        requireActive(user);

        if (authIdentityRepository.existsByUser_IdAndProvider(
                user.getId(),
                AuthProvider.GOOGLE
        )) {
            throw new OAuth2AuthenticationException("google_identity_conflict");
        }

        authIdentityRepository.save(AuthIdentity.google(user, subject));
        return user;
    }
}
```

`googleDisplayName(...)` использует claim `name`. Если он пустой, можно взять
часть email до `@`, но результат всё равно должен пройти ограничения `User`:
не пустой и не длиннее 100 символов.

Метод должен быть транзакционным. В production также обработай редкую гонку,
когда два callback одного нового пользователя пришли одновременно: уникальный
индекс может выбросить `DataIntegrityViolationException`. После rollback можно
повторно прочитать identity по `GOOGLE + sub`.

Не обновляй автоматически `displayName` при каждом входе. Пользователь мог
изменить имя внутри CollabDesk и не ожидает, что Google перезапишет его.

---

## 8. Подключи OidcUserService

Создай:

```text
auth/google/CollabDeskOidcUserService.java
```

Его задача:

1. Передать сетевую работу стандартному `OidcUserService`.
2. Получить уже проверенный `OidcUser`.
3. Разрешать эту логику только для registration id `google`.
4. Найти или создать локального `User`.
5. Вернуть `GoogleOidcPrincipal`.

```java
@Service
public class CollabDeskOidcUserService
        implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate = new OidcUserService();
    private final GoogleAccountService googleAccountService;

    @Override
    public OidcUser loadUser(OidcUserRequest request)
            throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(request);

        if (!"google".equals(request.getClientRegistration().getRegistrationId())) {
            throw new OAuth2AuthenticationException("unsupported_provider");
        }

        User user = googleAccountService.findOrCreate(oidcUser);
        return new GoogleOidcPrincipal(user, oidcUser);
    }
}
```

Не доверяй параметрам, которые frontend мог бы прислать сам. Email, `sub` и
`email_verified` должны браться только из проверенного OIDC ответа.

---

## 9. Измени SecurityConfig

Существующий form login нужно сохранить. Google login добавляется рядом и не
заменяет email/password.

Разреши служебные OAuth endpoints:

```java
.requestMatchers(
        HttpMethod.GET,
        "/oauth2/**",
        "/login/oauth2/**"
).permitAll()
```

Затем подключи OIDC service:

```java
.oauth2Login(oauth -> oauth
        .userInfoEndpoint(userInfo -> userInfo
                .oidcUserService(collabDeskOidcUserService)
        )
        .successHandler((request, response, authentication) ->
                response.sendRedirect(frontendUrl)
        )
        .failureHandler((request, response, exception) ->
                response.sendRedirect(frontendUrl + "/?oauth=failed")
        )
)
```

`frontendUrl` прочитай через configuration properties или `@Value` из
`app.frontend-url`. Не бери redirect URL из query-параметра пользователя — это
может создать open redirect.

На failure redirect не добавляй в URL текст исключения, Google token или email.
Frontend достаточно безопасного кода `oauth=failed`.

После успешного callback Spring Security сам создаёт и сохраняет HTTP-сессию.
Не нужно вручную создавать `JSESSIONID`.

CSRF оставь включённым. OAuth callback защищается параметром `state`, а обычные
POST/PATCH/DELETE запросы CollabDesk продолжают использовать существующий
`/csrf` и `X-XSRF-TOKEN`.

---

## 10. Добавь кнопку во frontend

В `AuthShell` покажи кнопку и на login, и на registration экране. Google сам
решит, нужно создать account или войти в существующий.

Используй обычную ссылку, а не `fetch`:

```jsx
<a
  className="google-auth-button"
  href="/oauth2/authorization/google"
>
  <GoogleIcon />
  Continue with Google
</a>
```

OAuth требует полной навигации браузера через Google. `fetch` здесь не подходит.

Под кнопкой можно поставить разделитель:

```text
Continue with Google
──────── or continue with email ────────
Email
Password
Sign in
```

Используй официальный четырёхцветный знак Google либо простой нейтральный SVG,
не перекрашивай логотип целиком в цвет CollabDesk.

После success redirect приложение уже выполняет `getCurrentUser()` при загрузке.
Поэтому отдельный endpoint для frontend не нужен.

При загрузке страницы прочитай `oauth` из `URLSearchParams`. Для
`oauth=failed` покажи понятное сообщение:

```text
Google sign-in could not be completed. Please try again.
```

После чтения параметра убери его через `history.replaceState`, чтобы ошибка не
появлялась снова после F5.

---

## 11. Добавь proxy для локального Vite

Frontend работает на `5173`, backend — на `8080`. Добавь в
`frontend/vite.config.js` ещё один proxy:

```js
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
  '/csrf': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
  '/oauth2': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
}
```

Google callback всё равно идёт напрямую на backend:

```text
http://localhost:8080/login/oauth2/code/google
```

После callback backend перенаправляет браузер обратно на
`APP_FRONTEND_URL=http://localhost:5173`.

Cookie привязана к hostname, а не к port, поэтому localhost-сессия будет
доступна запросам через Vite proxy.

---

## 12. Правила linking и крайние случаи

Реализуй следующие правила явно, чтобы поведение не зависело от случайного
порядка repository-запросов.

| Ситуация | Ожидаемый результат |
|---|---|
| Новый подтверждённый Google email | создать `User` и Google identity |
| Повторный вход того же `sub` | использовать существующую identity |
| Уже есть LOCAL account с тем же подтверждённым email | добавить Google identity к тому же `User` |
| Google email не подтверждён | отказать |
| User имеет статус `DISABLED` | отказать |
| У Google изменился display name | не перезаписывать имя CollabDesk автоматически |
| У Google изменился email, но `sub` прежний | найти account по identity; не создавать новый |
| Другой `sub` пришёл с email существующего Google account | не создавать вторую Google identity без отдельной проверки |

Последний пункт важен. Для простого первого варианта разреши linking по email,
только если у пользователя ещё нет `GOOGLE` identity. Если Google identity уже
есть с другим `sub`, останови вход и запиши безопасный warning без токенов.

Не объединяй два разных `User` автоматически. Если такие дубликаты когда-нибудь
появятся, account merge должен быть отдельной подтверждаемой операцией.

---

## 13. Тесты backend

### GoogleAccountServiceTest

Проверь отдельно:

1. Новый подтверждённый Google user создаёт одну запись `User` и одну
   `AuthIdentity(GOOGLE)`.
2. Повторный вход по тому же `sub` ничего не создаёт повторно.
3. Существующий LOCAL user с тем же email получает вторую identity, но остаётся
   тем же `User`.
4. Email нормализуется в lowercase.
5. Неподтверждённый email отклоняется.
6. Пустые `sub` и email отклоняются.
7. `DISABLED` user не получает сессию.
8. Существующее CollabDesk display name не перезаписывается Google name.

### CollabDeskOidcUserServiceTest

Замокай стандартный delegate либо вынеси его в bean, чтобы проверить:

- Google registration вызывает `GoogleAccountService`;
- возвращается `GoogleOidcPrincipal` с правильным `userId`;
- неизвестный registration id отклоняется;
- OIDC exception не превращается в зарегистрированного пользователя.

### SecurityMvcTest

Используй OAuth2/OIDC test support Spring Security и проверь:

```text
GET /oauth2/authorization/google -> доступен без сессии
GET /api/v1/auth/me с GoogleOidcPrincipal -> 200 и правильный userId/email/name
GET /api/v1/workspaces с GoogleOidcPrincipal -> проходит обычную авторизацию
POST без CSRF после Google login -> 403
POST с CSRF после Google login -> проверяется обычными правилами endpoint
LOCAL form login по-прежнему работает
logout завершает и LOCAL, и GOOGLE session
```

### Repository integration test

Проверь MySQL-ограничения:

- у Google identity `password_hash IS NULL`;
- одинаковый `(GOOGLE, sub)` нельзя сохранить дважды;
- один `User` может иметь `LOCAL` и `GOOGLE` identity;
- `findWithUserByProviderAndProviderSubject(...)` возвращает доступного `User`
  при `open-in-view=false`.

---

## 14. Ручная проверка

Перед запуском проверь, что `.env` содержит реальные credentials и не попал в
Git.

Запусти backend и frontend:

```powershell
.\mvnw.cmd spring-boot:run
```

```powershell
cd frontend
npm.cmd run dev
```

Затем проверь:

1. Открыть `http://localhost:5173`.
2. Нажать `Continue with Google`.
3. Выбрать email, добавленный в Google test users.
4. Подтвердить минимальные scopes.
5. Убедиться, что браузер вернулся на `localhost:5173`.
6. Убедиться, что dashboard загрузился без повторной формы входа.
7. Проверить `/api/v1/auth/me` в Network: ответ `200`, пароль и Google token в
   ответе отсутствуют.
8. В MySQL проверить одну Google identity:

```sql
SELECT id, user_id, provider, provider_subject, password_hash
FROM auth_identities
WHERE provider = 'GOOGLE';
```

9. Выйти и войти тем же Google account ещё раз — новая строка не должна
   появиться.
10. Проверить local login старого пользователя — он не должен сломаться.

После этого выполни:

```powershell
.\mvnw.cmd test
```

```powershell
cd frontend
npm.cmd run lint
npm.cmd run build
```

---

## 15. Частые ошибки

### `redirect_uri_mismatch`

В Google Console отсутствует точный callback. Для локального backend он должен
быть:

```text
http://localhost:8080/login/oauth2/code/google
```

### `access_denied` или приложение доступно только некоторым пользователям

OAuth app находится в `Testing`, а email не добавлен в `Test users`.

### После Google страница вернулась, но пользователь снова не авторизован

Проверь:

- сохранилась ли `JSESSIONID`;
- одинаковый ли hostname используется везде (`localhost`, а не смесь
  `localhost` и `127.0.0.1`);
- ведёт ли success handler на `APP_FRONTEND_URL`;
- возвращает ли custom OIDC service именно `GoogleOidcPrincipal`;
- принимает ли `/auth/me` общий `CollabDeskPrincipal`.

### `@AuthenticationPrincipal` равен `null`

Один из контроллеров всё ещё ожидает конкретный `AuthenticatedUserPrincipal`
вместо общего `CollabDeskPrincipal`.

### LazyInitializationException при чтении identity

Repository вернул `AuthIdentity` без загруженного `user`. Используй
`@EntityGraph(attributePaths = "user")` либо fetch join внутри транзакции.

### Google вход сломал form login

В `SecurityConfig` случайно заменили `.formLogin(...)` на `.oauth2Login(...)`.
Оба механизма должны быть подключены одновременно.

---

## 16. Что не нужно делать на этом этапе

Не добавляй:

- JWT для собственного API;
- Google token в `localStorage`;
- пароль для Google-only пользователя;
- хранение refresh token, если Google нужен только для входа;
- доступ к Google Drive, Calendar, Gmail и другим API;
- отдельную workspace-роль для Google users;
- новую Flyway-миграцию без реального изменения схемы;
- автоматическое объединение разных CollabDesk users.

Google отвечает только за подтверждение личности. Все роли, project access и
task permissions по-прежнему принадлежат CollabDesk.

---

## 17. Критерии готовности

Этап завершён, если:

- кнопка Google есть на login и registration экранах;
- OAuth начинается через `/oauth2/authorization/google`;
- callback обрабатывает Spring backend;
- новый Google user создаётся один раз;
- повторный вход использует тот же `User`;
- LOCAL и GOOGLE identity могут принадлежать одному `User`;
- `provider_subject` содержит Google `sub`, а не email;
- `password_hash` Google identity равен `NULL`;
- неподтверждённый email и `DISABLED` user отклоняются;
- после входа работает существующий `/api/v1/auth/me`;
- существующие workspace/project/task permissions работают без специальных
  исключений для Google;
- logout завершает Google-authenticated HTTP session;
- secret отсутствует во frontend, логах и Git;
- backend tests, frontend lint и frontend build проходят.

---

## Официальные материалы

- [Spring Security: OAuth 2.0 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/index.html)
- [Spring Security: OAuth2 Login core configuration](https://docs.spring.io/spring-security/reference/7.0/servlet/oauth2/login/core.html)
- [Spring Boot 4: OAuth2 client configuration](https://docs.spring.io/spring-boot/4.0/reference/web/spring-security.html#web.security.oauth2.client)
- [Google: OAuth 2.0 for web server applications](https://developers.google.com/identity/protocols/oauth2/web-server)
- [Google: OAuth 2.0 policies](https://developers.google.com/identity/protocols/oauth2/policies)
