# Этап 19B. Подтверждение локального email — подробный маршрут самостоятельной реализации

Этот файл раскрывает шаги 10–12 из `19-account-onboarding-github-and-security.md`.
Цель — не дать готовую реализацию для копирования, а провести по архитектуре так,
чтобы каждый класс и каждое правило безопасности были понятны.

## Текущая точка

Шаги 10–12 реализованы. В проекте уже есть Mailpit/SMTP-конфигурация,
`MailService`, challenge entity и V13, безопасная генерация и HMAC кода,
confirm/resend API, блокировка LOCAL login до подтверждения, автоматическая
session по варианту B и frontend-экран ввода кода.

Установленные правила:

```text
CODE_TTL = 10 минут от момента каждой новой выдачи
RESEND_COOLDOWN = 60 секунд
MAX_FAILED_ATTEMPTS = 3
confirm success = authenticated HTTP session + onboarding
```

То есть при resend срок всегда становится `now + 10 минут`, а не
`старый expiresAt + 10 минут`. Повторно выполнять какой-либо шаг инструкции не
нужно. Остаётся только ручная smoke-проверка реального письма через Mailpit:

```text
docker compose up -d
запустить backend и frontend
зарегистрировать новый LOCAL account
открыть http://localhost:8025
проверить sender, recipient, шестизначный code и срок 10 минут
ввести code и убедиться, что открылся onboarding без повторного login
```

Сам файл был дополнительно расширен начиная с **раздела 10.2**:

- в 10.2 добавлено объяснение каждой SMTP property и источника её значения;
- в 10.3 добавлен разбор именно твоего `MailService.java` и объяснено, почему
  отдельный interface сейчас не обязателен;
- в 10.4 описаны уже созданные и адаптированные mail tests;
- после шага 12 добавлена полная очередь будущих TDD-тестов (`TDD-0`–`TDD-12`).

После завершения 19B сценарий локальной регистрации должен выглядеть так:

```text
регистрация
    -> User с emailVerifiedAt = NULL
    -> LOCAL identity с BCrypt password hash
    -> одноразовый EMAIL_VERIFICATION challenge
    -> commit транзакции
    -> письмо с шестизначным кодом
    -> экран ввода кода
    -> успешная проверка кода
    -> emailVerifiedAt заполняется
    -> login или автоматическая session
    -> общий onboarding
```

Google и GitHub в этот flow не входят: их verified email уже подтверждён внешним
провайдером.

---

## 1. Что уже есть в проекте

Перед началом важно понимать текущую точку.

- `spring-boot-starter-mail` уже добавлен в `pom.xml`. Не добавляй dependency
  второй раз.
- `RegisterRequest` уже содержит `passwordConfirmation`.
- `AuthController` уже сравнивает пароли и возвращает
  `passwords_do_not_match`.
- `User` уже имеет `emailVerifiedAt`, `isEmailVerified()` и
  `markEmailVerified(Instant)`.
- `User.pendingLocal(...)` создаёт неподтверждённого пользователя.
- `RegistrationService` сейчас вызывает `User.pendingLocalOnboarding(...)`, но
  этот factory пока оставляет email подтверждённым. Это нужно исправить.
- После регистрации frontend сейчас сразу вызывает `loginUser(...)`. После 19B
  так делать нельзя: сначала должен открыться экран подтверждения email.
- `LocalUserDetailsService` сейчас разрешает вход независимо от
  `emailVerifiedAt`. Его нужно закрыть для неподтверждённых LOCAL-аккаунтов.
- В `docker-compose.yaml` уже есть MySQL, Redis и Mailpit.

Перед изменениями запусти baseline:

```powershell
.\mvnw.cmd test
cd frontend
npm.cmd run lint
npm.cmd run build
```

Если baseline уже красный, сначала зафиксируй причину. Иначе позже будет трудно
отличить свой regression от старой проблемы.

---

## 2. Граница 19B

В этот этап входят:

1. SMTP-конфигурация и локальный Mailpit.
2. Абстракция отправки писем.
3. Таблица одноразовых challenges.
4. Генерация, безопасное хранение и проверка шестизначного кода.
5. Expiration, resend cooldown, лимит попыток и инвалидирование старого кода.
6. Создание неподтверждённого LOCAL-пользователя.
7. Запрет login до подтверждения.
8. API confirm/resend.
9. Экран ввода кода во frontend.
10. Unit, repository и integration tests.

Пока не входят:

- password reset — для него подготовь `purpose`, но сам flow будет позже;
- TOTP 2FA и recovery-коды;
- SMS;
- смена основного email;
- HTML-шаблонизатор писем;
- гарантированная очередь доставки через transactional outbox.

---

## 3. Архитектура и ответственность слоёв

```text
AuthController
    -> RegistrationService
        -> UserRepository
        -> AuthIdentityRepository
        -> VerificationChallengeService
            -> VerificationChallengeRepository
            -> SecureRandom / CodeGenerator
            -> CodeHasher
            -> publish EmailVerificationRequested

после commit:
EmailVerificationMailListener
    -> MailService
        -> JavaMailSender
```

Почему нужны границы:

- controller занимается HTTP, DTO и status codes;
- registration service управляет одной бизнес-операцией регистрации;
- challenge service владеет правилами одноразовых кодов;
- mail service знает, как собрать и отправить письмо, но ничего не знает о БД;
- listener отделяет commit данных от внешней SMTP-доставки.

Не отправляй письмо прямо из controller и не клади `JavaMailSender` в
`RegistrationService`. Иначе бизнес-логику будет трудно тестировать, а ошибка
SMTP начнёт влиять на транзакцию регистрации.

---

# Шаг 10. SMTP и Mailpit

## 10.1. Добавить Mailpit в Docker Compose

Добавь в `docker-compose.yaml` сервис `mailpit`.

Нужные порты:

- `1025` — SMTP, сюда Spring отправляет письмо;
- `8025` — web UI, здесь разработчик читает перехваченные письма.

Ориентир структуры:

```yaml
mailpit:
  image: axllent/mailpit:<зафиксированная-версия>
  container_name: collabdesk-mailpit
  ports:
    - "1025:1025"
    - "8025:8025"
```

Версию image лучше фиксировать, а не использовать `latest`: одинаковый compose
должен предсказуемо запускаться через месяц и на другой машине.

После запуска проверь:

```powershell
docker compose up -d
docker compose ps
```

Открой `http://localhost:8025`. Пустой inbox означает, что Mailpit работает.

### Зачем Mailpit

Mailpit говорит по настоящему SMTP-протоколу, поэтому backend работает почти так
же, как с production mail provider. Но Mailpit не доставляет письмо наружу — оно
остаётся в локальном web UI. Это безопаснее, чем тестировать на реальных адресах.

## 10.2. Настроить Spring Mail

В `application.properties` свяжи Spring properties с env:

```properties
spring.mail.host=${MAIL_HOST:localhost}
spring.mail.port=${MAIL_PORT:1025}
spring.mail.username=${MAIL_USERNAME:}
spring.mail.password=${MAIL_PASSWORD:}
spring.mail.properties.mail.smtp.auth=${MAIL_AUTH:false}
spring.mail.properties.mail.smtp.starttls.enable=${MAIL_STARTTLS:false}
spring.mail.properties.mail.smtp.connectiontimeout=${MAIL_CONNECTION_TIMEOUT_MS:5000}
spring.mail.properties.mail.smtp.timeout=${MAIL_READ_TIMEOUT_MS:5000}
spring.mail.properties.mail.smtp.writetimeout=${MAIL_WRITE_TIMEOUT_MS:5000}

app.mail.from=${MAIL_FROM:no-reply@collabdesk.local}
```

