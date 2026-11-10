# Deployment view — native FreeBSD / Helios

## Результат

- **[SeasonPlan-deployment.vpp](SeasonPlan-deployment.vpp)** — настоящий редактируемый проект Visual Paradigm 18.1; диаграмма `Deployment view — native FreeBSD / Helios`.
- **[SVG](01-deployment.svg)** / **[PNG](01-deployment.png)** — экспорт из проекта, повторно открытого **другим процессом VP**. Экспорт содержит штатные evaluation-watermarks.
- [Проверка](validation.json), [снимок требований и геометрии](source-manifest.json), [инспекция повторно открытой модели](native-reopened.xml), [инструменты](tools).

Это **целевая архитектура развёртывания**, а не утверждение, что изображённые сервисы уже установлены. На диаграмме отдельно помечены проектные решения, известные характеристики Helios и неизвестные характеристики внешних систем. Приоритет: прямое требование пользователя → решения ST-00/границы Pollen Tree → остальные требования. Production-код и существующие `.vpp` не менялись.

## Архитектурное решение

```text
Браузер → HTTPS :443 → native nginx → HTTP loopback :8080 → Spring Boot JAR
                           │                                  │
                       frontend/dist/                    JDBC :5432
                                                              │
                                                    PostgreSQL 14 primary
                                                              │
                                           synchronous WAL + off-host backups
```

**Docker не является узлом, средой выполнения или артефактом диаграммы.** Контейнеризация из SRS DC-11 и Vision §§4.5/6/9.1 не применяется к Helios: артефакты устанавливает оператор. Файлы разработки с Docker в репозитории не являются схемой production-развёртывания; они не изменены.

- Сохраняется **модульный монолит**, а не микросервисы: один JVM-процесс с use-case services и изолированными интеграционными адаптерами. UI исполняется в браузере; на сервере лежит результат `vite build`, не dev-server. Node.js/npm нужны **только для сборки**.
- **nginx — предлагаемое инфраструктурное дополнение**, не существующая зависимость приложения. Обоснование выхода за основной application stack: TLS termination, обслуживание static SPA и единый origin, совместимый с `application-prod.yml` (приложение слушает loopback). Нужны разрешение администратора, сертификат и доступ к :443. Предоставляемый инфраструктурой эквивалентный reverse proxy может заменить nginx без изменения контрактов. Нельзя считать доступ к :443 или установку nginx уже разрешёнными.
- PostgreSQL 14 предлагается разместить нативно на Helios. Это **допущение о размещении**, не результат проверки сервера. Если доступна только общая/удалённая PostgreSQL, нужно заменить этот узел, JDBC URL, TLS и ACL, а не рисовать несуществующий локальный экземпляр.
- Отдельный recovery-хост — **предлагаемое условие выполнения требования о сохранности аудита**, не подтверждённый ресурс. Без него минимальная однохостовая установка возможна функционально, но не обеспечивает заявленный ниже RPO=0 при потере primary.

## Машины и ресурсы

| Машина | Характеристики / статус |
| --- | --- |
| Helios | `helios.cs.ifmo.ru`, FreeBSD 14.4-STABLE, amd64; Xeon E5-2643 0 @ 3.30 GHz, 16 logical CPU; 137381744640 bytes RAM (≈128 GiB); системный раздел 456 GB; OpenJDK 17.0.12. Источник: SRS §3.6.2, не live inventory. Тип носителя системного раздела не указан. |
| Ресурсы приложения | Vision §9.2: минимум 2 vCPU / 4 GB RAM / 20 GB SSD / 100 Mbit/s. Характеристики общего Helios **не являются квотой приложения**. Квоты, свободное место и нагрузку нужно проверить. |
| Клиент | Chrome / Firefox / Edge, сеть, экран ≥1366×768; CPU/RAM/ОС не нормированы. Специальное клиентское приложение не устанавливается. |
| Сборка / выпуск | Рабочая станция Windows 10 Pro; JDK 17/Maven, Node.js 24/npm.cmd по текущему проекту. CPU/RAM не заданы. Java 24 из локального PATH не является целевым runtime; сборку приложения выполнять JDK 17. |
| Recovery [ПРОЕКТ] | Независимый FreeBSD 14/amd64 хост: предварительно 2 vCPU / 4 GB / ≥100 GB SSD / ≥100 Mbit/s; отдельные диск и питание. Capacity зависит от WAL rate, retention и restore-тестов; 100 GB не гарантированная достаточная ёмкость. |
| Внешние API / почта | ОС/CPU/RAM, реальные URL, credentials и SLA не предоставлены; отвечает владелец провайдера. Спецификации машин не выдумываются. |

