# UC-5 — Завершение сезона: UML 2 Communication

Откройте **[SeasonPlan-UC5-communications.vpp](SeasonPlan-UC5-communications.vpp)**
в Visual Paradigm 18.1. Это нативный редактируемый проект: участники,
связи и нумерованные сообщения — настоящие UML-модели, не картинки.
Для просмотра и редактирования плагин не требуется.

## Три уровня представления

| Уровень / диаграмма | Содержание | Нативный экспорт |
| --- | --- | --- |
| Use-case: **1. Абстрактное описание** | Главный хранитель, Фея-планировщик, система и Древо; основной поток, уточнение, блокировки и архивирование | [PNG](01-use-case.png) · [SVG](01-use-case.svg) |
| Logical: **2. Взаимодействие пакетов** | Уровень существующего `packages/01-system`; начало от frontend, без акторов | [PNG](02-logical.png) · [SVG](02-logical.svg) |
| Implementation: **3. Формирование и уточнение отчёта** | `generate`, транзакция, DRAFT, версии и аудит | [PNG](03-generation.png) · [SVG](03-generation.svg) |
| Implementation: **3a. Снимок итоговых источников** | Все исходные порты, pollen-граница, blockers и KPI | [PNG](03a-sources.png) · [SVG](03a-sources.svg) |
| Implementation: **3b. Утверждение и COMPLETED** | Повторная проверка источников, approval, доменный переход, атомарные записи | [PNG](03b-approval.png) · [SVG](03b-approval.svg) |
| Implementation: **3c. Просмотр, экспорт, архив** | `view`, approved-only HTML/PDF, отдельная команда `archive` | [PNG](03c-publication.png) · [SVG](03c-publication.svg) |

**Все шесть схем — Communication diagrams UML 2**, современное название
диаграмм кооперации, не legacy Collaboration и не Sequence diagrams.
Представление реализации — совокупность 3/3a/3b/3c. Разбиение по операциям
сохраняет детали без одной перегруженной сети. У `CompletionService` есть
нативная ссылка на поддиаграмму 3a для раскрытия `snapshot(plan)`.

Читать нужно **по номерам сообщений**, не сверху вниз: вертикальная координата
не означает время. Вложенные номера обозначают вызовы внутри операции,
`[условие]` — guard, `*` — повтор. Вложенные `1.1.*` и `3.1.*` — работа
command-callback, пока `execute` ещё не вернул управление. Нумерация локальна
для каждой диаграммы. Тонкие связи соединяют участников; стрелки сообщений
задают направление вызова. Self-link возвращается к тому же объекту.
Вызовы синхронные; ответы реализации используют `ActionTypeReturn`.

Сигнатуры с типами, параметрами и видимостью хранятся в настоящих
`IOperation`/`IParameter`; сообщения связаны с ними через `IActionTypeCall`.
VP дополнительно показывает короткое имя связанной операции после подписи.
UUID, clock, сортировки, record-конструкторы, `Supplier.get`, DTO-mapping,
внутренности JDBC/HTTP свёрнуты и объяснены в заметках. Интерфейсные участники
означают объекты, предоставленные через указанный порт, а не создание экземпляра
Java-интерфейса.

## Соответствие диаграмме пакетов

Источник — [существующий проект](../packages/README.md), системный обзор
`01-system`; идентификаторы читаются непосредственно из `packages.xml`.

| Логический участник | Исходная архитектура |
| --- | --- |
| frontend | `frontend/src`, React-клиент |
| api | `com.seasonplan.lifecycle.api` |
| security | `com.seasonplan.security` |
| application | `com.seasonplan.lifecycle.application.*`, агрегировано как в системном обзоре |
| domain | `com.seasonplan.lifecycle.domain` |
| persistence | `com.seasonplan.lifecycle.persistence` |
| pollentree | `com.seasonplan.lifecycle.adapter.pollentree` |
| Pollen Tree | Внешний runtime-участник, не Java-пакет |

Классификаторы `*Subsystem` — представители соответствующих подсистем
в этих пакетах, не новые Java-классы или микросервисы. UML Package не используется
как тип экземпляра. `application` включает completion/pollen/shared/port;
отдельные выдуманные пакеты для сервисов не вводятся. `service`, `fake`, `config`
не показаны, поскольку в проектируемом UC-5 не участвуют. В частности,
совместимый `SeasonPlanService` пока **не** делегирует UC-5. PostgreSQL свёрнут
в ответственность persistence, а не объявлен Java-пакетом.

## Требования, правила и граница реализации

Основания: [UC-5](../../../arch_docs/USE_CASE_SPECIFICATION.md),
[SRS](../../../arch_docs/SYSTEM_REQUIREMENTS_SPECIFICATION.md),
[Vision](../../../arch_docs/VISION.md),
[ST-00](../ST-00-specification-resolution.md),
[единственная pollen-граница](../POLLEN_TREE_EXTERNAL_SERVICE.md),
`CompletionService`, `CompletionPorts`, `SeasonReport`, `SeasonPlan` и
`CompletionUseCaseTest`.