Здесь слева и справа находятся разные уровни конфигурации:

```text
spring.mail.host=${MAIL_HOST:localhost}
^^^^^^^^^^^^^^^^ ^^^^^^^^^ ^^^^^^^^^
Spring property   env name  default value
```

- `spring.mail.*` читает Spring Boot и на их основе создаёт `JavaMailSender`;
- `MAIL_*` — выбранные нами имена переменных окружения;
- значение после `:` — fallback, если env-переменная отсутствует;
- `app.mail.from` — наша собственная property. Spring Mail не использует её
  автоматически: текущий `MailService` получает её через constructor parameter
  с `@Value`.

### Что означает каждое поле

| Property | Что это | Откуда взять для локальной разработки | Откуда взять в production |
|---|---|---|---|
| `spring.mail.host` / `MAIL_HOST` | Адрес SMTP-сервера, к которому подключается backend | `localhost`, если Spring запускается из IntelliJ/терминала на Windows, а Mailpit проброшен на host | Из панели или документации почтового провайдера, например его SMTP hostname |
| `spring.mail.port` / `MAIL_PORT` | TCP-порт SMTP-сервера | `1025` — стандартный SMTP-порт Mailpit | Из настроек провайдера; часто это `587` для STARTTLS или `465` для implicit TLS, но нельзя угадывать — бери точное значение провайдера |
| `spring.mail.username` / `MAIL_USERNAME` | Логин SMTP-аккаунта | Пустая строка: стандартный Mailpit не требует authentication | Провайдер выдаёт SMTP username; это не обязательно адрес обычного пользователя |
| `spring.mail.password` / `MAIL_PASSWORD` | Пароль/API credential для SMTP | Пустая строка | Создаётся в панели провайдера; иногда называется SMTP password или API key. Не используй пароль от личной почты, если провайдер поддерживает отдельные credentials |
| `mail.smtp.auth` / `MAIL_AUTH` | Должен ли клиент отправлять username/password | `false` для стандартного Mailpit | Обычно `true`, если provider выдал credentials |
| `mail.smtp.starttls.enable` / `MAIL_STARTTLS` | После обычного подключения попросить сервер перейти на TLS через команду STARTTLS | `false`: стандартный Mailpit на `1025` работает без TLS | Обычно `true` для порта `587`; сверяйся с провайдером |
| `mail.smtp.connectiontimeout` / `MAIL_CONNECTION_TIMEOUT_MS` | Сколько миллисекунд ждать установления TCP-соединения | Оставь fallback `5000` | Начни с `5000`, затем меняй только по наблюдаемым проблемам |
| `mail.smtp.timeout` / `MAIL_READ_TIMEOUT_MS` | Сколько миллисекунд ждать очередного ответа SMTP-сервера | `5000` | Обычно разумный конечный timeout вроде `5000`; не оставляй бесконечным |
| `mail.smtp.writetimeout` / `MAIL_WRITE_TIMEOUT_MS` | Сколько миллисекунд разрешено на запись письма в соединение | `5000` | Обычно `5000` или значение согласно требованиям инфраструктуры |
| `app.mail.from` / `MAIL_FROM` | Адрес, который получатель увидит в поле From | Любой понятный тестовый адрес, например `no-reply@collabdesk.local` | Подтверждённый sender/domain из панели провайдера, например `no-reply@your-domain.com` |

`8025` сюда не записывается. Это HTTP-порт web-интерфейса Mailpit для человека.
Backend отправляет письма только на SMTP-порт `1025`.

### Какие значения нужны тебе сейчас

Если backend запускается на Windows из IntelliJ, а Mailpit — в Docker с
`"1025:1025"`, достаточно defaults из `application.properties`. Явно в `.env`
их можно записать так:

```properties
MAIL_HOST=localhost
MAIL_PORT=1025
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_AUTH=false
MAIL_STARTTLS=false
MAIL_FROM=no-reply@collabdesk.local
MAIL_CONNECTION_TIMEOUT_MS=5000
MAIL_READ_TIMEOUT_MS=5000
MAIL_WRITE_TIMEOUT_MS=5000
```

Эти локальные значения не нужно где-либо регистрировать или получать: они
следуют из твоего `docker-compose.yaml`. Проброс `1025:1025` означает, что порт
Mailpit доступен backend по адресу `localhost:1025`.

Если позже backend тоже будет работать внутри того же Docker Compose, значение
изменится на:

```properties
MAIL_HOST=mailpit
MAIL_PORT=1025
```

Внутри compose-контейнера `localhost` означает сам контейнер backend, поэтому для
связи контейнер-к-контейнеру используется DNS-имя сервиса `mailpit`. Публиковать
SMTP-порт на host в таком варианте необязательно.

### Где взять production-значения

Сначала выбирается SMTP provider. Это может быть Amazon SES, Mailgun, Postmark,
SendGrid, Brevo или почтовый сервер твоей организации. В его панели обычно есть
раздел `SMTP`, `SMTP credentials` или `Sending domains`. Оттуда копируются:

```text
SMTP hostname       -> MAIL_HOST
SMTP port           -> MAIL_PORT
SMTP username       -> MAIL_USERNAME
SMTP password/key   -> MAIL_PASSWORD
Encryption STARTTLS -> MAIL_STARTTLS=true
Verified sender     -> MAIL_FROM
```

До production тебе эти данные не нужны. Mailpit полностью закрывает локальную
разработку 19B.

Не путай STARTTLS и implicit TLS. Текущая property включает STARTTLS, обычно на
порту `587`. Если выбранный provider требует implicit TLS на `465`, потребуется
отдельная SSL-настройка Spring Mail по документации провайдера; одной
`MAIL_STARTTLS=true` недостаточно.

Если используешь `.env.example`, добавь туда только безопасные примеры. Реальные
SMTP password и verification pepper не коммить.

### Зачем нужны три timeout

SMTP — внешний сетевой ресурс. Без timeout один зависший сервер может надолго
занять request thread:

- connection timeout ограничивает установку соединения;
- read timeout ограничивает ожидание ответа;
- write timeout ограничивает отправку данных.

Mailpit локально не требует username, password, auth и STARTTLS. В production
значения поменяются через env без изменения Java-кода.

## 10.3. Создать почтовый сервис

Текущая достаточная структура:

```text
src/main/java/collabdesk/auth/mail/
    MailService.java
```

Метод 19B:

```text
MailService.sendEmailVerification(destination, code, validity)
```

На 19B реально используется только `sendEmailVerification`. Методы password
reset и security notification пока не добавляй: они появятся вместе с реальными
use cases и их параметрами.

Отдельный interface сейчас **не обязателен**. Один concrete `MailService` можно
подменять Mockito mock в tests так же, как interface. Выделяй interface позже,
если действительно появится вторая реализация (например SMTP и внешний HTTP mail
provider) либо если mail-контракт станет общей границей нескольких модулей.

### Разбор твоего текущего `MailService`

На текущий момент в проекте есть:

```text
MailService.java       // единственная concrete SMTP-реализация
```

Что в `MailService` уже сделано правильно:

- используется `JavaMailSender`, а не ручное SMTP-соединение;
- письмо создаётся через `SimpleMailMessage`;
- `from` берётся из `app.mail.from`, а не зашит в Java;
- `JavaMailSender` и `from` передаются через constructor;
- destination, code и срок `Duration` передаются параметрами;
- метод больше не требует username до onboarding;
- frontend URL больше не используется для ложного route `/{code}`;
- SMTP exception не проглатывается. Позже его сможет обработать
  AFTER_COMMIT-listener, не скрывая ошибку от инфраструктурного слоя.