ST-00 задаёт проверяемую нагрузку: 50 одновременных активных пользователей, ≥100 command/events в минуту, p95 основные экраны ≤3 s / обычные операции ≤5 s, доступность ≥80%. Более высокие противоречивые числа Vision не используются как подтверждённый capacity. Репликация не означает автоматический failover и сама по себе не гарантирует availability.

## Интерфейсы взаимодействия

В UML связи — **CommunicationPath: ненаправленные Association между Node**. Стрелки последовательности сообщений здесь не используются. Таблица описывает инициатора соединения, а не навигацию UML Association. Подробности также сохранены в native relationship documentation.

| ID | Инициатор → endpoint | Протокол / данные / защита |
| --- | --- | --- |
| I1 | Браузер → proxy | TCP 443, HTTPS TLS 1.2+; HTML/JS/CSS и REST JSON UTF-8; серверная аутентификация/RBAC, session cookie Secure/HttpOnly, отдельный CSRF token. Внешние API keys браузеру недоступны. |
| I2 | Proxy → JVM | HTTP `127.0.0.1:8080`, REST JSON; **только внутри одного хоста**. Proxy заменяет Forwarded/X-Forwarded-* доверенными значениями; прямой внешний доступ к JVM закрыт. |
| I3 | JVM → primary | JDBC / PostgreSQL wire, `127.0.0.1:5432`; отдельная runtime-role, не owner/superuser. Для удалённой БД потребуется TLS `verify-full`. |
| I4 | JVM → Pollen Tree | TCP 443, HTTPS TLS 1.2/1.3, JSON UTF-8, `X-API-Key`, connect/read timeout 2s/3s. Резервирование и расход только через `PollenService → PollenTreeClient`; не через нашу БД. Контракт адаптера предложен и тестируется локально; live provider не проверен. |
| I5 | JVM → Natural Calendar | TCP 443, REST HTTPS TLS 1.2+, JSON UTF-8, API-key или mTLS. Требуемая интеграция; текущий allowing fake не подходит для выпуска. |
| I6 | JVM → metric provider | TCP 443, REST pull HTTPS TLS 1.2+, JSON UTF-8, API-key или mTLS; каждые 1–5 минут. WSS/TLS :443 разрешён SRS как **альтернатива**, но не выбран и не показан отдельным действующим каналом. |
| I7 | JVM/outbox → mail relay | Предложение: SMTP submission TCP 587, обязательный STARTTLS TLS 1.2+ и SMTP AUTH; запрет plaintext fallback. SMTP — специальный интерфейс уведомлений SRS §3.6.3/ST-00, не JSON/REST API. In-app уведомления доступны через REST I1/I2; отдельный realtime-сервис не добавлен. |
| I8 | Оператор → FreeBSD | Предположение: SSH/SFTP TCP 22, ключ оператора; ручная публикация JAR/dist/config и restart. SSH — административный канал, не внешний бизнес-API. |
| I9 | Standby → primary | PostgreSQL streaming replication TCP 5432, TLS `verify-full`, SCRAM-SHA-256, ограниченная replication-role + allowlist. Standby открывает соединение; WAL идёт primary → standby. `synchronous_commit=remote_apply`, именованный synchronous standby. |
| I10 | Backup receiver → primary | PostgreSQL replication protocol TCP 5432, TLS `verify-full`, SCRAM-SHA-256, отдельная backup-role. `pg_basebackup` не реже одного раза в 4 часа; `pg_receivewal` непрерывно получает WAL для PITR. |