- Основная граница: **ACTIVE → COMPLETED**; **COMPLETED → ARCHIVED** —
  отдельное расширение после утверждения. Старое упоминание CORRECTION в UC-5
  разрешается ST-00: это рабочее условие, не девятый lifecycle-статус.
- Главный хранитель подтверждает результаты. Фея-планировщик может сформировать
  и уточнить отчёт **своего** плана, но не утвердить его или завершить сезон.
- Неполные данные дают DRAFT с blockers. Успешный ритуал, завершённые обязательные
  задачи/коррекции, покрытие метрик и отсутствие открытых CRITICAL повторно
  проверяются при approval; старый reportId или изменённый snapshot требуют
  нового формирования. Генерация сама по себе не завершает сезон.
- Утверждённая версия неизменяема. Уточнение после COMPLETED создаёт новый DRAFT;
  экспорт продолжает использовать прежний APPROVED. Повторное утверждение
  версии не выполняет повторный lifecycle-переход. Новый неутверждённый DRAFT
  блокирует архивирование.
- Pollen Tree только читается через `PollenService.usage` и `PollenTreeClient`.
  `sourceVersion` участвует в проверке свежести. Отсутствующая usage — blocker;
  сбой провайдера — исключение, не нулевой расход и не фиктивный успешный отчёт.
  Прямых pollen-записей в PostgreSQL нет.
- Отчёт, lifecycle и AuditLog должны коммититься в одной локальной транзакции.
  Исключение любой записи откатывает всю команду; успех возвращается только
  после COMMIT. Аудит append-only; обновление/удаление истории не показано.
  Локальная блокировка не даёт распределённого ACID-снимка с Древом.

Это **исходно-привязанный целевой проект**, не заявление о готовом runtime:

- `CompletionService`, доменная логика, порты и `JdbcCommandTransaction`
  существуют. `SeasonPlanRepository` имеет JDBC-реализацию; `AuditLogService`
  использует существующий audit-порт/JDBC-адаптер.
- `CompletionController «проект»` — отсутствующая REST-граница. UC-5 UI/API,
  resource-visibility для `view`/`export`, подключение сервиса в Spring,
  production-адаптеры Reports/источников/каталога ещё требуют реализации.
- `CompletionPorts.Exporter` существует, но настоящий PDF/HTML renderer отсутствует.
  Не предлагаются фиктивные готовые URL, JDBC-классы или renderer-методы.
- Порты разных источников не объединены в один невозможный Java-объект:
  несколько `forPlan(UUID)` с несовместимыми возвращаемыми типами требуют
  отдельных участников. Детализация 3a изолирует эту сеть от основного потока.

Покрытие: FR-69–73 — проверки, завершение и архивирование; FR-74–77 —
источники/версии/утверждение/экспорт; FR-13, FR-78–81 — аудит.
Регистрация инцидентов и уведомления FR-91 относятся к UC-4, не добавлены
как выдуманный шаг закрытия. Историческая аналитика — последующее использование
отчётов, а не обязательная операция UC-5.

## Проверки и воспроизведение

[validation.json](validation.json) содержит PASS, количества и SHA-256 VPP.
Проект сохранён и его копия открыта **в другом процессе VP**. Проверены:
нативный тип CommunicationDiagram, акторы/типы участников, связи и концы
сообщений, forward/reverse-направления, номера/guards/iteration, сигнатуры,
положение подписей, периметры связей, заметки и сохранённые условия взаимодействий.
Все PNG/SVG получены из повторно открытого VPP и визуально просмотрены.
18 регрессионных тестов UC-5 и 21 существующий тест генераторов UC-3/пакетов
проходят; независимое семантическое ревью принято после исправления границ
транзакций и вложенной нумерации. Проверки приложения не запускались:
изменения ограничены документацией и её инструментами.
Водяные знаки evaluation edition сохранены. Полный desktop-тест мышью не выполнялся.

[communications.xml](communications.xml) — вход генератора, не замена VPP;
[native-reopened.xml](native-reopened.xml) — отчёт Open API;
[source-manifest.json](source-manifest.json) — происхождение и хэши исходников.
[tools](tools) содержит генератор, PowerShell runner и регрессионные тесты.
Инструменты документации: Python standard library, установленный Graphviz,
Java compiler и bundled VP Java 11; runtime Java 17 приложения не менялся.

Из Windows PowerShell, в **новый** выходной каталог:

```powershell
python -B -m unittest discover -s .\docs\architecture\uc-5\tools -p 'test_*.py'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\docs\architecture\uc-5\tools\Generate-Communications.ps1 -Output "$env:TEMP\seasonplan-uc5-new"
python -B .\docs\architecture\uc-5\tools\validate_native.py .\docs\architecture\uc-5
```

Runner отказывается перезаписывать VPP, использует только копию baseline,
проверяет неизменность оригинала и удаляет собственный установленный плагин.
Код приложения, старые VPP и существующие пользовательские правки не менялись.
