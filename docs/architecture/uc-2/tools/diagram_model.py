"""UC-2 documentation model. Emits XML consumed by the native VP authoring plugin.
Python is authoring/test tooling only, not an application dependency.
"""
from pathlib import Path
import heapq
import json
import xml.etree.ElementTree as ET

PACKAGES = [
    dict(id="client", name="Клиент", paths=["frontend/src/features/planReview", "frontend/src/api"]),
    dict(id="boundary", name="API и безопасность", paths=["lifecycle.api", "lifecycle.service", "com.seasonplan.security"]),
    dict(id="application", name="Приложение", paths=["application.approval", "application.pollen", "application.shared", "application.preparation"]),
    dict(id="core", name="Домен и порты", paths=["domain", "application.port"]),
    dict(id="adapters", name="Адаптеры", paths=["persistence", "adapter.pollentree", "adapter.fake", "config"]),
]

# Each row is one explanatory step; alternatives are placed beside the main path.
# Names in the first two views deliberately contain no implementation signatures.
ROWS = [
    ("start", "initial", 0, 0, "", "", ""),
    ("open", "action", 0, 1, "Открыть отправленный\nсезонный план", "Запросить карточку плана\nи признаки готовности", "PlanReviewPage: decide / загрузка\nlifecycleApi.getPlan(id)\nlifecycleApi.getReadiness(id)"),
    ("authorize", "action", 1, 2, "Проверить право согласования", "Аутентификация и RBAC:\nГлавный хранитель", "CurrentActorProvider.currentActor()\nactor.requiredRole(CHIEF_GUARDIAN)\nValidationSupport.requireRole(...)"),
    ("access", "decision", 1, 3, "", "", ""),
    ("load", "action", 1, 4, "Получить параметры, историю\nи признаки готовности", "Прочитать план, историю аудита\nи сводку готовности", "LifecycleController.getPlan(id)\nSeasonPlanService.get(id) → PlanningService.get(id)\nSeasonPlanRepository.findById(id)"),
    ("history", "action", 2, 5, "Подготовить сводку для проверки", "Собрать историю решений\nи актуальные ограничения", "AuditLogService.entriesFor(id)\nReadinessChecklistService.calculateChecklist(id)\n«проект» API истории аудита"),
    ("view", "action", 0, 6, "Изучить параметры, историю\nи ограничения сезона", "Показать карточку, историю\nи признаки готовности", "PlanReviewPage: setPlan / setReadiness\n«проект» отобразить историю AuditLog\nSeasonPlanResponse, ReadinessResponse"),
    ("review", "decision", 0, 7, "", "", ""),
    ("status", "decision", 2, 8, "", "", ""),
    ("verify", "action", 2, 9, "Завершить проверку:\nSUBMITTED → VERIFIED\nСохранить переход и аудит", "Зарегистрировать проверку:\nSUBMITTED → VERIFIED\nАтомарно сохранить и записать аудит", "«проект» ApprovalService.verify(id, role, actorId)\nplan.verify(actorId, now): SUBMITTED → VERIFIED\nCommandTransaction.execute(id, command)"),
    ("verify-store", "action", 3, 10, "Сохранить результат проверки", "Зафиксировать проверку\nв хранилище и неизменяемом аудите", "«проект» внутри verify-транзакции:\nAuditLogService.append(id, SEASON_PLAN_VERIFIED, ...)\nSeasonPlanRepository.save(plan); COMMIT"),
    ("review-merge", "merge", 2, 11, "", "", ""),
    ("decision", "decision", 0, 12, "", "", ""),
    ("conditional", "action", 0, 13, "Задать условия утверждения:\nпроверки, доказательства, ответственные", "Добавить именованные условия\nрасширенного чек-листа UC-3", "«проект» ApprovalCondition\n{key, displayText, evidenceRequirement, ownerRole}\nУсловия решения; не новый статус"),
    ("approval-merge", "merge", 2, 14, "", "", ""),
    ("pollen-read", "action", 4, 15, "Проверить актуальный резерв\nчерез Древо Пыльцы: CONFIRMED", "Приложение → PollenService → порт\n→ HTTPS-адаптер → Древо Пыльцы", "pollen: PollenService.findLatestBySeasonPlanId(id)\nclient: PollenTreeClient.findLatestBySeasonPlanId(id)\nRestPollenTreeClient → HTTPS GET; требуется CONFIRMED"),
    ("reservation", "decision", 2, 16, "", "", ""),
    ("approve", "action", 2, 17, "Утвердить план:\nVERIFIED → APPROVED\nСохранить условия, если заданы", "Выполнить решение об утверждении\nVERIFIED → APPROVED\nУсловия станут входом UC-3", "«изменить» ApprovalService.approve(id, comment, role, actorId)\nplan: SeasonPlan.approve(comment, actorId, now)\nVERIFIED → APPROVED; «проект» сохранить условия"),
    ("pollen-block", "action", 3, 17, "При REJECTED заблокировать\nутверждение; указать причину возврата", "Резерв отклонён:\nзапретить APPROVED\nподготовить мотивированный возврат", "«проект» REJECTED: причина из PollenReservation\ncomment = причина отклонения\nНе изменять резерв локально"),
    ("blocked", "action", 4, 18, "Отсутствует подтверждение / сбой:\nпоказать блокировку; статус не менять", "Нет CONFIRMED / Древо недоступно:\nотменить команду; вернуть причины\nповтор после восстановления ресурса", "DomainValidationException / PollenTreeUnavailableException\nLifecycleExceptionHandler → ошибки API\nVERIFIED сохранён; retry новым запросом"),
    ("comment-merge", "merge", 0, 18, "", "", ""),
    ("comment", "action", 0, 19, "Ввести замечания\nдля возврата на доработку", "Получить обязательный\nкомментарий Главного хранителя", "PlanReviewPage.decide('return')\nlifecycleApi.returnPlan(id, {comment})\nLifecycleController.returnForRevision(id, request)"),
    ("comment-check", "decision", 1, 20, "", "", ""),
    ("comment-error", "action", 0, 21, "Показать ошибку:\nкомментарий обязателен", "Отклонить пустой комментарий\nбез смены статуса", "DomainValidationException: errors['comment']\nFieldErrors; текущий статус не меняется"),
    ("return-merge", "merge", 2, 21, "", "", ""),
    ("return", "action", 2, 22, "Вернуть на доработку с причиной:\nSUBMITTED / VERIFIED → DRAFT", "Оформить мотивированный возврат:\nSUBMITTED / VERIFIED → DRAFT", "«изменить» ApprovalService.returnForRevision(id, comment, role, actorId)\nplan.returnForRevision(comment, actorId, now)\nSUBMITTED / VERIFIED → DRAFT"),
    ("result-merge", "merge", 2, 23, "", "", ""),
    ("audit", "action", 3, 24, "Записать решение в AuditLog\nтолько добавлением", "Добавить неизменяемую запись\nо решении, акторе и времени", "AuditLogService.append(id, action, actorId, comment)\nAuditLogRepository.append(entry: AuditLogEntry)\naction = SEASON_PLAN_APPROVED / SEASON_PLAN_RETURNED"),
    ("save", "action", 4, 25, "Сохранить статус и замечания", "Сохранить план и условия\nв общей транзакции решения", "SeasonPlanRepository.save(plan)\nJdbcSeasonPlanRepository.save(plan)\n«проект» условия решения в той же транзакции"),
    ("outbox", "action", 3, 26, "Подготовить уведомление\nвместе с решением", "Сохранить событие уведомления\nв транзакционном outbox", "«проект» NotificationOutbox.enqueue(event) для UC-2\nevent: NotificationOutbox.Event\nполучатель = plan.createdBy(); IN_APP + EMAIL"),
    ("commit", "decision", 4, 27, "", "", ""),
    ("rollback", "action", 4, 28, "При ошибке отменить решение:\nстатус и аудит не изменены", "ROLLBACK плана, аудита и outbox\nВозврат ошибки; нет уведомления", "«проект» CommandTransaction.execute(id, command)\nJdbcCommandTransaction: ROLLBACK\nотмена локальной мутации / перечитать plan"),
    ("notify", "action", 4, 29, "Уведомить Фею-планировщика\nпосле сохранения решения", "После COMMIT доставить уведомление\nПовторять сбои; дедупликация eventId", "«проект» outbox-адаптер: IN_APP / EMAIL\nretry: экспоненциально до 24 ч; dedupe eventId\nНе откатывать уже сохранённое решение"),
    ("receive", "action", 0, 30, "Получить решение и замечания\nAPPROVED либо DRAFT", "Показать результат и уведомить автора\nAPPROVED → вход UC-3; DRAFT → вход UC-1", "PlanReviewPage: setPlan(result); cachePlan(result)\nsetMessage(...); автор получает уведомление\nAPPROVED или DRAFT; UC-3/UC-1 вне границы"),
    ("denied", "action", 1, 31, "Отклонить недопустимый запрос\nбез изменений", "Вернуть 401 / 403 / 404 / 400\nбез изменения плана", "SecurityConfiguration / LifecycleExceptionHandler\n401, 403, 404 либо errors['status']\nСостояние и аудит не изменяются"),
    ("end-merge", "merge", 1, 32, "", "", ""),
    ("end", "final", 1, 33, "", "", ""),
]