Осталась одна содержательная правка текста письма:

```text
текущий текст: "follow the link" и "verification link"
фактическое содержимое: шестизначный code
```

Замени эти фразы на «verification code» и «enter this code in CollabDesk».
Например, итоговый смысл body:

```text
Hello!
Your CollabDesk verification code is:
004271
This code expires in 10 minutes.
If you did not create an account, ignore this email.
```

`Duration` для этого этапа подходит. Назови параметр `validity` или
`expiresIn`, потому что `expiresAt` обычно означает абсолютный `Instant`, а у
тебя передаётся продолжительность. И добавь единицу `minutes` после `%d`.

Для первого варианта достаточно `SimpleMailMessage`:

```text
from: CollabDesk address из config
to: destination
subject: Verify your CollabDesk email
body: code + срок действия + предупреждение игнорировать чужой запрос
```

Не включай в письмо password, password hash, challenge id, внутренний user id или
полный stack trace.

### Почему сервис принимает исходный код

SMTP должен отправить человеку именно исходный код. В БД будет лежать только
его HMAC. Исходный код существует короткое время в памяти между генерацией и
отправкой, после чего нигде не сохраняется.

## 10.4. Проверить почтовый слой отдельно

Добавлен unit test:

```text
src/test/java/collabdesk/auth/mail/MailServiceTest.java
```

Он фиксирует уже реализованное поведение и проверяет два сценария:

- сформированный `SimpleMailMessage` передаётся в `JavaMailSender`;
- `MailSendException` выходит наружу и сможет быть пойман будущим listener.

Тест уже адаптирован под текущий constructor и `Duration`; reflection больше не
используется. Он проверяет:

- письмо уходит на нужный destination;
- sender берётся из config;
- subject не пустой;
- body содержит переданный код с leading zero;
- body не требует имени пользователя до onboarding;
- срок отображается предсказуемо;
- SMTP failure передаётся вызывающему коду.

Не проверяй всё письмо одной огромной строкой. Такой test ломается от любого
переноса строки. Проверяй отдельные важные свойства и обязательные фрагменты.

Отдельный test на `MailSendException` нужен не для того, чтобы SMTP service
ловил исключение. Его ответственность — сообщить о failure вызывающему коду.
Ловить и безопасно логировать ошибку будет AFTER_COMMIT listener из шага 11.7.

Затем временно вызови отправку только из test или локального dev-проверочного
сценария и убедись, что письмо появилось в Mailpit. Не создавай публичный
`/test-email` endpoint в production-коде.

### Готовность шага 10

- compose поднимает Mailpit;
- `http://localhost:8025` открывается;
- SMTP host/port/from задаются env;
- есть обязательные timeout;
- остальной бизнес-код вызывает `MailService`, а с `JavaMailSender` работает
  только mail-слой;
- mail service имеет unit test.

### Что уже закрыто у тебя сейчас

- [x] dependency `spring-boot-starter-mail`;
- [x] Mailpit в compose на портах 1025/8025;
- [x] SMTP properties и конечные timeout;
- [x] безопасные локальные значения в `.env.example`;
- [x] базовая отправка `SimpleMailMessage`;
- [x] unit tests текущей реализации;
- [x] constructor injection для `JavaMailSender` и `from`;
- [x] code-flow без URL и без имени до onboarding;
- [x] `Duration` вместо произвольной строки срока;
- [ ] исправлен текст `link` на `code` и добавлено слово `minutes`;
- [ ] ручная доставка письма в Mailpit.

---

# Шаг 11. Одноразовые verification challenges

## 11.1. Выбрать простую модель: одна текущая запись на purpose

Твоя идея правильная для текущего масштаба:

```text
один User
    -> максимум один текущий EMAIL_VERIFICATION challenge
    -> максимум один текущий PASSWORD_RESET challenge
    -> максимум один текущий SENSITIVE_ACTION challenge
```

Это обеспечивается уникальностью `(user_id, purpose)`. Resend не вставляет
вторую строку, а обновляет существующую: новый hash, новый срок, ноль ошибок и
`consumedAt = NULL`. Поэтому предыдущий код автоматически перестаёт работать.

Важно различать:

- «одна запись на purpose» — строка обновляется и история старых кодов теряется;
- «один активный challenge на purpose» — старые строки остаются как история, но
  только одна активна.

Для 19B выбираем первый, более простой вариант. История verification-кодов не
нужна: позже важные факты вроде `EMAIL_VERIFIED` будут записываться в отдельный
security event log, а не храниться через старые секретные challenges.

Ограничение модели: общий `SENSITIVE_ACTION` означает, что запуск одного важного
действия отменит код другого. Это даже безопасное поведение. Если позже нужно
будет одновременно подтверждать разные действия, добавишь `action_type` или
`context` и изменишь unique key на `(user_id, purpose, action_type)`.

## 11.2. Жизненный цикл одной строки

```text
нет строки
    -> issue создаёт ACTIVE challenge

ACTIVE
    -> wrong code: failedAttempts + 1
    -> correct code: consumedAt = now
    -> прошло 10 минут: EXPIRED, даже без UPDATE
    -> resend: эта же строка получает новый codeHash/issuedAt/expiresAt

CONSUMED / EXPIRED / EXHAUSTED
    -> resend обновляет эту же строку и снова делает её ACTIVE
```

Отдельное поле `status` не нужно. Состояние вычисляется:

```text
consumed = consumedAt != null
expired = now >= expiresAt
exhausted = failedAttempts >= MAX_FAILED_ATTEMPTS
active = !consumed && !expired && !exhausted
```

## 11.3. Какие классы нужны и зачем

Рекомендуемый минимальный пакет:

```text
src/main/java/collabdesk/auth/verification/
    VerificationChallenge.java
    VerificationPurpose.java
    VerificationChannel.java
    VerificationChallengeRepository.java
    VerificationCodeGenerator.java
    VerificationCodeHasher.java
    VerificationChallengeService.java
    VerificationIssuedEvent.java
    VerificationMailListener.java
```

### `VerificationChallenge`

JPA entity одной текущей проверки. Она хранит состояние и защищает собственные
инварианты: нельзя использовать просроченный код, число попыток не отрицательное,
после успешной проверки challenge становится consumed.

### `VerificationPurpose`

Enum отвечает на вопрос «что разрешит успешный код?»:

```text
EMAIL_VERIFICATION -> подтвердить основной email
PASSWORD_RESET     -> разрешить установку нового password
SENSITIVE_ACTION   -> подтвердить одно важное действие
```

Purpose обязательно входит в HMAC context. Иначе hash кода для email
verification теоретически можно было бы переиспользовать как password reset.

### `VerificationChannel`

Enum отвечает на вопрос «куда отправили код?»:

```text
EMAIL
SMS
```

На 19B используется только `EMAIL`. Поле можно пока не создавать, если ты точно
хочешь отдельную таблицу для SMS. Но общий `channel` стоит дёшево и позволит
позже использовать ту же механику attempts/expiry для телефона.

### `VerificationChallengeRepository`

Работает с MySQL. Главные операции:

```text
findByUserIdAndPurpose(...)
findByUserIdAndPurposeForUpdate(...) // с PESSIMISTIC_WRITE
save(...)
```

Обычный CRUD даёт `JpaRepository`; вручную нужны только lookup и locking query.

### `VerificationCodeGenerator`