Локальный management bind `127.0.0.1:8081` существует в конфигурации, но текущая security chain запрещает `/actuator/**`. Диаграмма **не обещает работающий health API**: доступ/credentials для локальных проверок надо отдельно реализовать. В Интернет management не публикуется.

### Совместимость маршрутов

Frontend использует `VITE_API_BASE_URL=/api`; backend использует root-relative mappings. Предлагаемое правило proxy должно удалять **ровно внешний префикс `/api/`**:

```text
/api/season-plans       → /season-plans
/api/auth/login         → /auth/login
/api/auth/csrf          → /auth/csrf
/api/admin/users        → /admin/users
/… (не /api/)           → frontend/dist/ + SPA fallback
```

Это целевое правило, **не поставляемая production-конфигурация nginx**. Авторизация admin остаётся в backend; proxy не должен превращать API-ошибку в `index.html`.

## Ручной выпуск и разделение полномочий

1. На рабочей станции JDK 17/Maven собирают executable Spring Boot JAR; `npm.cmd ci` / `npm.cmd run build` создают `frontend/dist/`. Проверяют тесты, версию и SHA-256. GitHub Actions допустим для проверки, но автоматический CD на Helios здесь не предполагается.
2. Оператор публикует release directory через SFTP, отдельно устанавливает secrets с ограниченными правами; настоящие credentials не входят в репозиторий или диаграмму.
3. **Проектная доработка:** оператор запускает на Helios одноразовый Liquibase migration bundle с отдельной owner/migration-role. DBA предварительно разрешает/устанавливает `btree_gist`. Источник миграций — changelog приложения; здесь не предлагается обходить migration history ручным выполнением отдельных SQL-файлов.
4. Privileged migration process завершается. Обычный JVM-процесс запускается с runtime credentials и `SPRING_LIQUIBASE_ENABLED=false` (проектный production override); migration credentials ему не передаются. Runtime-role имеет SELECT/INSERT для AuditLog, не владеет таблицами и не имеет UPDATE/DELETE/TRUNCATE/DDL прав на аудит. Имеющиеся row-level triggers уже запрещают UPDATE/DELETE; grants, TRUNCATE/DDL-hardening и release bundle ещё надо реализовать.
5. Native nginx обслуживает dist и проксирует API. `daemon(8)`/разрешённый supervisor и restart/rollback runbook — **проектная эксплуатационная настройка**, не готовый rc.d installer. Смена JAR не гарантирует возможность отката несовместимой миграции.

Репозиторий сейчас использует один datasource и штатный Liquibase startup. Разделение миграции/runtime выше намеренно является целевой доработкой; схема не выдаёт её за существующий deployment script.

## AuditLog, бэкапы и ограничения гарантии

Четырёхчасовой бэкап оставляет окно потери данных и **не выполняет RR-1 сам по себе**. Поэтому для целевой установки предложены:

- transactional audit append вместе с локальным business write и durable outbox;
- `fsync=on`, `full_page_writes=on`, именованный synchronous standby и `remote_apply`;
- независимый recovery-host; base backups ≤4 часа и непрерывный WAL; проверяемое восстановление PITR;
- ограничения прав, immutable audit history, approved report snapshots и контролируемый доступ к архивам.

**RPO=0 ограничен подтверждёнными локальными PostgreSQL transactions при отказе одного primary-хоста.** Если standby недоступен, commit может ждать: нельзя автоматически ослабить synchronous durability и сохранить ту же гарантию. Клиентский timeout не означает rollback; неоднозначные результаты требуют idempotency/reconciliation. Одновременная потеря обоих хостов, уничтожение резервов привилегированным DBA, ошибки оператора и полная защита от административной подмены не решаются одной репликой. Для более сильного толкования RR-1 нужны независимое immutable/WORM-хранилище и отдельная политика доверия/retention; эти ресурсы не выдуманы как имеющиеся.

Pollen Tree — отдельная система: её успешная операция и наш local audit **не образуют распределённую транзакцию**. Имеющийся retry/recovery контракт интеграции сохраняется; диаграмма не обещает невозможную локальную атомарность с удалённым провайдером.

## Что реализовано, а что ещё нет