EDGES = [
    ("start", "open", ""), ("open", "authorize", ""), ("authorize", "access", ""),
    ("access", "load", "доступ разрешён; план существует; SUBMITTED / VERIFIED"), ("access", "denied", "else"),
    ("load", "history", ""), ("history", "view", ""), ("view", "review", ""),
    ("review", "status", "проверка пройдена"), ("review", "comment-merge", "нужны правки"),
    ("status", "verify", "SUBMITTED"), ("status", "review-merge", "VERIFIED"),
    ("verify", "review-merge", "проверка и аудит сохранены"),
    ("review-merge", "decision", ""),
    ("decision", "approval-merge", "утвердить без условий"), ("decision", "conditional", "утвердить с условиями"),
    ("decision", "comment-merge", "вернуть"), ("conditional", "approval-merge", ""),
    ("approval-merge", "pollen-read", ""), ("pollen-read", "reservation", ""),
    ("reservation", "approve", "CONFIRMED"), ("reservation", "pollen-block", "REJECTED и нет CONFIRMED"),
    ("reservation", "blocked", "else: отсутствует / DRAFT / недоступен"),
    ("pollen-block", "return-merge", ""), ("comment-merge", "comment", ""),
    ("comment", "comment-check", ""), ("comment-check", "return-merge", "непустой комментарий"),
    ("comment-check", "comment-error", "else"), ("comment-error", "comment-merge", "исправить"),
    ("return-merge", "return", ""), ("return", "result-merge", ""), ("approve", "result-merge", ""),
    ("result-merge", "audit", ""), ("audit", "save", ""), ("save", "outbox", ""),
    ("outbox", "commit", ""), ("commit", "notify", "COMMIT успешен"),
    ("commit", "rollback", "else: ошибка записи / аудита / outbox"), ("notify", "receive", ""),
    ("receive", "end-merge", ""), ("blocked", "end-merge", ""), ("rollback", "end-merge", ""),
    ("denied", "end-merge", ""), ("end-merge", "end", ""),
]