Делает только одно: через `SecureRandom` возвращает строку из шести цифр. Малый
отдельный класс позволяет в service test подставить код `004271` и не зависеть
от случайности.

Можно генерировать прямо в service, это не ошибка. Но тогда для предсказуемого
test придётся передавать в service `SecureRandom` или другую подменяемую функцию.
Отдельный generator обычно получается понятнее.

### `VerificationCodeHasher`

Вычисляет и проверяет HMAC. Он изолирует криптографию, server pepper и
constant-time comparison. Service не должен знать детали `Mac`, hex и encoding.

### `VerificationChallengeService`

Оркестратор бизнес-сценария:

- блокирует нужного User/challenge;
- проверяет cooldown;
- генерирует и сохраняет новый challenge;
- проверяет введённый code;
- увеличивает attempts;
- помечает challenge consumed;
- для `EMAIL_VERIFICATION` вызывает `user.markEmailVerified(now)`;
- публикует событие для письма.

### `VerificationIssuedEvent`

Короткоживущий объект в памяти с destination, исходным code, purpose и сроком.
Он нужен, потому что в БД исходного кода уже нет, а письмо всё-таки должно его
получить. Event нельзя логировать или сохранять как JSON.

### `VerificationMailListener`

Получает event только после commit и вызывает существующий `MailService`. Если
SMTP упал, listener безопасно фиксирует failure без code. Регистрация при этом
уже сохранена, а пользователь сможет сделать resend.

Отдельные `IssuedVerificationChallenge` result records добавляй только когда
появятся реальные данные для controller, например `expiresAt` и
`resendAvailableAt`. Заранее пустые wrapper-классы не нужны.

## 11.4. Миграция V13 для одной строки на purpose

Не изменяй `V1`–`V12`. Создай:

```text
src/main/resources/db/migration/
V13__create_account_verification_challenges.sql
```

Ориентир схемы:

```sql
CREATE TABLE verification_challenges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    purpose VARCHAR(40) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    destination VARCHAR(320) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_verification_challenge_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_verification_challenge_user_purpose
        UNIQUE (user_id, purpose),
    CONSTRAINT verification_challenge_purpose_ck
        CHECK (purpose IN (
            'EMAIL_VERIFICATION',
            'PASSWORD_RESET',
            'SENSITIVE_ACTION'
        )),
    CONSTRAINT verification_challenge_channel_ck
        CHECK (channel IN ('EMAIL', 'SMS')),
    CONSTRAINT verification_challenge_attempts_ck
        CHECK (failed_attempts >= 0),
    CONSTRAINT verification_challenge_expiry_ck
        CHECK (expires_at > issued_at)
);
```

### Зачем каждое поле

| Поле | Зачем оно нужно |
|---|---|
| `id` | Технический стабильный primary key для JPA. Клиенту его возвращать не нужно. |
| `user_id` | Владелец проверки. Код одного User не должен подтвердить другого. FK также удалит challenges вместе с удаляемым тестовым/анонимизируемым аккаунтом. |
| `purpose` | Какое действие разрешает код. Вместе с `user_id` определяет единственную текущую строку. |
| `channel` | Куда доставили код: сейчас EMAIL, позже возможен SMS. Не является адресом доставки. |
| `destination` | Снимок конкретного email/телефона, куда ушёл код. Для email verification нельзя подтвердить адрес, отличный от того, для которого выдали challenge. |
| `code_hash` | HMAC исходного кода. Сам код в MySQL не хранится. `VARCHAR(64)` подходит для SHA-256 в lowercase hex. |
| `issued_at` | Когда был выдан именно текущий код. Нужен для 60-секундного resend cooldown. При resend обновляется. |
| `expires_at` | Абсолютный момент, после которого код не принимается. Обычно `issuedAt + 10 минут`. |
| `consumed_at` | `NULL`, пока код не использован; время успешного использования после confirm. Делает код одноразовым. При resend снова становится `NULL`. |
| `failed_attempts` | Количество неправильных вводов текущего кода. После лимита, например 5, challenge блокируется. При resend сбрасывается в 0. |
| `version` | Поле `@Version` для обнаружения потерянных конкурентных UPDATE. Не заменяет осознанный locking flow, но даёт дополнительную защиту. |

Почему здесь `issued_at`, а не `created_at`: строка живёт долго и обновляется при
каждом resend. `created_at` описывал бы возраст строки, но не возраст текущего
кода. Если нужна дата первого создания строки, добавь оба поля, но для логики
19B достаточно `issued_at`.

Отдельный index на `(user_id, purpose)` не нужен: unique constraint уже создаёт
индекс. Индекс по destination добавляй только под фактический query.

## 11.5. Создать entity и её операции

Entity не должна иметь публичные setters. Нужны factory и предметные методы:

```text
VerificationChallenge.issue(...)
rotate(destination, codeHash, issuedAt, expiresAt)
isExpired(now)
isConsumed()
hasAttemptsRemaining(maxAttempts)
registerFailedAttempt()
consume(now)
```

`rotate(...)` реализует resend на той же строке:

```text
destination = новый нормализованный destination
codeHash = hash нового code
issuedAt = now
expiresAt = now + TTL
consumedAt = null
failedAttempts = 0
```

Инварианты:

- user, purpose, channel, destination и codeHash обязательны;
- `expiresAt` строго позже `issuedAt`;
- `failedAttempts` не может стать отрицательным;
- `consume(now)` нельзя успешно выполнить второй раз;
- expired/exhausted challenge нельзя consume.

## 11.6. Генерация и безопасное хранение кода

Да, исходный code просто генерируется случайно:

```text
number = SecureRandom.nextInt(1_000_000)
code = String.format("%06d", number)
```

`000042` — валидный код, поэтому в Java, JSON и React он всегда `String`, не
integer.

Но в строку challenge записывается не `code`, а HMAC:

```text
storedHash = HMAC-SHA-256(
    serverPepper,
    userId + ":" + purpose + ":" + destination + ":" + code
)
```

Почему недостаточно сохранить code как есть: при утечке БД любой активный код
сразу готов к использованию. Почему недостаточно обычного SHA-256: у кода всего
миллион вариантов, их легко перебрать. HMAC требует ещё и секретный server pepper.

Config:

```properties
app.verification.code-pepper=${VERIFICATION_CODE_PEPPER}
```

Pepper — длинный случайный production secret, который не коммитится. Для tests
используется отдельное фиксированное тестовое значение. Сравнивай hash через
constant-time operation, например `MessageDigest.isEqual`.

## 11.7. Issue и resend одной текущей записи

Алгоритм:

```text
issueOrRotate(userId, EMAIL_VERIFICATION, EMAIL, user.email):
    1. получить и заблокировать User
    2. убедиться, что User ACTIVE и email ещё не verified
    3. найти challenge по (userId, purpose)
    4. если строка есть, проверить issuedAt + 60 секунд
    5. проверить дополнительный send limit
    6. создать raw code через SecureRandom
    7. вычислить codeHash через hasher
    8. если строки нет — создать её
    9. если строка есть — вызвать rotate(...)
   10. сохранить/flush
   11. опубликовать VerificationIssuedEvent с raw code
   12. вернуть только expiresAt и resendAvailableAt
```

Почему сначала блокируется User: при самом первом запросе challenge ещё нет, то
есть строку challenge заблокировать невозможно. Lock существующего User
сериализует две параллельные первые выдачи, а unique `(user_id, purpose)` остаётся
последней защитой БД.

Constants:

