# UC-2 — Согласовать сезонный план: UML 2 Activity

Откройте **[SeasonPlan-UC2-activities.vpp](SeasonPlan-UC2-activities.vpp)** в **Visual Paradigm 18.1** через **Project → Open**. Импорт и авторский плагин не нужны. Это редактируемый нативный проект: действия, потоки, разделы ответственности, операции и объекты являются UML-моделями, а не картинками.

## Три требуемых представления

| Диаграмма | Уровень описания | Содержимое |
| --- | --- | --- |
| **1. UC-2 — Абстрактное описание** | Use-case view | Главный хранитель, система, Фея-планировщик; проверка, утверждение, возврат, отклонённый резерв, условное утверждение, аудит и уведомление. Вход — SUBMITTED. |
| **2. UC-2 — Более подробное описание…** | Logical view | Взаимодействия клиента, API/безопасности, приложения, домена/портов и адаптеров. Явные границы чтения плана и обращения к Древу. Транзакции, ошибки и независимая доставка уведомления. |
| **3. UC-2 — Полное описание прецедента…** | Implementation view | Вызовы методов, классы, переменные/объекты, типизированные результаты, предлагаемые расширения, COMMIT, ExceptionHandler и ROLLBACK. |

Дополнительно включена **0. UC-2 — Пакеты и уровни логического представления**. Существующей диаграммы пакетов UC-2 в репозитории не найдено, поэтому эта опорная диаграмма построена по [фактической структуре backend](../../../backend/README.md). Пять её пакетов **непосредственно связаны через `ActivityPartition.represents`** с пятью одноимёнными разделами логической activity:

1. **Клиент** — `frontend/src/features/planReview`, `frontend/src/api`.
2. **API и безопасность** — `lifecycle.api`, `lifecycle.service`, `com.seasonplan.security`.
3. **Приложение** — `application.approval`, `application.pollen`, `application.shared`, `application.preparation`.
4. **Домен и порты** — `domain`, `application.port`.
5. **Адаптеры** — `persistence`, `adapter.pollentree`, `adapter.fake`, `config`.

Java-пути сокращены относительно `com.seasonplan.lifecycle`, кроме явно указанного security. Это архитектурные группировки, **не новые пакеты исходного кода или Maven-модули**. Стрелки package-диаграммы означают зависимости кода; вызов реализации порта через DI не обращает направление зависимости.

## Область и решения по требованиям

Моделируется только **второй прецедент**, «Согласовать сезонный план». Планирование/повторная отправка UC-1, подтверждение пыльцы её хранителем и подготовка/расширенный чек-лист UC-3 здесь не разворачиваются.

При противоречиях исходных спецификаций использовано [авторитетное решение ST-00](../ST-00-specification-resolution.md):

- Главный хранитель проверяет `SUBMITTED → VERIFIED`, затем утверждает только `VERIFIED → APPROVED`.
- Возврат с непустой причиной: `SUBMITTED / VERIFIED → DRAFT`. Самопереход VERIFIED → VERIFIED не выполняется; повторное рассмотрение показано в подробных видах.
- Перед APPROVED читается актуальный авторитетный резерв через **PollenService → PollenTreeClient → HTTPS-адаптер → Древо Пыльцы**. Никаких локальных изменений резерва.
- Разрешающий статус резерва — **CONFIRMED**, не APPROVED. REJECTED без действующего CONFIRMED приводит к мотивированному возврату. Отклонённая замена не отменяет предыдущий действующий подтверждённый резерв.
- Условное утверждение остаётся APPROVED; именованные условия с доказательствами и владельцами передаются в расширенный чек-лист UC-3. Новый статус не вводится.
- Каждый переход, AuditLog и относящийся к решению outbox фиксируются атомарно. Проверка VERIFIED — отдельная команда со своим аудитом; откат последующего решения не удаляет уже сохранённую проверку.
- Неизменяемый AuditLog только дополняется. Уведомление доставляется после COMMIT; HTTP-ответ не ждёт доставки или её повторов.

Трассировка: UC-2 main flow и все три alternative flows; FR-7–FR-9, FR-13, FR-19, FR-78–FR-81; уведомление результата из UC-2 и ST-00. FR-87 в SRS означает уведомление о дефиците, а не общее уведомление об утверждении — эта неточность исходной ссылки не перенесена в модель.

## Это целевой проект, а не утверждение о готовой реализации

Синие действия используют существующие элементы проекта; **«проект»** и **«изменить»** явно обозначают отсутствующие расширения и необходимое изменение семантики. Даже существующий метод внутри целевой последовательности не означает, что вся последовательность уже интегрирована.

| Аспект | В текущем коде | В целевой activity |
| --- | --- | --- |
| Verify | Нет команды, маршрута и `SeasonPlan.verify`; нет `SEASON_PLAN_VERIFIED` в AuditAction | Явно предлагаемые API/сервис/доменный переход и аудит |
| Approve | `ApprovalService.approve` и `SeasonPlan.approve` требуют SUBMITTED и пропускают VERIFIED | Исправленный guard; предлагаемый overload с `List<ApprovalCondition>` |
| Return | Только SUBMITTED → DRAFT | Поддержать также VERIFIED → DRAFT |
| Conditions | Нет модели условий и поля запроса | Предлагаемые `ApprovalCondition`, `DecisionRequest.conditions`, фасад/overload и сохранение снимка условий |
| Review history | `AuditLogService.entriesFor` существует, но API и UI истории отсутствуют | Проектируемое раскрытие истории в карточке |
| Atomicity | Approval делает audit → fake notification → save без общего CommandTransaction | Общая транзакция, явный COMMIT и обработчики исключений |
| Notifications | `NotificationService` — in-memory fake списка строк | UC-2 wiring к существующему контракту NotificationOutbox; durable adapter/delivery ещё нужны |
| Calendar / restrictions | `PlanningService.validatePlan` и разрешающий stub календаря; не полная целевая проверка конфликтов территорий | Повторные проверки в verify и перед утверждением требуют доработки |