def build_diagrams():
    result = []
    for index, (key, subtitle) in enumerate([
        ("01-use-case", "Абстрактное описание"),
        ("02-logical", "Более подробное описание; уровни взаимодействия совпадают с диаграммой пакетов"),
        ("03-implementation", "Полное описание прецедента с указанием вызываемых методов, используемых классов и объектов"),
    ]):
        # Abstract view uses three actor/system partitions; detailed views share actual package layers.
        lanes = ([dict(id="guardian", name="Главный хранитель"), dict(id="system", name="Система SeasonPlan"), dict(id="planner", name="Фея-планировщик")]
                 if index == 0 else [dict(id=p["id"], name=p["name"]) for p in PACKAGES])
        width = 430 if index == 0 else (390 if index == 1 else 560)
        step = 120 if index == 0 else (130 if index == 1 else 150)
        diagram = dict(id=key, name=f"{index+1}. UC-2 — {subtitle}", subtitle=subtitle,
                       lanes=lanes, laneWidth=width, height=360+34*step, nodes=[], edges=[])
        for ident, kind, lane, row, abstract, logical, implementation in ROWS:
            # verify-store is folded into verify's atomic action; no extra execution token.
            if ident == "verify-store":
                continue
            if index == 0:
                actual_lane = 0 if ident in {"start", "open", "view", "review", "decision", "conditional", "comment-merge", "comment", "comment-error"} else (2 if ident == "receive" else 1)
                # Separate rejected-resource branch from approve in the system partition.
                offset = 110 if ident == "pollen-block" else (-110 if ident == "approve" else 0)
            else:
                actual_lane, offset = lane, 0
            w, h = ((20, 20) if kind in {"initial", "final"} else ((40, 40) if kind in {"decision", "merge"} else (width-70, 80 if index < 2 else 100)))
            w, h = (w+19)//20*20, (h+19)//20*20
            x = 60+actual_lane*width+(width//20*10)-w//2+offset
            if index == 0 and ident in {"approve", "pollen-block"}:
                w = 200; x = 60+actual_lane*width+(width//20*10)-w//2+offset
            text = (abstract, logical, implementation)[index]
            if ident == "verify" and index == 2:
                text += "\naudit.append(...SEASON_PLAN_VERIFIED...); plans.save(plan); COMMIT"
                h = 124
            diagram["nodes"].append(dict(id=ident, kind=kind, lane=lanes[actual_lane]["id"], text=text,
                                         x=x//10*10, y=260+row*step, w=w//10*10, h=h//10*10))
        for source, target, guard in EDGES:
            diagram["edges"].append(dict(id=f"e{len(diagram['edges'])}", **{"from": source, "to": target}, guard=guard, kind="control"))
        diagram["notes"] = [
            "UC-2 «Согласовать сезонный план». Целевая модель по ST-00, НЕ описание готового runtime.\nПредусловия: авторизованный Главный хранитель; план SUBMITTED; основные проверки выполнены.\nПовторное рассмотрение VERIFIED допустимо. Пыльца подтверждается до утверждения через Древо Пыльцы.",
            "UML 2: скруглённый прямоугольник — действие; ромб — decision / merge; стрелка — ControlFlow.\nGuards взаимоисключающие; merge не синхронизирует альтернативы. Статусы указаны как данные, не State.\nverify — отдельная атомарная команда. Решение: мутация + аудит + план + outbox в одной транзакции.\nОшибка любого шага записи → ROLLBACK. Доставка только после COMMIT. UC-1 и UC-3 не разворачиваются.",
        ]
        if index == 2:
            diagram["notes"].append("«проект» — отсутствующая реализация / предлагаемая операция. «изменить» — существующий метод с неверной семантикой.\nСейчас approve пропускает VERIFIED; verify, условия, API аудита и durable outbox UC-2 отсутствуют.\nNotificationService — только in-memory fake; целевая доставка показана как проект.\nРезерв в ST-00 = CONFIRMED (не APPROVED). REJECTED replacement не отменяет действующий CONFIRMED.")
        if index > 0:
            # Expose each actual package boundary instead of drawing a single cross-layer action.
            for n in diagram["nodes"]:
                if n["y"] >= 260+15*step:
                    n["y"] += 2*step
            diagram["height"] += 2*step
            def add(ident, lane, row, logical, implementation, kind="action", type_name=""):
                w,h=(width-70,80 if index==1 else 100)
                w=(w+19)//20*20
                n=dict(id=ident,kind=kind,lane=lanes[lane]["id"],text=logical if index==1 else implementation,
                       x=60+lane*width+(width//20*10)-w//2,y=260+row*step,w=w,h=h)
                if type_name:
                    n["type"]=type_name
                diagram["nodes"].append(n)
            def edge(source,target,guard="",kind="control"):
                diagram["edges"].append(dict(id=f"detail-{source}-{target}",**{"from":source,"to":target},guard=guard,kind=kind))
            def replace_edge(source,target,chain):
                diagram["edges"]=[e for e in diagram["edges"] if (e["from"],e["to"]) != (source,target)]
                for a,b in zip([source]+chain,chain+[target]):
                    edge(a,b)
            add("load-service",2,4,"Прочитать карточку через\nприкладной сервис планирования","SeasonPlanService.get(id)\nPlanningService.get(id): SeasonPlan")
            add("load-repo",3,4,"Передать запрос чтения\nчерез SeasonPlanRepository","plans: SeasonPlanRepository.findById(id)\nOptional<SeasonPlan> → plan")
            add("load-jdbc",4,4,"Загрузить план из PostgreSQL\nчерез JDBC-адаптер","JdbcSeasonPlanRepository.findById(id)\n→ plan: SeasonPlan; JDBC / PostgreSQL")
            replace_edge("load","history",["load-service","load-repo","load-jdbc"])
            add("approval-command",1,15,"Передать решение об утверждении\nчерез API и фасад","lifecycleApi.approvePlan(id, {comment})\nLifecycleController.approve(id, request)\nSeasonPlanService.approve(id, comment, role, actorId)")
            add("approval-tx",2,16,"Начать транзакцию решения\nзаблокировать и перечитать план VERIFIED","«проект» CommandTransaction.execute(id, command)\nJdbcCommandTransaction: SELECT ... FOR UPDATE\nplans.findById(id); require VERIFIED")
            add("pollen-service",2,17,"Проверить ресурс через единую\nприкладную границу PollenService","pollen: PollenService.findLatestBySeasonPlanId(id)\n→ Optional<PollenReservation>")
            add("pollen-port",3,17,"Запросить актуальный резерв\nчерез порт PollenTreeClient","client: PollenTreeClient.findLatestBySeasonPlanId(id)\n→ Optional<PollenReservation>")
            replace_edge("approval-merge","pollen-read",["approval-command","approval-tx","pollen-service","pollen-port"])
            next(n for n in diagram["nodes"] if n["id"]=="pollen-read")["text"]=("Вызвать Древо Пыльцы по HTTPS\nполучить авторитетный резерв" if index==1 else "RestPollenTreeClient.findLatestBySeasonPlanId(id)\nHTTPS GET /season-plans/{id}/reservations/latest\nCONFIRMED — единственное разрешение APPROVED")
            add("return-tx",1,23,"Начать транзакцию возврата\nперечитать SUBMITTED / VERIFIED","«проект» CommandTransaction.execute(id, command)\nplans.findById(id); require SUBMITTED / VERIFIED")
            diagram["edges"]=[e for e in diagram["edges"] if (e["from"],e["to"]) != ("comment-check","return-merge")]
            edge("comment-check","return-tx","непустой комментарий");edge("return-tx","return-merge")
            # Access errors and stale state terminate without a transition. Role authentication is a precondition.
            if index==2:
                for ident,lane,row,text,type_name,source in [
                    ("plan-object",3,5,"plan: SeasonPlan\n[SUBMITTED / VERIFIED]","SeasonPlan","load-repo"),
                    ("reservation-object",3,18,"reservation: PollenReservation\n[authoritative; если присутствует]","PollenReservation","pollen-read"),
                    ("audit-object",2,26,"entry: AuditLogEntry\n[append-only]","AuditLogEntry","audit"),
                ]:
                    add(ident,lane,row,text,text,"object",type_name);edge(source,ident,kind="object")
        if index == 0:
            # Business abstraction: hide transport, repositories and transaction mechanics.
            hidden = {"authorize", "access", "history", "status", "save", "outbox", "commit", "rollback", "denied"}
            diagram["nodes"] = [n for n in diagram["nodes"] if n["id"] not in hidden]
            diagram["edges"] = [e for e in diagram["edges"] if e["from"] not in hidden and e["to"] not in hidden]
            for source,target,guard in [("open","load",""), ("load","view",""), ("review","verify","проверка пройдена"), ("audit","notify","")]:
                diagram["edges"].append(dict(id=f"abstract-{source}", **{"from":source,"to":target},guard=guard,kind="control"))
            # A single predecessor no longer needs a merge node.
            diagram["nodes"] = [n for n in diagram["nodes"] if n["id"] != "review-merge"]
            diagram["edges"] = [e for e in diagram["edges"] if e["from"] != "review-merge" and e["to"] != "review-merge"]
            diagram["edges"].append(dict(id="abstract-verified", **{"from":"verify","to":"decision"},guard="",kind="control"))
            next(n for n in diagram["nodes"] if n["id"] == "audit")["text"] = "Атомарно сохранить решение\nи неизменяемую запись AuditLog"
            # Compress removed explanatory rows without moving branches out of their partitions.
            ys = sorted({n["y"] for n in diagram["nodes"]})
            for n in diagram["nodes"]:
                n["y"] = 260+ys.index(n["y"])*120
            diagram["height"] = 420+len(ys)*120
        if index == 0:
            diagram['notes'][0] = diagram['notes'][0].replace('Повторное рассмотрение VERIFIED допустимо. ', 'Вход только SUBMITTED; повторный обзор VERIFIED показан в подробных видах. ')
        else:
            complete_target(diagram,index,step,width)
        result.append(diagram)
    return result


def complete_target(d,index,step,width):
    """Explicit exception semantics, separate API response, and condition propagation."""
    nodes={n['id']:n for n in d['nodes']}
    def place(n,lane,row):
        n['lane']=PACKAGES[lane]['id'];n['x']=60+lane*width+width//20*10-n['w']//2;n['y']=260+row*step
    def add(ident,kind,lane,row,text,w=None,h=None,parent=None):
        w=w if w is not None else (width-70+19)//20*20
        h=h if h is not None else (80 if index==1 else 100)
        n=dict(id=ident,kind=kind,text=text,w=w,h=h)
        place(n,lane,row)
        if parent:n['parent']=parent
        d['nodes'].append(n);nodes[ident]=n;return n
    def edge(a,b,guard='',kind='control'):
        d['edges'].append(dict(id=f'target-{a}-{b}',**{'from':a,'to':b},guard=guard,kind=kind))
    d['edges']=[e for e in d['edges'] if (e['from'],e['to']) not in {('commit','notify'),('commit','rollback'),('rollback','end-merge'),('receive','end-merge'),('status','verify'),('blocked','end-merge')}]
    for e in d['edges']:
        if (e['from'],e['to'])==('pollen-read','reservation'):e['guard']=''
        if (e['from'],e['to'])==('reservation','blocked'):e['guard']='else: отсутствует / DRAFT'
        if (e['from'],e['to'])==('access','load'):e['guard']='роль CHIEF_GUARDIAN'
    # Actor verification is an explicit command, not an automatic client-selected status.
    add('verify-command','action',1,9,'Инициировать команду проверки через API' if index==1 else '«проект» lifecycleApi.verifyPlan(id)\nPOST /api/season-plans/{id}/verify\nLifecycleController.verify(id) → SeasonPlanService.verify(...)')
    edge('status','verify-command','SUBMITTED');edge('verify-command','verify')
    place(nodes['return-tx'],2,22)
    place(nodes['audit'],2,26)
    if index==2:
        place(nodes['audit-object'],3,26)
        nodes['reservation-object']['type']='Optional<PollenReservation>'
        nodes['reservation-object']['text']='reservationResult: Optional<PollenReservation>\n[empty либо актуальный резерв]'
        for e in d['edges']:
            if e['to']=='plan-object':e['from']='load-service'
        nodes['conditional']['text']='«проект» conditions: List<ApprovalCondition>\n{key, displayText, evidenceRequirement, ownerRole}\nПо умолчанию []; не новый статус'
        nodes['approval-command']['text']='«проект» approvePlan(id, {comment, conditions})\nLifecycleController.approve(id, request)\nSeasonPlanService.approve(id, comment, conditions, role, actorId)'
        nodes['approve']['text']='«проект» ApprovalService.approve(id, comment, conditions, role, actorId)\nplan.approve(comment, actorId, now); «изменить» guard\nplan.setApprovalConditions(conditions) «проект»; VERIFIED → APPROVED'
        nodes['verify']['text']='«проект» ApprovalService.verify(id, role, actorId)\nCommandTransaction.execute(id, () → validate; plan.verify(...))\naudit.append(...SEASON_PLAN_VERIFIED...); plans.save(plan); COMMIT'
        nodes['approval-tx']['text']+='\n«проект» повторить проверки сезона и территорий'
    nodes['blocked']['text']='Выбросить ошибку: нет CONFIRMED\nExceptionHandler отменит транзакцию' if index==1 else 'throw new DomainValidationException(pollenErrors)\nНет актуального CONFIRMED; ExceptionHandler → ROLLBACK\nПлан остаётся VERIFIED; повтор после восстановления резерва'
    nodes['commit']['kind']='action'
    nodes['commit']['text']='COMMIT общей транзакции решения' if index==1 else '«проект» JdbcCommandTransaction / TransactionTemplate\nCOMMIT: plan + AuditLogEntry + outbox event\nЛюбое исключение записи → exception handler'
    nodes['commit']['w']=(width-70+19)//20*20;nodes['commit']['h']=80 if index==1 else 100;place(nodes['commit'],4,29)
    # The handler body has no incoming/outgoing control edges. Exception input is native;
    # its internal activity-final terminates this invocation without resuming failed writes.
    handler=nodes['rollback'];handler['kind']='handler';handler['w']=(width-30)//20*20;handler['h']=340;place(handler,4,33)
    handler['text']='Обработчик Exception: отменить текущую команду'
    start=add('rollback-start','initial',4,33,'',20,20,'rollback');start['y']=handler['y']+60
    body=add('rollback-body','action',4,33,'ROLLBACK; восстановить статус начала команды\nВернуть ошибку; уведомление не публиковать' if index==1 else '«проект» JdbcCommandTransaction: ROLLBACK\nПеречитать / отбросить мутированный plan; вернуть ошибку\nСохранённый ранее VERIFIED и его аудит не отменять',handler['w']-40,120,'rollback');body['y']=handler['y']+110
    end=add('rollback-final','final',4,33,'',20,20,'rollback');end['y']=handler['y']+280
    edge('rollback-start','rollback-body');edge('rollback-body','rollback-final')
    for source in ['verify','approval-tx','return-tx','pollen-read','approve','return','audit','save','outbox','commit','blocked']:
        edge(source,'rollback',kind='exception')
    add('after-commit','fork',2,30,'',100,20)
    add('response','action',0,31,'Вернуть и показать результат API\nне ожидая доставки уведомления' if index==1 else 'LifecycleController → SeasonPlanResponse → HTTP 200\nPlanReviewPage: setPlan(result); cachePlan(result)\nsetMessage(...); не ждать outbox delivery')
    add('delivery-join','join',2,34,'',100,20)
    nodes['receive']['text']='Фея-планировщик получает результат\nAPPROVED → UC-3; DRAFT → UC-1' if index==1 else 'Фея-планировщик: получение in-app / email\nРезультат и комментарии; условия APPROVED\nUC-1 / UC-3 вне границы данной activity'
    edge('commit','after-commit');edge('after-commit','response');edge('after-commit','notify')
    edge('response','delivery-join');edge('receive','delivery-join');edge('delivery-join','end-merge')
    d['notes'][1]+='\nЗигзаг — ExceptionHandler; чёрные полосы — fork / join.\nExceptionHandler защищает вызовы, включая verify и COMMIT; handler завершает activity после ROLLBACK.\nFork: ответ API не ждёт доставки. Join завершает весь бизнес-процесс, а НЕ HTTP-запрос.'


def validate(d):
    nodes = {n["id"]: n for n in d["nodes"]}
    if len(nodes) != len(d["nodes"]):
        raise ValueError("duplicate node")
    incoming, outgoing = {k: [] for k in nodes}, {k: [] for k in nodes}
    for edge in d["edges"]:
        if edge["from"] not in nodes or edge["to"] not in nodes:
            raise ValueError("unknown endpoint")
        outgoing[edge["from"]].append(edge)
        incoming[edge["to"]].append(edge)
    reached, pending = set(), [n["id"] for n in nodes.values() if n["kind"] == "initial"]
    while pending:
        k = pending.pop()
        if k in reached:
            continue
        reached.add(k)
        pending.extend(e["to"] for e in outgoing[k])
        pending.extend(n['id'] for n in nodes.values() if n.get('parent')==k and n['kind']=='initial')
    if reached != set(nodes):
        raise ValueError(f"unreachable nodes: {set(nodes)-reached}")
    for k, n in nodes.items():
        if n["w"] <= 0 or n["h"] <= 0:
            raise ValueError("invalid bounds")
        if n["kind"] == "decision" and (len(incoming[k]) != 1 or len(outgoing[k]) < 2 or any(not e["guard"] for e in outgoing[k])):
            raise ValueError(f"invalid decision {k}")
        if n["kind"] == "merge" and (len(incoming[k]) < 2 or len(outgoing[k]) != 1):
            raise ValueError(f"invalid merge {k}")
        control_out = len([e for e in outgoing[k] if e['kind']=='control'])
        expected_out = 0 if k=='blocked' and any(e['kind']=='exception' for e in outgoing[k]) else 1
        if n["kind"] == "action" and (len([e for e in incoming[k] if e["kind"]=="control"]) != 1 or control_out != expected_out):
            raise ValueError(f"implicit join/fork {k}")
    for i, a in enumerate(d["nodes"]):
        for b in d["nodes"][i+1:]:
            if a.get('parent')==b['id'] or b.get('parent')==a['id']:
                continue
            if max(a["x"], b["x"]) < min(a["x"]+a["w"], b["x"]+b["w"])  and max(a["y"], b["y"]) < min(a["y"]+a["h"], b["y"]+b["h"]):
                raise ValueError(f"overlap: {a['id']}, {b['id']}")


def route(d):
    """Grid A*: cardinal endpoints (also true diamond vertices), obstacle clearance."""
    nodes = {n["id"]: n for n in d["nodes"]}
    occupied = set()
    for n in nodes.values():
        if n['kind']=='handler':
            continue
        for x in range(n["x"]-10, n["x"]+n["w"]+11, 10):
            for y in range(n["y"]-10, n["y"]+n["h"]+11, 10):
                occupied.add((x, y))
    used = {}
    for edge in d["edges"]:
        a, b = nodes[edge["from"]], nodes[edge["to"]]
        def ports(n):
            x,y,w,h = (n[k] for k in ("x","y","w","h"))
            return [(x+w//2,y),(x+w,y+h//2),(x+w//2,y+h),(x,y+h//2)]
        # Ensure centers are on the routing grid by using 20-unit dimensions.
        pa, pb = ports(a), ports(b)
        starts, ends = {}, {}
        directions = [(0,-1),(1,0),(0,1),(-1,0)]
        for p, v in zip(pa, directions):
            starts[(p[0]+v[0]*30,p[1]+v[1]*30)] = p
        for side,(p,v) in enumerate(zip(pb,directions)):
            if edge['kind']=='exception' and side!=3:
                continue
            ends[(p[0]+v[0]*30,p[1]+v[1]*30)] = p
        def heuristic(p):
            return min(abs(p[0]-q[0])+abs(p[1]-q[1]) for q in ends)
        queue, best, prev = [], {}, {}
        for p in starts:
            state = (p, -1)
            best[state] = 0
            heapq.heappush(queue, (heuristic(p), 0, state))
        final = None
        while queue:
            _, cost, state = heapq.heappop(queue)
            if cost != best[state]:
                continue
            p, direction = state
            if p in ends:
                final = state; break
            for i,(dx,dy) in enumerate(directions):
                q = (p[0]+dx*10,p[1]+dy*10)
                if q in occupied or not (30 <= q[0] <= 60+len(d["lanes"])*d["laneWidth"] and 220 <= q[1] <= d["height"]):
                    continue
                newcost = cost+10+(18 if direction not in (-1,i) else 0)+used.get(q,0)*3
                target = (q,i)
                if newcost < best.get(target,10**12):
                    best[target] = newcost; prev[target] = state
                    heapq.heappush(queue,(newcost+heuristic(q),newcost,target))
        if final is None:
            raise ValueError(f"route not found {edge['id']}")
        points = []
        while final in prev:
            points.append(final[0]); final = prev[final]
        points.append(final[0]); points.reverse()
        points = [starts[points[0]]] + points + [ends[points[-1]]]
        simple = []
        for p in points:
            while len(simple)>1 and (simple[-2][0] == simple[-1][0] == p[0] or simple[-2][1] == simple[-1][1] == p[1]):
                simple.pop()
            simple.append(p)
        if edge['kind']=='exception':
            # UML2 ExceptionHandler lightning notation, not an ordinary ControlFlow.
            x,y=simple[-1]
            simple=simple[:-1]+[(x-30,y),(x-20,y-7),(x-24,y+7),(x-10,y),(x,y)]
            simple=[p for i,p in enumerate(simple) if i==0 or p!=simple[i-1]]
        edge["points"] = simple
        for p in points:
            used[p] = used.get(p,0)+1


def export(path):
    root = ET.Element("project", name="SeasonPlan UC-2 — UML 2 activities")
    packages = ET.SubElement(root,"packages")
    for p in PACKAGES:
        el = ET.SubElement(packages,"package",id=p["id"],name=p["name"])
        el.text = "\n".join(p["paths"])
    for d in build_diagrams():
        # All cardinal midpoints must align to the ten-unit route grid.
        for n in d["nodes"]:
            n["w"] = (n["w"]+19)//20*20
            n["h"] = (n["h"]+19)//20*20
        validate(d); route(d)
        el = ET.SubElement(root,"diagram",id=d["id"],name=d["name"],width=str(60+len(d["lanes"])*d["laneWidth"]),height=str(d["height"]))
        for i,p in enumerate(d["lanes"]):
            ET.SubElement(el,"lane",id=p["id"],name=p["name"],x=str(60+i*d["laneWidth"]),width=str(d["laneWidth"]))
        for n in d["nodes"]:
            ne = ET.SubElement(el,"node",**{k:str(v) for k,v in n.items() if k != "text"})
            ne.text = n["text"]
        for e in d["edges"]:
            ET.SubElement(el,"edge",**{k:str(v) for k,v in e.items() if k != "points"},points=";".join(f"{x},{y}" for x,y in e["points"]))
        for i,text in enumerate(d["notes"]):
            note=ET.SubElement(el,"note",x="60",y=str(d["height"]+50+i*180),w=str(2000 if d["id"] == "03-implementation" else 1250),h="160")
            note.text=text
    ET.indent(root)
    ET.ElementTree(root).write(path,encoding="utf-8",xml_declaration=True)


if __name__ == "__main__":
    import sys
    export(Path(sys.argv[1]) if len(sys.argv)>1 else Path(__file__).with_name("diagrams.xml"))