```text
CODE_TTL = 10 минут
RESEND_COOLDOWN = 60 секунд
MAX_FAILED_ATTEMPTS = 3
```

В текущей реализации значение `MAX_FAILED_ATTEMPTS` выбрано равным `3`.
Также каждый resend задаёт новый срок как `now + CODE_TTL`: старый `expiresAt`
не продлевается ещё на десять минут.

При модели одной строки старый code инвалидируется автоматически: `rotate`
заменяет `codeHash`. Отдельный `invalidatedAt` не нужен.

## 11.8. Отправить письмо только после commit

Service публикует event внутри транзакции, а listener использует:

```text
@TransactionalEventListener(phase = AFTER_COMMIT)
```

Порядок:

```text
сохранить User/identity/challenge
-> commit
-> listener вызывает MailService с raw code и Duration до expiresAt
```

Если SMTP вызвать до commit, письмо может прийти, а transaction затем откатится.
Пользователь получит код, которого нет в БД.

Listener ловит `MailException`, логирует только безопасные identifiers и не
логирует code/event целиком. Уже сохранённая регистрация не откатывается; resend
остаётся доступен.

Между commit и SMTP процесс теоретически может упасть. Для текущего проекта это
приемлемо благодаря resend. Полная гарантия доставки потребовала бы outbox.

## 11.9. Confirm

```text
confirmEmail(email, code):
    1. нормализовать email
    2. найти User
    3. найти challenge по (userId, EMAIL_VERIFICATION) с PESSIMISTIC_WRITE
    4. проверить consumedAt == null
    5. проверить now < expiresAt
    6. проверить failedAttempts < 3
    7. вычислить HMAC введённого code в том же context
    8. если hash не совпал — failedAttempts + 1 и commit
    9. если совпал — user.markEmailVerified(now)
   10. challenge.consume(now)
   11. commit User и challenge вместе
```

Lock нужен, чтобы два параллельных confirm не использовали один код дважды и не
потеряли увеличение attempts.

Ошибки наружу:

```text
invalid_or_expired_code
verification_attempts_exhausted
verification_resend_too_soon
```

Не раскрывай через resend, существует ли email. Для неизвестного, уже verified
или OAuth-only адреса внешний ответ должен выглядеть одинаково.

## 11.10. Rate limiting при модели одной строки

Сама строка надёжно обеспечивает:

- 60-секундный cooldown через `issuedAt`;
- 10-минутный TTL через `expiresAt`;
- максимум 3 неверных попытки;
- одноразовость через `consumedAt`.

Но она не хранит историю отправок, поэтому не может сама посчитать «не больше N
писем за час». Для этого есть три варианта:

1. Redis counter по нормализованному destination/IP — самый простой сейчас.
2. Добавить в challenge `send_window_started_at` и `sends_in_window`.
3. Хранить отдельную историю отправок — нужно только для серьёзного audit/abuse
   анализа.

Для CollabDesk сейчас разумно: correctness хранить в MySQL, а часовой/IP limit —
в Redis. Если Redis недоступен, поведение нужно выбрать явно. Не доверяй любому
`X-Forwarded-For`, пока не настроен доверенный reverse proxy.

## 11.11. Тесты этой модели

Unit tests:

- generator возвращает ровно 6 цифр и сохраняет leading zero;
- HMAC зависит от user, purpose, destination и code;
- новая entity active и имеет 0 attempts;
- rotate меняет hash/срок и сбрасывает consumed/attempts;
- wrong code увеличивает attempts;
- после трёх ошибок код не работает;
- expired и consumed code не работают;
- consumed code нельзя использовать повторно;
- resend раньше cooldown отклоняется;
- resend после cooldown обновляет ту же строку, а не создаёт вторую;
- после resend старый code не работает.

MySQL tests:

- Flyway V13 применяется на чистой схеме;
- unique запрещает две строки одного `(user_id, purpose)`;
- тому же User разрешены разные purposes;
- разным Users разрешён одинаковый purpose;
- CHECK constraints работают;
- cascade удаляет challenges вместе с User;
- locking не позволяет двум confirmations использовать код дважды.

Event tests:

- `MailService` не вызывается при rollback;
- вызывается после commit;
- SMTP failure не удаляет User/challenge;
- resend остаётся доступен после SMTP failure.

---

# Шаг 12. Подключить verification к локальной регистрации

## 12.1. Исправить создание LOCAL User

Сейчас `pendingLocalOnboarding(...)` использует общий `pendingOnboarding(...)`,
который начинает с конструктора подтверждённого User. После 19B factory должен
одновременно давать два состояния:

```text
emailVerifiedAt = NULL
onboardingCompletedAt = NULL
```

Исправь factory внутри `User`, а не обнуляй приватные поля из service.

После этого unit test entity должен отдельно проверить:

- обычный legacy/test `new User(...)` остаётся verified и onboarded, если это
  всё ещё нужно существующим tests;
- `pendingExternal(...)` — verified, но not onboarded;
- `pendingLocal(...)` — not verified;
- `pendingLocalOnboarding(...)` — not verified и not onboarded.

Так явно видна разница источников доверия к email.

## 12.2. Переделать RegistrationService

Новый алгоритм:

```text
register(email, rawPassword):
    1. проверить обязательные параметры
    2. нормализовать email через User.normalizeEmail(...)
    3. проверить duplicate email
    4. BCrypt-encode password
    5. создать pending local onboarding User
    6. сохранить User
    7. создать и сохранить LOCAL AuthIdentity
    8. выдать EMAIL_VERIFICATION challenge
    9. вернуть RegistrationResult с verificationRequired = true
```

Все записи выполняются в одной `@Transactional` операции. Если сохранение
identity или challenge падает, User тоже откатывается.

Не отправляй письмо отдельным вызовом после `registrationService.register(...)`
в controller. Событие выдачи challenge уже содержит намерение доставки и будет
обработано после commit.

### Ответ регистрации

Расширь `RegistrationResult` и `RegisterResponse` полезным состоянием, например:

```json
{
  "email": "member@example.com",
  "verificationRequired": true,
  "expiresAt": "...",
  "resendAvailableAt": "..."
}
```

Не возвращай:

- raw verification code;
- code hash;
- challenge id, если API не нуждается в нём;
- password/password hash;
- provider subject.

Реши, нужны ли `id`, `displayName` и `status` до authentication. Чем меньше
данных публично возвращает регистрация, тем проще контракт.

## 12.3. Запретить LOCAL login до verification

После загрузки `AuthIdentity` в `LocalUserDetailsService` проверь:

```text
user.status == ACTIVE
user.isEmailVerified() == true
```

Для неподтверждённого email выбрасывай наружу ту же authentication failure, что
для неверного password или неизвестного email. Login endpoint не должен помогать
угадывать зарегистрированные адреса.

Альтернатива — учитывать `emailVerified` внутри `UserDetails.isEnabled()`. Но
тогда внимательно проверь смысл enabled для disabled и external principals.
Для текущего проекта локальная проверка в `LocalUserDetailsService` проще и
меньше влияет на Google/GitHub.

Важно: backend-запрет обязателен. Скрыть кнопку во frontend недостаточно — HTTP
request можно отправить без React.

## 12.4. Добавить API

Рекомендуемые endpoints:

```text
POST /api/v1/auth/email-verification/confirm
POST /api/v1/auth/email-verification/resend
```

DTO confirm:

```json
{
  "email": "member@example.com",
  "code": "004271"
}
```

DTO resend:

```json
{
  "email": "member@example.com"
}
```

Validation:

- email: `@NotBlank`, `@Email`, max 320;
- code: `@NotBlank` и regexp `^[0-9]{6}$`;
- DTO нельзя логировать целиком.

Добавь оба POST endpoint в `SecurityConfig` как `permitAll()`. `permitAll` не
отключает CSRF: frontend всё равно должен получать CSRF token и отправлять его.

Возможные ответы:

```text
register: 201 + verification state
confirm: 200 + CurrentUserResponse при auto-login
         или 204, если после confirm пользователь отдельно логинится
resend: 204 для принятого запроса, включая безопасный no-op
rate limit: 429 + Retry-After
invalid code: 400/422 с безопасным problem code
```

Выбери один контракт и закрепи integration tests и OpenAPI annotations.

## 12.5. Решить вопрос автоматической сессии

Для CollabDesk выбран вариант B: успешное подтверждение сразу создаёт
аутентифицированную HTTP session и переводит пользователя к onboarding.
Вариант A ниже оставлен только для понимания возможной альтернативы.

### Вариант A — проще

После успешного confirm вернуть `204`, открыть login и попросить ввести password.
Это легче реализовать и проверить, но UX хуже.

### Вариант B — рекомендуемый для исходного плана

Код подтверждения становится достаточным доказательством для создания первой
session. После `confirmEmail(...)`:

1. загрузить свежего User и LOCAL identity;
2. построить `AuthenticatedUserPrincipal` без передачи hash клиенту;
3. создать authenticated token на backend;
4. применить session fixation protection;
5. сохранить новый `SecurityContext` через Spring Security repository;
6. вернуть безопасный `CurrentUserResponse`;
7. frontend передаст пользователя в `onAuthenticated`, и откроется onboarding.

Не делай это через frontend-флаг `verified=true` и не передавай password повторно
из registration form. После очистки формы password должен исчезнуть из state.

Реализуй вариант B сразу: отдельный `EmailVerificationSessionService` создаёт
authenticated token, применяет session-fixation protection и сохраняет новый
`SecurityContext` через `SecurityContextRepository`.

## 12.6. Переделать frontend API

В `frontend/src/api/authApi.js` добавь функции:

```text
confirmEmailVerification({ email, code })
resendEmailVerification({ email })
```

Обе используют `withCsrf`, JSON и общий `createApiError`.

После `registerUser(...)` больше не вызывай `loginUser(...)`. Вместо этого:

```text
registration success
    -> очистить оба password поля
    -> сохранить email для текущего verification flow
    -> показать EmailVerificationScreen
```

Email допустимо держать в React state или `sessionStorage` для refresh. Не клади
туда password или verification code. `localStorage` для чувствительного flow не
нужен.

## 12.7. Создать EmailVerificationScreen

Экран должен содержать:

- заголовок «Проверьте почту»;
- маскированный email для отображения;
- input с `inputMode="numeric"`, `autoComplete="one-time-code"` и `maxLength=6`;
- фильтрацию нецифровых символов;
- кнопку подтверждения;
- countdown до resend;
- кнопку resend после окончания countdown;
- возможность вернуться к регистрации/login;
- состояния loading, invalid/expired, attempts exhausted, resend limited;
- `role="alert"` для ошибки и доступный label для поля.

Маскирование — только UI, не средство безопасности. Пример:

```text
member@example.com -> m***r@example.com
```

Countdown во frontend улучшает UX, но не защищает backend. После refresh или
ручного HTTP request серверный cooldown всё равно обязан сработать.

После resend:

- очистить старый введённый code;
- обновить `expiresAt` и `resendAvailableAt` из ответа либо начать подтверждённый
  сервером countdown;
- показать нейтральное сообщение «Если аккаунт ожидает подтверждения, письмо
  отправлено»;
- помнить, что старый код уже невалиден.

После успешного confirm:

- очистить code/email verification state;
- при варианте A открыть login с заполненным email;
- при варианте B вызвать `onAuthenticated(responseUser)` и открыть onboarding.

## 12.8. Обновить старые tests и test support

После изменения регистрации существующие tests, которые используют регистрацию
как shortcut для готового аккаунта, начнут падать. Не ослабляй production-правило
ради tests.

Обнови `LocalAccountTestSupport.registerCompletedAccount(...)`:

```text
register
-> получить User из repository
-> markEmailVerified(testInstant)
-> completeOnboarding(...)
-> saveAndFlush
```

Либо пусть integration helper проходит настоящий confirm flow с предсказуемым
test code. Первый вариант быстрее для unrelated workspace tests, второй нужен
для auth integration tests.

Обязательные изменения ожиданий:

- `RegistrationTest`: после регистрации `isEmailVerified()` теперь false;
- `AuthenticationIntegrationTest`: обычный успешный login сначала подтверждает
  тестового пользователя;
- новый test: правильный password до verification возвращает `401`;
- `LocalUserDetailsServiceTest`: добавить отдельного pending local User и
  проверить отказ;
- rollback test теперь проверяет также отсутствие challenge.

## 12.9. Полная матрица integration tests

Backend:

1. Регистрация создаёт ровно User, LOCAL identity и один challenge.
2. User активен, но `emailVerifiedAt = NULL` и onboarding не завершён.
3. Password хранится только как BCrypt hash.
4. Code хранится только как HMAC.
5. Письмо отправляется после commit.
6. Login до verification возвращает `401` даже с верным password.
7. Правильный code заполняет `emailVerifiedAt` и consume challenge.
8. Правильный code нельзя использовать второй раз.
9. Неверный code увеличивает attempts ровно на один.
10. После трёх ошибок challenge больше не принимается.
11. Expired code отклоняется.
12. Resend до cooldown получает `429` либо выбранную domain error.
13. Resend после cooldown создаёт новый code.
14. После resend старый code не работает, новый работает.
15. Resend неизвестного email не раскрывает отсутствие аккаунта.
16. SMTP failure не создаёт второй User и не откатывает первый.
17. OAuth User с verified email не получает локальный challenge.
18. Confirm/resend без CSRF получают `403`.
19. Ни response, ни ProblemDetail не содержат code/hash/password.
20. Два параллельных confirm request не используют challenge дважды.

Frontend:

1. Разные password не вызывают register API.
2. Успешная регистрация открывает verification, а не вызывает login.
3. Password fields очищаются после создания аккаунта.
4. Поле code принимает только 6 цифр и сохраняет leading zero.
5. Неверный code показывает ошибку и не открывает onboarding.
6. Countdown блокирует кнопку resend только на уровне UX.
7. Успешный resend очищает старый code.
8. Успешный confirm открывает login или onboarding согласно выбранному контракту.
9. Refresh verification screen не восстанавливает password/code.

---

# 3.1. Как писать оставшиеся тесты заранее и не сломать компиляцию

Test-first не означает, что нужно прямо сейчас создать двадцать Java-файлов со
ссылками на несуществующие классы. Такой suite не станет красным на assertion —
он вообще не скомпилируется. Работай короткими TDD-циклами:

```text
1. создать минимальный production type/сигнатуру
2. написать один компилирующийся красный test
3. увидеть ожидаемую причину failure
4. реализовать минимум для green
5. refactor
6. перейти к следующему правилу
```

Ниже готовая очередь тестов. Имена методов специально формулируют бизнес-правило,
поэтому их можно переносить в test class почти без изменений.

## TDD-0. Почтовый слой — уже добавлено

Файл:

```text
src/test/java/collabdesk/auth/mail/MailServiceTest.java
```

Текущие tests:

```text
sendsEmailVerificationMessageWithConfiguredEnvelopeAndContent
propagatesSmtpFailureToTheFutureAfterCommitListener
```