| Уже подтверждается исходниками | Целевые доработки / инфраструктурные условия |
| --- | --- |
| Spring Boot 3.2.12 / Java release 17; PostgreSQL JDBC, Spring Session JDBC; React 18.3.1 / Vite build | Native proxy/TLS, installation/service scripts, cert lifecycle, согласование :443/квот |
| `prod`: loopback bind, Secure session cookie; authentication/CSRF/RBAC; management loopback | Production hardening ролей, one-shot migrations, backup/replication/restore и operational monitoring |
| Pollen Tree REST adapter с HTTPS/key/timeouts; запрет fake в prod | Live endpoints/provider contract; Natural Calendar, sensors и SMTP adapters |
| Ядро UC-1…UC-5, отдельные JDBC adapters и migration tables | Полная REST/UI/composition/persistence UC-4/UC-5, durable outbox/delivery, scheduler, source snapshots и PDF/HTML renderers |

В частности, существование таблиц `ritual_sessions`/`season_reports` ещё не означает совместимость с полной текущей domain-моделью. PostgreSQL SeasonPlan **не владеет pollen inventory/reservations**; legacy unused table не изображается как действующая интеграция.

## UML 2 и проверка

Использованы реальные `INode` со стандартными `device` / `executionEnvironment` stereotypes, `IArtifact`, `IDeploymentSpecification`, `IAssociation` и `IDeployment`, а не class-rectangles или импортированная картинка. Представление — типовой deployment topology, не instance diagram. Вложенность имеет **и модельного владельца, и parent shape**. Размещение артефакта показано разрешённой UML 2 вложенной нотацией; соответствующие native Deployment relations также сохранены в модели. В VP 18.1 Open API Deployment хранит **target/supplier → artifact/client**; это особенность API, не указание рисовать обратную UML deploy-arrow. Дополнительные deploy-стрелки в диаграмме не дублируют вложенность.

Проверены 24 automated regression tests, отдельное native save/reopen, модельные семьи/stereotypes/ownership, Deployment endpoints, Association endpoints, размеры, зарегистрированный текст notes, периметровое присоединение линий, ортогональность, отсутствие пересечений shapes/подписей и нормальный `SIDE_NONE` у relation-name captions. Превью визуально проверено. Полный desktop GUI drag-test, live deployment, load test, TLS handshake и restore/failover test **не выполнялись**. Контроль привязан к SHA-256 итогового `.vpp` в `validation.json`.

### Повторная генерация (PowerShell)

```powershell
python -B -m unittest discover -s .\docs\architecture\deployment\tools -p 'test_*.py'
# Только новая папка: генератор не перезаписывает вручную изменённый проект.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  .\docs\architecture\deployment\tools\Generate-Deployment.ps1 `
  -Output "$env:TEMP\seasonplan-deployment-new"
```

`ExecutionPolicy Bypass` действует только в явно запущенном дочернем PowerShell, не меняет политику машины. Генератор требует Visual Paradigm 18.1, Python 3 и `javac`; plugin компилируется `--release 11` для bundled VP Java 11, отдельно от Java 17 приложения. Input `.vpp` всегда копируется; existing projects защищены. Временный authoring plugin удаляется; для открытия готового проекта он не нужен.

## Источники

- [SRS](../../../arch_docs/SYSTEM_REQUIREMENTS_SPECIFICATION.md): RR-1/4/5, PR-1…8, DC-1…13, §§3.6.2–3.6.4.
- [Vision](../../../arch_docs/VISION.md): §§4.1, 5, 6, 9.2; [Use cases](../../../arch_docs/USE_CASE_SPECIFICATION.md): UC-1…UC-5.
- [ST-00](../ST-00-specification-resolution.md): canonical lifecycle, transactional audit/outbox, load profile, report immutability.
- [Pollen Tree boundary](../POLLEN_TREE_EXTERNAL_SERVICE.md), backend/frontend paths и SHA-256 в `source-manifest.json`.
- [OMG UML 2.5.1](https://www.omg.org/spec/UML/2.5.1), [VP deployment Open API](https://knowhow.visual-paradigm.com/openapi/deployment-diagram/), [VP deployment reference](https://www.visual-paradigm.com/VPGallery/diagrams/Deployment.html).