Существующий `SeasonPlanStatus.canTransitionTo` сам по себе не доказывает соблюдение матрицы командами: текущий `SeasonPlan.approve` её не вызывает. Production-код в этой работе **не изменялся**.

Предлагаемые подписи/поля — решения для проектирования, **не существующие Java-декларации**. В implementation-модели основные существующие и предлагаемые вызовы связаны с настоящими UML Operations через CallOperationAction; вторичные вызовы видны в тексте действий. Типы в `Implementation type projections` — проекции для этого прецедента, не полный class diagram приложения.

## Нотация UML 2 и выполнение

- Скруглённые прямоугольники — Actions, ромбы — отдельные DecisionNode и MergeNode. Взаимоисключающие ветви не объединяются неявным join.
- Чёрная точка — InitialNode; мишень — ActivityFinalNode. Статусы плана — данные/ограничения действий, **не UML State вместо activity-узлов**.
- Толстые чёрные полосы — ForkNode / JoinNode: HTTP-ответ и доставка уведомления независимы. Последующий join завершает весь бизнес-процесс, **не удерживает HTTP-запрос**.
- Прямоугольные типизированные ObjectNodes и ObjectFlows из OutputPins показывают `SeasonPlan`, `Optional<PollenReservation>` и `AuditLogEntry`. Optional не выдаётся за извлечённый объект.
- Зигзагообразные ExceptionHandlers имеют тип Exception и общий типизированный exception-input обработчика. Они защищают вызовы до COMMIT включительно, а также блокировку отсутствующего CONFIRMED. Обработчик — StructuredActivityNode с внутренним ROLLBACK и ActivityFinal; у тела нет внешних control-flow, поэтому ошибочная цепочка записей не возобновляется.
- Это проект поведения; live-доступность Древа, совместимость провайдера и реальная доставка здесь не подтверждаются. Ограничения интеграции описаны в [Pollen handoff](../POLLEN_TREE_EXTERNAL_SERVICE.md).

## Просмотр и проверка

SVG и PNG ниже экспортированы **из повторно открытого `.vpp` в отдельном процессе Visual Paradigm**, не из независимого текстового рендерера. Для подробных видов увеличьте масштаб. Evaluation-watermarks локальной установки сохранены.

| Вид | SVG | PNG |
| --- | --- | --- |
| Опорные пакеты | [00-packages.svg](00-packages.svg) | [00-packages.png](00-packages.png) |
| Абстрактный | [01-use-case.svg](01-use-case.svg) | [01-use-case.png](01-use-case.png) |
| Логический | [02-logical.svg](02-logical.svg) | [02-logical.png](02-logical.png) |
| Реализация | [03-implementation.svg](03-implementation.svg) | [03-implementation.png](03-implementation.png) |

[validation.json](validation.json) связывает результат с SHA-256 доставленного проекта и экспортов; [native-reopened.xml](native-reopened.xml) — отчёт Open API, **не импортный проект**.

Пройдены 10 регрессионных тестов исходной модели и проверки повторно открытых UML-типов, текстов, координат, принадлежности разделам, `represents`, ссылок на операции и типизированного параметра conditions, типов результатов, концов/маршрутов потоков, exception-input, чёрных control nodes и непустых зарегистрированных заметок. Проверка выполнена на уровне Open API и визуального просмотра экспортов; полная desktop GUI automation и жест drag captions не выполнялись. Guard captions сохранены в стандартном режиме `SIDE_NONE`.

## Источники и воспроизведение

Основные источники: [Use-case specification](../../../arch_docs/USE_CASE_SPECIFICATION.md), [SRS](../../../arch_docs/SYSTEM_REQUIREMENTS_SPECIFICATION.md), [Vision](../../../arch_docs/VISION.md), ST-00, backend README, [ApprovalService](../../../backend/src/main/java/com/seasonplan/lifecycle/application/approval/ApprovalService.java), [SeasonPlan](../../../backend/src/main/java/com/seasonplan/lifecycle/domain/SeasonPlan.java), [PollenService](../../../backend/src/main/java/com/seasonplan/lifecycle/application/pollen/PollenService.java), [LifecycleController](../../../backend/src/main/java/com/seasonplan/lifecycle/api/LifecycleController.java), [PlanReviewPage](../../../frontend/src/features/planReview/PlanReviewPage.jsx) и соответствующие тесты согласования.

Авторские инструменты находятся в [tools](tools): декларативная модель, тесты, Java Open API plugin и read-only validator. Python standard library используется **только для подготовки документации/проверок**, не добавляется в стек приложения. Java-плагин компилируется отдельно с `--release 11` для bundled VP JVM; target приложения остаётся Java 17.

PowerShell 5.1, из корня репозитория:

```powershell
python -B -m unittest discover -s .\docs\architecture\uc-2\tools -p test_diagrams.py
python -B .\docs\architecture\uc-2\tools\validate_native.py .\docs\architecture\uc-2

# Повторная генерация в НОВОЙ папке, не поверх пользовательских правок.
# Bypass действует только на этот дочерний PowerShell-процесс.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  .\docs\architecture\uc-2\tools\Generate-Activities.ps1 `
  -Output "$env:TEMP\seasonplan-uc2-regenerated"
```

Скрипт загружает только временную копию UC-4 baseline, проверяет неизменность оригинала, удаляет свой временный плагин и scratch-копии. Исходные проекты UC-4 не изменяются. Тесты приложения не перезапускались: изменения ограничены архитектурной документацией и её инструментами.