Тест уже соответствует текущему constructor и параметру `Duration`. После
исправления текста письма усили его assertions: проверяй `verification code`,
`004271` и `10 minutes`, но не сравнивай body одной большой строкой.

## TDD-1. User factory — первый тест шага 12

Production type уже существует, поэтому следующий test можно добавить в
`UserTest` непосредственно перед исправлением factory:

```text
pendingLocalOnboardingIsUnverifiedAndRequiresOnboarding
```

Arrange/Act:

```text
User user = User.pendingLocalOnboarding("member@example.com", "member")
```

Assertions:

```text
status == ACTIVE
email == member@example.com
emailVerified == false
emailVerifiedAt == null
onboardingCompleted == false
onboardingCompletedAt == null
```

Сначала test должен упасть именно на `emailVerified == false`. После этого
исправь factory и запусти весь `UserTest`, чтобы не сломать external factory.

Рядом добавь regression test:

```text
pendingExternalHasVerifiedEmailButStillRequiresOnboarding
```

Он защищает различие LOCAL и OAuth.

## TDD-2. VerificationCodeGenerator

Сначала создай только class и метод `generate()`, затем test class:

```text
src/test/java/collabdesk/auth/verification/
    VerificationCodeGeneratorTest.java
```

Tests:

```text
generateReturnsExactlySixDecimalCharacters
generatePreservesLeadingZeroes
```

Для leading zero нельзя надеяться, что настоящий `SecureRandom` случайно выдаст
маленькое число. Передавай источник случайности через constructor либо выдели
минимальную подменяемую границу. В test принудительно верни `42` и ожидай
`"000042"`.

Не пиши statistical test на «два кода должны отличаться»: случайность имеет право
редко вернуть одинаковое значение, и test станет flaky.

## TDD-3. VerificationCodeHasher

Создай сигнатуры hasher, но сначала не реализуй HMAC. Test class:

```text
VerificationCodeHasherTest
```

Tests:

```text
sameContextAndCodeProduceSameHash
differentCodeProducesDifferentHash
differentUserProducesDifferentHash
differentPurposeProducesDifferentHash
rawCodeIsNotPresentInStoredHash
matchesAcceptsCorrectCode
matchesRejectsWrongCode
```

В test используй фиксированный test pepper. Не проверяй конкретную hex-строку из
внешнего online generator: важен контракт, а не случайно выбранный способ
сериализации context. Один known-answer test можно добавить после стабилизации
формата, чтобы будущий refactor не инвалидировал все активные challenges.

## TDD-4. VerificationChallenge entity

После создания entity начни с чистых unit tests без Spring и MySQL:

```text
VerificationChallengeTest
```

Порядок tests:

```text
newChallengeStartsActiveWithZeroFailedAttempts
expiredChallengeIsNotActive
consumeMakesChallengeInactive
consumeCannotBeAppliedTwice
wrongAttemptIncrementsCounter
thirdWrongAttemptExhaustsChallenge
attemptAfterExhaustionIsRejected
rotateReactivatesSameChallengeWithNewHashAndExpiry
expiresAtMustBeAfterIssuedAt
```

Передавай `Instant now` параметром в методы. Не вызывай `Instant.now()` внутри
каждого assertion: tests на границе времени будут нестабильными.

## TDD-5. Flyway V13 и repository

Когда entity contract понятен, создай migration и repository. Используй
Testcontainers MySQL, потому что H2 не проверит поведение реального MySQL.

Файлы:

```text
VerificationChallengeMigrationTest.java
VerificationChallengeRepositoryTest.java
```

Migration tests:

```text
createsVerificationChallengesTableWithExpectedColumns
rejectsUnknownPurpose
rejectsUnknownChannel
rejectsNegativeFailedAttempts
rejectsSecondRowForSameUserAndPurpose
allowsDifferentPurposesForSameUser
deletingUserDeletesOwnedChallenges
```

Repository tests:

```text
findsCurrentChallengeForUserAndPurpose
doesNotReturnChallengeBelongingToAnotherUser
lockedLookupReturnsManagedChallenge
```

Не проверяй только `repository.save()` — это почти полностью test Spring Data.
Проверяй custom queries и constraints, которые несут правила 19B.

## TDD-6. VerificationChallengeService.issue

Для unit tests подставь mocks/fakes:

```text
VerificationChallengeRepository
VerificationCodeGenerator
VerificationCodeHasher
ApplicationEventPublisher
Clock
```

Test class и порядок:

```text
VerificationChallengeServiceTest

issueCreatesChallengeExpiringAfterConfiguredTtl
issueStoresHashAndNeverStoresRawCode
issuePublishesMailEventWithRawCode
resendRotatesExistingChallengeInsteadOfCreatingSecondRow
resendResetsAttemptsAndConsumedAt
issueRejectsVerifiedUser
issueRejectsDisabledUser
issueRejectsResendDuringCooldown
issueRejectsDestinationHourlyLimit
```

В `issueStoresHashAndNeverStoresRawCode` захвати entity через `ArgumentCaptor` и
проверь, что `codeHash` равен значению mock hasher, но не raw code. Не пытайся
достать private field reflection, если entity уже предоставляет безопасное
package-private поведение для test в том же package.

## TDD-7. VerificationChallengeService.confirm

Продолжи в том же test class:

```text
confirmMarksUserVerifiedAndConsumesChallenge
confirmRejectsWrongCodeAndIncrementsAttempts
confirmRejectsExpiredChallenge
confirmRejectsConsumedChallenge
confirmRejectsExhaustedChallenge
confirmDoesNotVerifyUserWhenHashDoesNotMatch
confirmNormalizesEmailBeforeLookup
```

После unit tests добавь MySQL integration test:

```text
concurrentConfirmConsumesChallengeOnlyOnce
```

Это отдельный integration test с двумя transaction/thread, а не Mockito test.
Он доказывает, что repository lock действительно работает.

## TDD-8. AFTER_COMMIT listener

Test class:

```text
EmailVerificationMailListenerIntegrationTest
```

Tests:

```text
sendsMailAfterTransactionCommits
doesNotSendMailWhenTransactionRollsBack
smtpFailureDoesNotRollBackPersistedRegistration
smtpFailureDoesNotExposeVerificationCodeInLogs
```

В первых двух tests одного вызова transactional service недостаточно, если сам
test обёрнут в transaction. Явно заверши test transaction либо вызови отдельный
transactional bean и проверяй mock после возврата/commit.

Последний test логов имеет смысл только когда в listener появится logging. Не
проверяй отсутствие code во всех логах приложения глобальным поиском — захвати
logger конкретного listener.

## TDD-9. RegistrationService

Расширь существующие `RegistrationTest` и `RegistrationRollbackTest`:

```text
registrationCreatesUnverifiedLocalUser
registrationCreatesExactlyOneEmailVerificationChallenge
registrationReturnsVerificationRequired
challengeFailureRollsBackUserAndLocalIdentity
mailIsNotSentBeforeRegistrationCommit
smtpFailureDoesNotCreateDuplicateUser
```

Старый test
`correctRegistrationCreatesOneUserAndOneLocalIdentity` не удаляй: измени
assertion `isEmailVerified()` с `true` на `false` и добавь проверку challenge.

Разделяй persistence failure и SMTP failure:

- failure сохранения challenge происходит до commit и откатывает регистрацию;
- SMTP failure происходит после commit и не откатывает регистрацию.

## TDD-10. Local login

В `LocalUserDetailsServiceTest`:

```text
unverifiedLocalUserCannotBeLoadedForPasswordLogin
verifiedLocalUserCanBeLoadedForPasswordLogin
```

В `AuthenticationIntegrationTest`:

```text
correctPasswordBeforeEmailVerificationReturnsUnauthorized
correctPasswordAfterEmailVerificationCreatesSession
unknownAndUnverifiedEmailsHaveSamePublicLoginResponse
```

Последний test сравнивает HTTP status и безопасное тело, а не время выполнения:
надёжно доказать отсутствие timing side-channel обычным integration test нельзя.

## TDD-11. Confirm/resend HTTP API

Test class:

```text
EmailVerificationIntegrationTest
```

Tests:

```text
confirmRequiresCsrf
confirmRejectsMalformedEmail
confirmRejectsCodeThatIsNotExactlySixDigits
confirmAcceptsCodeWithLeadingZero
confirmReturnsSafeProblemForInvalidCode
confirmDoesNotReturnChallengeOrCodeHash
resendRequiresCsrf
resendReturnsSamePublicResponseForUnknownEmail
resendReturnsSamePublicResponseForVerifiedEmail
resendTooSoonReturnsTooManyRequestsAndRetryAfter
resendInvalidatesPreviousCode
successfulConfirmCannotBeRepeated
```

Для реального code в integration test подставь test generator с известным
значением. Никогда не делай production endpoint, который возвращает code «только
для tests».

## TDD-12. Frontend

В проекте пока нет настроенного frontend test runner. Не добавляй Vitest и
Testing Library скрыто внутри mail-коммита. Сначала отдельным tooling-коммитом
подключи runner, затем tests:

```text
authApi.test.js
    confirm sends JSON with CSRF
    resend sends JSON with CSRF
    API problem is converted to field/general error

EmailVerificationScreen.test.jsx
    renders masked destination
    keeps leading zero in code
    removes non-digits
    submit is disabled until six digits
    resend is disabled during countdown
    successful resend clears code
    successful confirm continues authentication flow
```

До появления test runner обязательный минимум — `npm.cmd run lint`,
`npm.cmd run build` и ручной сценарий из раздела 5.

## Правило включения будущих tests

Не помечай будущие tests `@Disabled` просто для зелёной сборки: disabled test
легко забыть навсегда. Добавляй очередной test тогда, когда минимальный production
type уже компилируется, и оставляй его красным только на время короткого TDD-цикла.
Перед коммитом test suite должен быть зелёным.

---

# 4. Рекомендуемый порядок маленьких коммитов

Не делай весь 19B одним коммитом. Удобный порядок:

```text
1. chore(mail): add Mailpit and SMTP configuration
2. feat(mail): add account mail abstraction
3. feat(auth): persist verification challenges
4. feat(auth): issue and validate email verification codes
5. feat(auth): require verified email for local login
6. feat(auth): add email verification endpoints
7. feat(auth-ui): add email verification screen
8. test(auth): cover local email verification flow
```

После каждого коммита tests должны оставаться зелёными. Временный публичный
endpoint или логирование code не должны попадать даже в промежуточный commit.

---

# 5. Ручная проверка от начала до конца

1. Запусти MySQL, Redis и Mailpit через compose.
2. Запусти backend и frontend.
3. Зарегистрируй новый локальный email.
4. Убедись, что UI открыл экран verification, а не workspace.
5. Попробуй войти правильным password до verification — должен быть `401`.
6. Открой `http://localhost:8025` и найди письмо.
7. Введи неправильный code и проверь понятную ошибку.
8. Введи правильный code.
9. Проверь переход на login/onboarding согласно выбранному контракту.
10. Попробуй тот же code второй раз — он не должен работать.
11. Создай новый аккаунт, нажми resend после cooldown.
12. Проверь, что код из первого письма больше не работает.
13. Проверь, что код из второго письма работает.
14. Останови Mailpit и зарегистрируй новый email.
15. Убедись, что User не дублируется, приложение не зависает, а после запуска
    Mailpit можно сделать resend.
16. Проверь backend logs и browser Network: там нет password, raw code, code hash
    и mail secret.

Полезные SQL-проверки выполняй только локально:

```sql
SELECT id, email, email_verified_at, onboarding_completed_at
FROM users
ORDER BY id DESC;

SELECT id, user_id, purpose, channel, destination,
       issued_at, expires_at, consumed_at, failed_attempts, version
FROM verification_challenges
ORDER BY id DESC;
```

Не выбирай `code_hash` без необходимости и не копируй его в screenshots/issues.

---

# 6. Типичные ошибки

## Письмо отправляется до commit

Результат: человек получает нерабочий code после rollback. Исправление —
AFTER_COMMIT listener или outbox.

## В БД лежит `123456`

Результат: утечка БД сразу раскрывает активные коды. Храни HMAC с server pepper.

## Использован SHA-256 без secret

Миллион вариантов перебирается очень быстро. Нужен HMAC, а не простой digest.

## Code преобразуется в number

`004271` превращается в `4271`. Храни и передавай code как строку.

## Старый code работает после resend

Новый challenge обязан атомарно инвалидировать прошлые active challenges того же
user/purpose.

## Проверяется только countdown React

Пользователь может вызвать API напрямую. Все лимиты применяет backend.

## Unverified User всё равно входит

`ACTIVE` и `emailVerified` — разные состояния. Для LOCAL login нужны оба.

## SMTP exception откатывает регистрацию

SMTP вызывается после commit. Ошибка доставки оставляет возможность resend.

## В test отключили проверку email

Это скрывает production bug. Tests должны явно подтверждать test user или
проходить verification flow.

## Resend сообщает, что email не существует

Это account enumeration. Ответ для неизвестного адреса должен быть нейтральным.

## Логи содержат request DTO или event

DTO confirm содержит code, registration DTO содержит password. Не логируй их
целиком и не используй автоматический `toString` в security-sensitive событиях.

---

# 7. Финальный Definition of Done для 19B

- [ ] Mailpit запускается из `docker-compose.yaml`.
- [ ] SMTP полностью конфигурируется через env и имеет timeout.
- [ ] Только `MailService` напрямую зависит от `JavaMailSender`.
- [ ] V13 создаёт challenges и проходит на чистом MySQL.
- [ ] Код генерируется через `SecureRandom` и всегда содержит 6 цифр.
- [ ] В MySQL хранится HMAC, исходный code нигде не сохраняется и не логируется.
- [ ] Code живёт ограниченное время и используется только один раз.
- [ ] Есть cooldown, send limit и максимум неверных попыток.
- [ ] Resend инвалидирует предыдущий code.
- [ ] LOCAL registration создаёт unverified, not-onboarded User.
- [ ] User + LOCAL identity + challenge создаются одной транзакцией.
- [ ] Письмо отправляется только после commit.
- [ ] SMTP failure не создаёт duplicate User и допускает resend.
- [ ] LOCAL login до подтверждения запрещён на backend.
- [ ] Confirm и resend требуют CSRF и имеют безопасные ответы.
- [ ] После регистрации frontend открывает verification screen.
- [ ] Password очищается и не хранится для последующего confirm.
- [ ] После подтверждения пользователь попадает на login или сразу в onboarding.
- [ ] OAuth login не требует локального verification code.
- [ ] Backend tests, frontend lint и frontend build проходят.

Финальная проверка:

```powershell
.\mvnw.cmd test
cd frontend
npm.cmd run lint
npm.cmd run build
```

После выполнения всех пунктов можно поставить галочки шагам 10, 11 и 12 в
основном файле этапа 19.
