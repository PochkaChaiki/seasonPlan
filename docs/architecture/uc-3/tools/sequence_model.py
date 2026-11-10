"""Source-backed UC-3 interaction specification. Not application code or a VPP substitute."""
import hashlib
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]
JAVA = 'backend/src/main/java/com/seasonplan/lifecycle/'
PACKAGE_SOURCE = 'docs/architecture/packages/packages.xml'


def package_definitions():
    """Reuse the existing system-package view; do not invent service packages."""
    source = ET.parse(REPO/PACKAGE_SOURCE).getroot().find("diagram[@id='01-system']")
    selected = {'frontend': 'frontend', 'api': 'com.seasonplan.lifecycle.api',
                'security': 'com.seasonplan.security', 'service': 'com.seasonplan.lifecycle.service',
                'application': 'com.seasonplan.lifecycle.application', 'domain': 'com.seasonplan.lifecycle.domain',
                'persistence': 'com.seasonplan.lifecycle.persistence',
                'pollentree': 'com.seasonplan.lifecycle.adapter.pollentree'}
    original = {p.get('id'): p for p in source.findall('package')}
    result = [(key, 'frontend' if key == 'frontend' else original[ident].get('name'),
               original[ident].get('body'), ident) for key, ident in selected.items()]
    return result + [
        ('database', 'PostgreSQL', 'База данных: season_plans, magic_tasks, fairy_assignments, audit_log; целевой outbox. Не Java-пакет. Прямых записей пыльцы нет.', ''),
        ('provider', 'Pollen Tree', 'Внешняя система: HTTPS / JSON, API key или mTLS. Единственный владелец резерва/лимитов; не Java-пакет.', '')]

# id, owner, method, return, typed parameters, source-relative directory, exact source evidence
OPS = [
 ('apiReady','LifecycleController','markRitualReady','SeasonPlanResponse','UUID id','api','public SeasonPlanResponse markRitualReady('),
 ('facadeReady','SeasonPlanService','markRitualReady','SeasonPlan','UUID id;ActorRole role;String actorId','service','public SeasonPlan markRitualReady('),
 ('ready','PreparationService','markRitualReady','SeasonPlan','UUID id;ActorRole role;String actorId','application/preparation','public SeasonPlan markRitualReady('),
 ('assert','ReadinessChecklistService','assertReadyForRitual','void','UUID seasonPlanId','application/preparation','public void assertReadyForRitual('),
 ('calculate','ReadinessChecklistService','calculateChecklist','Map<String, List<String>>','UUID seasonPlanId','application/preparation','public Map<String, List<String>> calculateChecklist('),
 ('complete','ReadinessChecklistService','completeItem','void','UUID seasonPlanId;String key;ActorRole actorRole;String actorId','application/preparation','public void completeItem('),
 ('planFind','JdbcSeasonPlanRepository','findById','Optional<SeasonPlan>','UUID id','persistence','public Optional<SeasonPlan> findById('),
 ('planSave','JdbcSeasonPlanRepository','save','SeasonPlan','SeasonPlan plan','persistence','public SeasonPlan save('),
 ('planReady','SeasonPlan','markRitualReady','void','String actorId;Instant now','domain','public void markRitualReady('),
 ('planComplete','SeasonPlan','completeReadinessChecklistItem','void','String key;String actorId;Instant now','domain','public void completeReadinessChecklistItem('),
 ('request','PollenService','requestReservation','PollenReservation','UUID planId;UUID requestId;int requestedAmount;ActorRole role;String actorId','application/pollen','public PollenReservation requestReservation(UUID planId, UUID requestId,'),
 ('confirm','PollenService','confirmReservation','PollenReservation','UUID id;int confirmedAmount;String comment;ActorRole role;String actorId','application/pollen','public PollenReservation confirmReservation('),
 ('reject','PollenService','rejectReservation','PollenReservation','UUID id;String comment;ActorRole role;String actorId','application/pollen','public PollenReservation rejectReservation('),
 ('latest','PollenService','findLatestBySeasonPlanId','Optional<PollenReservation>','UUID planId','application/pollen','public Optional<PollenReservation> findLatestBySeasonPlanId('),
 ('capacity','PollenService','capacity','PollenCapacity','','application/pollen','public PollenCapacity capacity()'),
 ('clientCapacity','RestPollenTreeClient','capacity','PollenCapacity','','adapter/pollentree','public PollenCapacity capacity()'),
 ('clientLatest','RestPollenTreeClient','findLatestBySeasonPlanId','Optional<PollenReservation>','UUID planId','adapter/pollentree','public Optional<PollenReservation> findLatestBySeasonPlanId('),
 ('clientConfirm','RestPollenTreeClient','confirmReservation','PollenReservation','UUID id;int confirmedAmount;String comment;String actorId','adapter/pollentree','public PollenReservation confirmReservation('),
 ('clientReject','RestPollenTreeClient','rejectReservation','PollenReservation','UUID id;String comment;String actorId','adapter/pollentree','public PollenReservation rejectReservation('),
 ('audit','AuditLogService','append','AuditLogEntry','UUID seasonPlanId;AuditAction action;String actorId;String comment','application/shared','public AuditLogEntry append('),
 ('auditOnce','AuditLogService','appendOnce','AuditLogEntry','UUID eventId;UUID seasonPlanId;AuditAction action;String actorId;String comment','application/shared','public AuditLogEntry appendOnce('),
 ('auditRepo','JdbcAuditLogRepository','append','AuditLogEntry','AuditLogEntry entry','persistence','public AuditLogEntry append('),
 ('taskCreate','MagicTaskService','createTask','MagicTask','UUID seasonPlanId;MagicTask draft;ActorRole actorRole;String actorId','application/preparation','public MagicTask createTask('),
 ('taskUpdate','MagicTaskService','updateTask','MagicTask','UUID taskId;MagicTask draft;ActorRole actorRole;String actorId','application/preparation','public MagicTask updateTask('),
 ('taskValidate','MagicTaskService','validateTask','void','MagicTask task','application/preparation','private void validateTask('),
 ('taskMutate','MagicTask','updateFrom','void','MagicTask draft','domain','public void updateFrom('),
 ('taskSave','JdbcMagicTaskRepository','save','MagicTask','MagicTask task;String actorId','persistence','public MagicTask save('),
 ('taskFind','JdbcMagicTaskRepository','findById','Optional<MagicTask>','UUID id','persistence','public Optional<MagicTask> findById('),
 ('taskList','JdbcMagicTaskRepository','findBySeasonPlanId','List<MagicTask>','UUID seasonPlanId','persistence','public List<MagicTask> findBySeasonPlanId('),
 ('assign','FairyAssignmentService','assignFairy','FairyAssignment','UUID magicTaskId;String fairyId;String role;String talent;AssignmentStatus status;ActorRole actorRole;String actorId','application/preparation','public FairyAssignment assignFairy('),
 ('talent','FairyAssignmentService','validateTalentCompatibility','boolean','MagicTask task;String talent','application/preparation','public boolean validateTalentCompatibility('),
 ('conflict','FairySchedulePolicy','hasScheduleConflict','boolean','MagicTask task;String fairyId','application/preparation','public boolean hasScheduleConflict('),
 ('assignmentList','JdbcFairyAssignmentRepository','findByMagicTaskId','List<FairyAssignment>','UUID magicTaskId','persistence','public List<FairyAssignment> findByMagicTaskId('),
 ('fairyList','JdbcFairyAssignmentRepository','findConfirmedByFairyId','List<FairyAssignment>','String fairyId','persistence','public List<FairyAssignment> findConfirmedByFairyId('),
 ('assignmentSave','JdbcFairyAssignmentRepository','save','FairyAssignment','FairyAssignment assignment;String actorId','persistence','public FairyAssignment save('),
 ('transaction','JdbcCommandTransaction','execute','T','UUID seasonPlanId;Supplier<T> command','persistence','public <T> T execute('),
 ('throw','ValidationSupport','throwIfAny','void','Map<String, List<String>> errors','application/shared','public static void throwIfAny('),
]
PROPOSED = [
 ('amend','PollenService','requestAmendment','PollenReservation','UUID planId;UUID priorId;UUID requestId;BigDecimal grams'),
 ('clientAmend','RestPollenTreeClient','requestAmendment','PollenReservation','UUID planId;UUID priorId;UUID requestId;BigDecimal grams'),
 ('participation','ParticipationService','confirmParticipation','FairyAssignment','UUID assignmentId;Instant confirmedAt;CurrentActor actor'),
 ('rules','SeasonReadinessPolicy','evaluate','Map<String, List<String>>','SeasonPlan plan;List<MagicTask> tasks;List<FairyAssignment> assignments;PollenReservation reservation'),
 ('outbox','PreparationNotifications','enqueue','void','UUID planId;String event;Set<String> recipients'),
]
TYPE_PACKAGES = {
 'LifecycleController':'api','PreparationPage':'frontend','SeasonPlanService':'service',
 'PollenService':'application','RestPollenTreeClient':'pollentree',
 'MagicTaskService':'application','JdbcMagicTaskRepository':'persistence','MagicTask':'domain',
 'FairyAssignmentService':'application','FairySchedulePolicy':'application','JdbcFairyAssignmentRepository':'persistence','FairyAssignment':'domain','ParticipationService':'application',
 'PreparationService':'application','ReadinessChecklistService':'application','JdbcSeasonPlanRepository':'persistence','SeasonPlan':'domain','SeasonReadinessPolicy':'application','JdbcCommandTransaction':'persistence',
 'AuditLogService':'application','JdbcAuditLogRepository':'persistence','ValidationSupport':'application','PreparationNotifications':'application',
}


class Diagram:
    def __init__(self, root, key, name, lines, notes):
        self.e = ET.SubElement(root, 'diagram', id=key, name=name, notes=notes)
        self.y = 260
        self.count = 0
        self.last = None
        self.lines = {}
        for index, entry in enumerate(lines):
            ident, name, typ, pkg = entry[:4]
            kind = entry[4] if len(entry) > 4 else 'object'
            x = 120 + index * 370
            line = ET.SubElement(self.e, 'lifeline', id=ident, name=name, type=typ, package=pkg,
                                 kind=kind, x=str(x), y='170', w='290', h='0')
            self.lines[ident] = line
            if kind == 'actor':
                ET.SubElement(self.e, 'actor-symbol', id=key+':actor-type:'+ident, lifeline=ident,
                              x=str(x+125), y='110', w='40', h='50')
        self.e.set('width', str(120 + (len(lines)-1)*370 + 400))

    def msg(self, a, b, text, op='', operand='', kind='call', gap=76, reply_to=''):
        # Reserve a guard band when moving to another operand of an alt.
        previous = self.e.findall('message')
        if operand and previous and previous[-1].get('operand') != operand:
            self.y += 64
        self.count += 1
        ident = self.e.get('id') + '-m' + str(self.count)
        ET.SubElement(self.e, 'message', id=ident, **{'from':a, 'to':b, 'text':text,
            'operation':op, 'operand':operand, 'kind':kind, 'y':str(self.y), 'replyTo':reply_to})
        self.y += gap
        self.last = ident
        return ident

    def reply(self, a, b, text, call, operand=''):
        return self.msg(a,b,text,operand=operand,kind='reply',reply_to=call)

    def fragment(self, ident, operator, guards, parent=''):
        x, width = 65, int(self.e.get('width'))-100
        if parent:
            owner = next(f for f in self.e.findall('fragment') if any(o.get('id') == parent for o in f.findall('operand')))
            x, width = int(owner.get('x'))+20, int(owner.get('w'))-40
        f = ET.SubElement(self.e, 'fragment', id=ident, operator=operator, parent=parent, x=str(x), y=str(self.y-38),
                          w=str(width), h='0')
        for key, guard in guards:
            ET.SubElement(f, 'operand', id=key, guard=guard)
        self.y += 60
        return f

    def end(self, f):
        messages = self.e.findall('message')
        os = f.findall('operand')
        # Include nested break/alt contents in their containing operand's bounds.
        parent_of = {o.get('id'): child.get('parent') for child in self.e.findall('fragment')
                     for o in child.findall('operand')}
        def contained(m, operand):
            owner = m.get('operand')
            while owner:
                if owner == operand:
                    return True
                owner = parent_of.get(owner)
            return False
        start = int(f.get('y')) + 21  # VP anchors the first operand under the operator tab.
        for index, o in enumerate(os):
            owned = [m for m in messages if contained(m, o.get('id'))]
            if not owned:
                raise ValueError('empty operand ' + o.get('id'))
            if index+1 < len(os):
                following = [m for m in messages if contained(m, os[index+1].get('id'))]
                end = int(following[0].get('y')) - 100
            else:
                end = self.y - 10
            o.set('y', str(start));o.set('h', str(end-start));start = end
        f.set('h', str(self.y-int(f.get('y'))-10))
        self.y += 48

    def ref(self, target, text, covered):
        indexes = [list(self.lines).index(k) for k in covered]
        x = 120 + min(indexes)*370-25
        w = (max(indexes)-min(indexes))*370+340
        ET.SubElement(self.e, 'ref', target=target, text=text, covered=','.join(covered),
                      x=str(x), y=str(self.y-20), w=str(w), h='100')
        self.y += 150

    def finish(self):
        key = self.e.get('id')
        post = {'03a-resources': 'Resource snapshot / immutable provider decision and local audit; plan stays APPROVED.',
                '03b-tasks': 'Valid MagicTask saved with audit, or no local command changes on failure; plan stays APPROVED.',
                '03c-assignments': 'Valid assignment and participation saved with audit, or no command changes on failure; plan stays APPROVED.'}
        self.e.set('precondition', 'SeasonPlan APPROVED; authenticated authorized roles; current source data.')
        self.e.set('postcondition', post.get(key, 'On successful commit: RITUAL_READY iff every readiness rule passes; otherwise APPROVED; immutable audit. Ritual start excluded.'))
        self.e.set('height',str(self.y+210))
        for l in self.lines.values():
            l.set('h',str(self.y-170))
        self.executions()
        return self.e

    def executions(self):
        """Explicit bounded execution segments, never one bar across HTTP requests.

        A segment highlights processing at a send/receive (not elapsed method time).
        Merge only touching segments in the same operand; referenced interactions
        get segments on their covered participants. Actors have no execution bars.
        """
        for ident, line in self.lines.items():
            if line.get('kind') == 'actor':
                continue
            spans = []
            for m in self.e.findall('message'):
                if ident in (m.get('from'), m.get('to')):
                    start = int(m.get('y'))
                    finish = start + (48 if m.get('from') == m.get('to') else 30)
                    spans.append((start, finish, m.get('operand'), m))
            for ref in self.e.findall('ref'):
                if ident in ref.get('covered').split(','):
                    start = int(ref.get('y'))
                    spans.append((start, start+int(ref.get('h')), '', None))
            groups = []
            for start, finish, operand, message in sorted(spans, key=lambda item:item[0]):
                if groups and groups[-1][2] == operand and start <= groups[-1][1]:
                    groups[-1][1] = max(groups[-1][1], finish)
                    groups[-1][3].append(message)
                else:
                    groups.append([start, finish, operand, [message]])
            for index, (start, finish, operand, messages) in enumerate(groups):
                aid = self.e.get('id')+':execution:'+ident+':'+str(index+1)
                ET.SubElement(self.e, 'activation', id=aid, lifeline=ident, operand=operand,
                              x=str(int(line.get('x'))+int(line.get('w'))//2-4),
                              y=str(start), w='8', h=str(finish-start))
                for message in messages:
                    if message is not None:
                        for end in ('from', 'to'):
                            if message.get(end) == ident:
                                message.set(end+'Activation', aid)


def line(ident, typ, name=None, package=None):
    return (ident, name if name is not None else ident, typ, package or TYPE_PACKAGES[typ])


def actor(ident, name):
    return (ident,name,'','frontend','actor')


def build():
    root=ET.Element('uc3-sequences', version='1', scope='APPROVED → RITUAL_READY')
    ps=ET.SubElement(root,'packages')
    ps.set('source', PACKAGE_SOURCE)
    for ident,name,body,source in package_definitions():
        ET.SubElement(ps,'package',id=ident,name=name,body=body,source=source)
    operations=ET.SubElement(root,'operations')
    for key,owner,name,ret,params,directory,evidence in OPS:
        ET.SubElement(operations,'operation',id=key,owner=owner,name=name,returns=ret,parameters=params,
                      source=JAVA+directory+'/'+owner+'.java',evidence=evidence,status='existing',
                      package=TYPE_PACKAGES[owner])
    for key,owner,name,ret,params in PROPOSED:
        ET.SubElement(operations,'operation',id=key,owner=owner,name=name,returns=ret,parameters=params,
                      status='proposed',package=TYPE_PACKAGES[owner])

    d=Diagram(root,'01-use-case','1. UC-3 — Абстрактное описание',[
        actor('coordinator','Координатор талантов'), actor('guardian','Хранитель Древа Пыльцы'),
        actor('planner','Фея-планировщик'),
        line('system','SeasonPlanSystem','система','application'), line('tree','PollenTree','древо','provider')],
        'Предусловие: SeasonPlan APPROVED; роли авторизованы; сведения актуальны. Фея-планировщик читает результаты и корректирует план только в отдельном UC-1/UC-2.\nПостусловие: RITUAL_READY, но НЕ запуск ритуала. Древо — единственный владелец пыльцы. Каждый критический успех фиксируется в неизменяемом AuditLog. Целевой процесс по ST-00.')
    c=d.msg('coordinator','system','Открыть подготовку утверждённого сезона')
    d.msg('system','tree','Получить подтверждённый резерв и лимиты')
    d.reply('system','coordinator','Резерв, задачи, роли, доступность, чек-лист',c)
    f=d.fragment('u-amend','opt',[('u-amend-yes','Требуется уточнение резерва')])
    d.msg('guardian','system','Уточнить объём: создать связанный резерв-замену',operand='u-amend-yes')
    d.msg('system','tree','Проверить лимиты и запросить DRAFT; старый CONFIRMED неизменяем',operand='u-amend-yes')
    d.msg('guardian','system','Подтвердить либо отклонить замену с причиной',operand='u-amend-yes')
    d.msg('system','tree','Зафиксировать решение; без прямой записи в локальную БД',operand='u-amend-yes')
    d.end(f)
    f=d.fragment('u-work','loop',[('u-work-each','Для каждой задачи и требуемой роли; до устранения замечаний')])
    d.msg('coordinator','system','Создать / уточнить MagicTask: талант, срок, категория',operand='u-work-each')
    d.msg('coordinator','system','Назначить фею и роль; зафиксировать подтверждение участия',operand='u-work-each')
    d.msg('system','coordinator','Совместимость, роли, расписание; предупреждения и нехватка исполнителей',operand='u-work-each')
    d.end(f)
    d.msg('coordinator','system','Завершить обязательные задачи и условия; проверить готовность')
    f=d.fragment('u-result','alt',[('u-block','Есть дефицит / неподтверждённая замена / конфликт / невыполненный чек-лист'),('u-ok','else: все условия готовности выполнены')])
    d.msg('system','coordinator','Блокировать RITUAL_READY; причины; SeasonPlan остаётся APPROVED',operand='u-block')
    d.msg('system','planner','Показать замечания: корректировка через отдельное согласование',operand='u-block')
    d.msg('system','coordinator','Сохранить RITUAL_READY и неизменяемый аудит атомарно',operand='u-ok')
    d.end(f);d.finish()

    d=Diagram(root,'02-logical','2. UC-3 — Взаимодействие пакетов',[
        line('frontend','Frontend','frontend','frontend'),line('api','ApiSubsystem','api','api'),
        line('security','SecuritySubsystem','security','security'),line('service','ServiceSubsystem','service','service'),
        line('application','ApplicationSubsystem','application','application'),line('domain','DomainSubsystem','domain','domain'),
        line('persistence','PersistenceSubsystem','persistence','persistence'),line('pollentree','PollenTreeAdapter','pollentree','pollentree'),
        line('db','PostgreSQL','PostgreSQL','database'),line('tree','PollenTree','Pollen Tree','provider')],
        'Точные пакеты из packages/01-system: frontend (frontend/src), api, security, service, application, domain, persistence, pollentree. Акторов нет; первый запрос инициирует frontend.\napplication агрегирует preparation, pollen, shared и port как в системной диаграмме пакетов. Отдельных пакетов для сервисов, портов, fake и config нет. PostgreSQL и внешнее Pollen Tree — добавленные участники, не Java-пакеты.\nПроверки ST-00, подтверждение участия, атомарные записи и outbox — целевой процесс с явными пометками «проект». Древо и PostgreSQL не участвуют в общей распределённой транзакции.')
    opening=d.msg('frontend','api','GET: открыть подготовку APPROVED')
    d.msg('api','security','Проверить сессию и RBAC; получить CurrentActor')
    d.msg('api','service','Загрузить SeasonPlan через совместимый фасад')
    d.msg('service','application','Загрузить план / данные подготовки')
    d.msg('application','persistence','Прочитать план, задачи, назначения и чек-лист')
    d.msg('persistence','db','SELECT: актуальные локальные данные')
    d.msg('api','application','Получить ресурс через PollenService')
    d.msg('application','pollentree','PollenTreeClient: резерв и лимиты')
    d.msg('pollentree','tree','HTTPS / JSON: авторитетное состояние')
    d.reply('api','frontend','Карточка подготовки, резерв и чек-лист',opening)
    f=d.fragment('l-amend','opt',[('l-amend-yes','Запрошено уточнение ресурса')])
    d.msg('frontend','api','Команды хранителя: связанная замена; решение с причиной',operand='l-amend-yes')
    d.msg('api','application','«проект»: запросить / подтвердить / отклонить замену через PollenService',operand='l-amend-yes')
    d.msg('application','pollentree','«проект»: проверить лимит; запросить связанный DRAFT / решение',operand='l-amend-yes')
    d.msg('pollentree','tree','HTTPS: решение; прежний CONFIRMED неизменяем до supersession',operand='l-amend-yes')
    d.msg('application','application','AuditLogService: appendOnce с тем же eventId',operand='l-amend-yes')
    d.msg('application','persistence','Добавить событие решения; без локальной записи пыльцы',operand='l-amend-yes')
    d.msg('persistence','db','INSERT AuditLog; никаких UPDATE / DELETE аудита',operand='l-amend-yes')
    d.end(f)
    f=d.fragment('l-work','loop',[('l-each','Для каждой команды задачи / назначения / подтверждения участия')])
    d.msg('frontend','api','Создать / уточнить MagicTask; назначить фею / подтвердить участие',operand='l-each')
    d.msg('api','application','Проверить APPROVED, талант, срок, роли и расписание',operand='l-each')
    d.msg('application','persistence','Прочитать текущие задачи и подтверждённые назначения',operand='l-each')
    d.msg('persistence','db','SELECT: данные для проверки конфликтов',operand='l-each')
    d.msg('application','domain','Изменить допустимую задачу / назначение; «проект»: датированное участие',operand='l-each')
    d.msg('application','persistence','«проект»: атомарные save + audit (+ outbox при уведомлении)',operand='l-each')
    d.msg('persistence','db','«проект»: локальные изменения и INSERT AuditLog; COMMIT',operand='l-each')
    d.msg('api','frontend','Результат команды; ошибки не сохраняют недопустимые данные',operand='l-each')
    d.end(f)
    d.msg('frontend','api','POST /season-plans/{id}/mark-ritual-ready [отдельная команда]')
    d.msg('api','service','Запросить переход APPROVED → RITUAL_READY')
    d.msg('service','application','PreparationService: проверить готовность и выполнить переход')
    d.msg('application','persistence','«проект»: BEGIN, блокировка SeasonPlan; прочитать актуальные данные')
    d.msg('persistence','db','«проект»: lock / SELECT плана, задач, назначений и условий')
    d.msg('application','pollentree','PollenService: повторно проверить CONFIRMED и ожидающую замену')
    d.msg('pollentree','tree','HTTPS: актуальное состояние резерва')
    d.msg('application','application','«проект»: категории, COMPLETED, RITUAL_LEAD, подтверждения,\nотсутствие конфликтов / CRITICAL и дополнительные условия')
    f=d.fragment('l-result','alt',[('l-errors','Есть ошибки готовности'),('l-success','Нет ошибок готовности; все записи успешны'),('l-failure','Готовность пройдена; запись аудита бросает исключение')])
    d.msg('application','persistence','«проект»: rollback; статус APPROVED без изменений',operand='l-errors')
    d.msg('persistence','db','ROLLBACK',operand='l-errors')
    d.msg('application','api','Вернуть все ключи блокировки; нехватка ролей / ресурсов',operand='l-errors')
    d.msg('api','frontend','Ошибки готовности; исправить данные и повторить',operand='l-errors')
    d.msg('application','domain','Выполнить допустимый доменный переход; изменить статус в памяти',operand='l-success')
    d.msg('application','application','AuditLogService: добавить SEASON_MARKED_RITUAL_READY',operand='l-success')
    d.msg('application','persistence','«проект»: сохранить план, неизменяемый аудит и outbox',operand='l-success')
    d.msg('persistence','db','«проект»: UPDATE SeasonPlan + INSERT AuditLog / outbox; COMMIT',operand='l-success')
    d.msg('application','api','Результат после COMMIT; доставка уведомлений отдельно',operand='l-success')
    d.msg('api','frontend','SeasonPlanResponse RITUAL_READY',operand='l-success')
    d.msg('application','persistence','Попытка append SEASON_MARKED_RITUAL_READY',operand='l-failure')
    d.msg('persistence','db','INSERT AuditLog бросает исключение; переход не сохранён',operand='l-failure')
    d.msg('application','persistence','«проект»: rollback; ошибки других записей аналогичны',operand='l-failure')
    d.msg('persistence','db','ROLLBACK',operand='l-failure')
    d.msg('api','frontend','Ошибка записи; не возвращать успех',operand='l-failure')
    d.end(f);d.finish()

    d=Diagram(root,'03-implementation','3. UC-3 — Полное описание: композиция взаимодействий',[
        line('ui','PreparationPage','page'),line('api','LifecycleController','controller'),
        line('pollen','PollenService','pollen'),line('tasks','MagicTaskService','tasks'),
        line('assign','FairyAssignmentService','assignments'),line('ready','PreparationService','preparation')],
        'ref — реальные InteractionUse со ссылкой на подробную последовательность. Каждый ref раскрывается отдельной диаграммой 3a–3d; вместе это полное описание UC-3.\nПоказаны экземпляры классов, реальные операции и типизированные параметры в модели. «проект» обозначает отсутствующие методы или изменяемую обвязку, а не существующий код.\nAPI берёт CurrentActor из сессии, не из тела запроса; requiredRole проверяет координатора/хранителя. Фея-планировщик — только чтение в UC-3. Старт RitualSession вне границ.')
    d.msg('ui','api','GET /season-plans/{id}; /tasks; /readiness; /tasks/{taskId}/assignments')
    d.ref('03a-resources','3a. Обзор и уточнение ресурса; Хранитель Древа Пыльцы',['api','pollen'])
    d.msg('ui','api','POST /season-plans/{id}/tasks или PATCH /tasks/{id}')
    d.ref('03b-tasks','3b. MagicTask: createTask / updateTask; проверки и сохранение',['api','tasks'])
    d.msg('ui','api','POST /tasks/{id}/assignments; Подтверждение участия')
    d.ref('03c-assignments','3c. FairyAssignment, FairySchedulePolicy; роли и конфликты',['api','assign'])
    d.msg('ui','api','POST /season-plans/{id}/readiness-checklist/{key}/complete [опционально]')
    d.msg('ui','api','POST /season-plans/{id}/mark-ritual-ready [отдельная команда]')
    d.ref('03d-readiness','3d. Чек-лист; markRitualReady; аудит; целевая транзакция',['api','pollen','ready'])
    d.msg('api','ui','SeasonPlanResponse RITUAL_READY либо ошибки по ключам; не запуск ритуала')
    d.finish()

    d=Diagram(root,'03a-resources','3a. Реализация — обзор и ресурс пыльцы',[
        line('api','LifecycleController','controller'),line('plans','JdbcSeasonPlanRepository','plans'),
        line('pollen','PollenService','pollen'),line('client','RestPollenTreeClient','client'),
        line('audit','AuditLogService','audit')],
        'plans = SeasonPlanRepository → JdbcSeasonPlanRepository; client = PollenTreeClient → RestPollenTreeClient (fake заменяем через тот же контракт).\nКонтроллер вызывает SeasonPlanService.get и сервисы списков для карточки; фасад делегирует репозиторию. Чтение назначений повторяется для каждой задачи.\nСвязанные замены CONFIRMED, расчёт BigDecimal g scale=3 и supersession — «проект» ST-00: текущий requestReservation использует int и не связывает замену. HTTPS-вызовы только через PollenService/его расширение; локально хранится только аудит.\nСбой аудита после принятого Древом решения НЕ откатывает удалённый резерв: повтор appendOnce с детерминированным eventId; надёжное восстановление требует доработки.')
    d.msg('api','plans','findById(id) : Optional<SeasonPlan> [через SeasonPlanService.get]','planFind')
    c=d.msg('api','pollen','findLatestBySeasonPlanId(id)','latest')
    d.msg('pollen','client','findLatestBySeasonPlanId(planId) [HTTPS]','clientLatest')
    d.reply('pollen','api','Optional<PollenReservation>; status=CONFIRMED',c)
    f=d.fragment('r-amend','opt',[('r-amend-yes','Нужна корректировка объёма')])
    c=d.msg('api','pollen','«проект» requestAmendment(planId, priorId, requestId, grams)\nрасширение единой границы PollenService','amend','r-amend-yes')
    d.msg('pollen','client','capacity() : PollenCapacity; HTTPS: лимит Древа','clientCapacity','r-amend-yes')
    capacity_error=d.fragment('r-capacity-error','break',[('r-capacity-error-yes','Лимит превышен / capacity недоступна')],parent='r-amend-yes')
    d.msg('pollen','api','«проект»: ошибка ресурса; замена не запрошена, прежний резерв сохранён',operand='r-capacity-error-yes')
    d.end(capacity_error)
    d.msg('pollen','client','«проект» requestAmendment(planId, priorId, requestId, grams)\nHTTPS: связанный DRAFT создаёт Древо, не локальная БД','clientAmend','r-amend-yes')
    d.reply('pollen','api','Связанный DRAFT; прежний CONFIRMED сохранён; готовность блокируется',c,'r-amend-yes')
    d.end(f)
    decision_flow=d.fragment('r-decision-opt','opt',[('r-decision-requested','Есть DRAFT и хранитель принимает решение; иначе только чтение')])
    f=d.fragment('r-decision','alt',[('r-confirm','Подтверждение успешно; объём допустим'),('r-reject','Отклонение с причиной успешно'),('r-bad','Подтверждение отклонено лимитом или недоступностью Древа')],parent='r-decision-requested')
    c=d.msg('api','pollen','confirmReservation(id, amount, comment, POLLEN_GUARDIAN, actorId)','confirm','r-confirm')
    d.msg('pollen','client','confirmReservation(id, confirmedAmount, comment, actorId)','clientConfirm','r-confirm')
    d.msg('pollen','audit','appendOnce(eventId, planId, POLLEN_RESERVATION_CONFIRMED, actorId, comment)','auditOnce','r-confirm')
    d.reply('pollen','api','PollenReservation CONFIRMED; «проект»: атомарный supersession в Древе',c,'r-confirm')
    d.msg('api','pollen','rejectReservation(id, reason, POLLEN_GUARDIAN, actorId)','reject','r-reject')
    d.msg('pollen','client','rejectReservation(id, reason, actorId)','clientReject','r-reject')
    d.msg('pollen','audit','appendOnce(eventId, planId, POLLEN_RESERVATION_REJECTED, actorId, reason)','auditOnce','r-reject')
    d.msg('pollen','api','«проект»: отклонённая замена не отменяет прежний авторитетный CONFIRMED',operand='r-reject')
    d.msg('api','pollen','confirmReservation(id, amount, comment, POLLEN_GUARDIAN, actorId)','confirm','r-bad')
    d.msg('pollen','client','confirmReservation(id, amount, comment, actorId) → исключение','clientConfirm','r-bad')
    d.msg('pollen','api','Ошибка; без локального успешного аудита, RITUAL_READY не устанавливать',operand='r-bad')
    d.end(f);d.end(decision_flow);d.finish()

    d=Diagram(root,'03b-tasks','3b. Реализация — MagicTask',[
        line('api','LifecycleController','controller'),line('svc','MagicTaskService','tasks'),
        line('plans','JdbcSeasonPlanRepository','plans'),line('task','MagicTask','draft / existing'),
        line('repo','JdbcMagicTaskRepository','taskRepository'),line('audit','AuditLogService','audit')],
        'Пакеты соответствуют системному обзору: сервисы — application, доменные объекты — domain, JDBC-репозитории — persistence. Порты свёрнуты в application. Инъекции: SeasonPlanRepository → plans; MagicTaskRepository → taskRepository.\ncreateTask проверяет APPROVED, title / requiredTalent / start < end; UUID и seasonPlanId задаются перед save. updateTask сейчас НЕ проверяет статус плана, начатость и неизменяемость завершённой задачи и НЕ пишет аудит — целевые изменения явно отмечены.\nНовая задача по текущему коду получает READY, если статус null. Обязательная категория и COMPLETED обязательных работ — целевые данные/правила ST-00, не доказательство наличия полей в исходниках.\nКаждая критическая команда требует транзакционной обвязки сохранение + аудит (пока отсутствует). Ошибка валидации: 400; нет объекта: 404; роль: 403. После ошибки UI исправляет запрос.')
    f=d.fragment('t-command','alt',[('t-create','Создание'),('t-update','Изменение существующей задачи')])
    c=d.msg('api','svc','createTask(planId, request.toDraft(), TALENT_COORDINATOR, actorId)','taskCreate','t-create')
    d.msg('svc','plans','findById(planId); status должен быть APPROVED','planFind','t-create')
    d.msg('svc','svc','validateTask(draft); title, requiredTalent, timeWindow','taskValidate','t-create')
    c=d.msg('api','svc','updateTask(taskId, request.toDraft(), TALENT_COORDINATOR, actorId)','taskUpdate','t-update')
    d.msg('svc','repo','findById(taskId) : Optional<MagicTask>','taskFind','t-update')
    d.msg('svc','svc','validateTask(draft); «проект»: проверить APPROVED / unstarted / stale','taskValidate','t-update')
    d.end(f)
    f=d.fragment('t-invalid','break',[('t-invalid-yes','Проверка выбранной команды не пройдена / объект не найден / нет прав')])
    d.msg('svc','api','Ошибка 400 / 403 / 404; без мутации, save и успешного ответа',operand='t-invalid-yes')
    d.end(f)
    d.msg('svc','task','updateFrom(draft) для update; default READY при create status=null;\nдля create: setId(UUID), setSeasonPlanId(planId)','taskMutate')
    f=d.fragment('t-persist','alt',[('t-saved','save и audit завершаются нормально'),
        ('t-save-failed','save бросает исключение'),('t-audit-failed','save успешен, audit бросает исключение')])
    d.msg('svc','repo','save(task, actorId) : MagicTask [JDBC upsert]','taskSave','t-saved')
    d.msg('svc','audit','append(planId, MAGIC_TASK_CREATED, actorId, title);\n«проект» audit для updateTask в той же транзакции','audit','t-saved')
    d.msg('svc','api','«проект»: COMMIT; MagicTask → MagicTaskResponse',operand='t-saved')
    d.msg('svc','repo','save(task, actorId) → исключение','taskSave','t-save-failed')
    d.msg('svc','api','«проект»: rollback; ошибка записи; нет успешного ответа',operand='t-save-failed')
    d.msg('svc','repo','save(task, actorId) : MagicTask','taskSave','t-audit-failed')
    d.msg('svc','audit','append(planId, action, actorId, details) → исключение','audit','t-audit-failed')
    d.msg('svc','api','«проект»: rollback включая задачу; нет успешного ответа',operand='t-audit-failed')
    d.end(f);d.finish()

    d=Diagram(root,'03c-assignments','3c. Реализация — назначения и расписание',[
        line('api','LifecycleController','controller'),line('svc','FairyAssignmentService','assignments'),
        line('schedule','FairySchedulePolicy','schedule'),line('tasks','JdbcMagicTaskRepository','tasks'),
        line('repo','JdbcFairyAssignmentRepository','assignmentRepository'),line('participation','ParticipationService','participation «проект»'),line('audit','AuditLogService','audit')],
        'assignFairy: роль координатора; идентификатор феи / роль обязательны; талант совпадает; проверка расписания только при CONFIRMED. Текущий hasScheduleConflict ограничен тем же SeasonPlan; межсезонная проверка требует расширения.\nЦикл проверки конфликтов читает MagicTask для каждого подтверждённого назначения феи и сравнивает TimeWindow.overlaps. При несовместимости/конфликте ValidationSupport.throwIfAny бросает DomainValidationException до save.\nТекущий API позволяет координатору передать статус CONFIRMED, не хранит confirmedAt и не имеет команды самостоятельного подтверждения. ParticipationService — явно проектируемый контракт, не существующий класс.\nЦель: атомарные save + аудит + outbox, защита устаревших назначений и подтверждение участия. При нехватке ролей блокируется готовность, предупреждение не заменяет проверку. Уведомления FR-88/89 требуют outbox/планировщика.')
    d.msg('api','svc','assignFairy(taskId, fairyId, role, talent, status, TALENT_COORDINATOR, actorId)','assign')
    d.msg('svc','tasks','findById(taskId) : Optional<MagicTask>','taskFind')
    d.msg('svc','svc','validateTalentCompatibility(task, talent) : boolean','talent')
    f=d.fragment('a-conflict','opt',[('a-confirmed','status=CONFIRMED')])
    d.msg('svc','schedule','hasScheduleConflict(task, fairyId)','conflict','a-confirmed')
    d.msg('schedule','repo','findConfirmedByFairyId(fairyId)','fairyList','a-confirmed')
    d.msg('schedule','tasks','findById(assignment.magicTaskId()) для каждого назначения','taskFind','a-confirmed')
    d.msg('schedule','svc','boolean: тот же план и пересечение окон; «проект» межсезонная проверка',operand='a-confirmed')
    d.end(f)
    f=d.fragment('a-result','alt',[('a-invalid','talent mismatch / пустые поля / конфликт расписания'),('a-valid','else: данные допустимы')])
    d.msg('svc','api','DomainValidationException(errors); ничего не сохранять',operand='a-invalid')
    d.msg('svc','repo','save(new FairyAssignment(...), actorId)','assignmentSave','a-valid')
    d.msg('svc','audit','append(planId, FAIRY_ASSIGNED, actorId, fairyId)','audit','a-valid')
    d.msg('svc','api','FairyAssignment → FairyAssignmentResponse',operand='a-valid')
    d.end(f)
    f=d.fragment('a-participation','opt',[('a-pending','Назначение создано, но участие ещё не подтверждено')])
    d.msg('api','participation','«проект» confirmParticipation(assignmentId, confirmedAt, currentActor)','participation','a-pending')
    d.msg('participation','schedule','hasScheduleConflict(task, fairyId) при подтверждении','conflict','a-pending')
    conflict=d.fragment('a-confirm-conflict','break',[('a-confirm-conflict-yes','conflict=true: прекратить подтверждение без save')],parent='a-pending')
    d.msg('participation','api','«проект»: ошибка конфликта; участие НЕ подтверждено; никаких записей',operand='a-confirm-conflict-yes')
    d.end(conflict)
    d.msg('participation','repo','«проект» save(assignment CONFIRMED + confirmedAt, actorId)','assignmentSave','a-pending')
    d.msg('participation','audit','«проект» append(planId, action, actorId, confirmationEvidence)','audit','a-pending')
    d.end(f);d.finish()

    d=Diagram(root,'03d-readiness','3d. Реализация — чек-лист и RITUAL_READY',[
        line('api','LifecycleController','controller'),line('facade','SeasonPlanService','facade'),
        line('prep','PreparationService','preparation'),line('check','ReadinessChecklistService','checklist'),
        line('repo','JdbcSeasonPlanRepository','plans'),line('pollen','PollenService','pollen'),
        line('tasks','JdbcMagicTaskRepository','tasks'),line('assign','JdbcFairyAssignmentRepository','assignments'),
        line('rules','SeasonReadinessPolicy','rules «проект»'),line('plan','SeasonPlan','plan'),
        line('audit','AuditLogService','audit'),line('tx','JdbcCommandTransaction','transaction «проект: обвязка»')],
        'Типы репозиториев — реальные JDBC-реализации инъецируемых интерфейсов. У каждого сервиса/домена своя линия жизни; рекурсия calculateChecklist показана self-call.\nТекущий calculateChecklist: latest.status=CONFIRMED, tasks непусты и все READY; на каждую задачу хотя бы одно CONFIRMED с подходящим талантом; все ручные ChecklistItem complete.\n«проект» расширение calculateChecklist заменяет READY-предикат на завершённость обязательных задач по каталогу; проверяет категории, RITUAL_LEAD и сезонные роли, подтверждения с датой, конфликты, CRITICAL, условия approval и незавершённые замены. Нет ручного обхода правил.\nJdbcCommandTransaction.execute существует, но UC-3 его не вызывает. Ниже показана целевая обвязка: блокировка плана, аудит + save + outbox в одной транзакции; rollback при исключении. Нет атомарности с удалённым Древом.\ncompleteItem сейчас сохраняет ручную отметку без аудита; целевой аудит/доказательства требуют изменения. Существующий PreparationService допускает POLLEN_GUARDIAN, хотя API/ST-00 разрешают переход только координатору; целевая проверка должна совпадать с API.')
    f=d.fragment('c-item','opt',[('c-item-yes','Есть подтверждённые доказательства для ручного пункта')])
    d.msg('api','check','completeItem(planId, key, actorRole, actorId)','complete','c-item-yes')
    d.msg('check','repo','findById(planId)','planFind','c-item-yes')
    d.msg('check','plan','completeReadinessChecklistItem(key, actorId, clock.instant())','planComplete','c-item-yes')
    d.msg('check','repo','save(plan); «проект»: аудит доказательств в этой же транзакции','planSave','c-item-yes')
    d.end(f)
    d.msg('api','facade','markRitualReady(id, TALENT_COORDINATOR, actorId)','facadeReady')
    d.msg('facade','tx','«проект» execute(planId, command) — обвязка UC-3; BEGIN + lock','transaction')
    d.msg('tx','prep','markRitualReady(id, role, actorId) [внутри command]','ready')
    d.msg('prep','repo','findById(id); «проект»: только APPROVED и TALENT_COORDINATOR','planFind')
    d.msg('prep','check','assertReadyForRitual(id)','assert')
    d.msg('check','check','calculateChecklist(seasonPlanId)','calculate')
    d.msg('check','repo','findById(seasonPlanId)','planFind')
    d.msg('check','pollen','findLatestBySeasonPlanId(seasonPlanId); authoritative CONFIRMED','latest')
    d.msg('check','tasks','findBySeasonPlanId(seasonPlanId)','taskList')
    f=d.fragment('c-each','loop',[('c-each-task','Для каждого MagicTask')])
    d.msg('check','assign','findByMagicTaskId(task.id()); CONFIRMED + подходящий talent','assignmentList','c-each-task')
    d.end(f)
    d.msg('check','rules','«проект» evaluate(plan, tasks, assignments, reservation)\nCOMPLETED обязательных задач; категории; роли; CRITICAL; условия','rules')
    f=d.fragment('c-result','alt',[('c-errors','errors не пусты / нет ресурса / нехватка ролей / конфликт'),('c-ok','Нет ошибок готовности; все записи успешны'),('c-write','Нет ошибок готовности; audit.append бросает исключение'),('c-save-error','Нет ошибок готовности; аудит успешен, plans.save бросает исключение')])
    d.msg('check','prep','DomainValidationException: все ключи; без markRitualReady/save',operand='c-errors')
    d.msg('tx','facade','«проект»: rollback; SeasonPlan остаётся APPROVED',operand='c-errors')
    d.msg('prep','plan','markRitualReady(actorId, clock.instant())','planReady','c-ok')
    d.msg('prep','audit','append(id, SEASON_MARKED_RITUAL_READY, actorId, null)','audit','c-ok')
    d.msg('prep','repo','save(plan) : SeasonPlan','planSave','c-ok')
    d.msg('tx','facade','«проект»: COMMIT после save + audit (+ outbox при уведомлении)',operand='c-ok')
    d.msg('facade','api','SeasonPlan → SeasonPlanResponse(status=RITUAL_READY)',operand='c-ok')
    d.msg('prep','plan','markRitualReady(actorId, clock.instant()) [изменение только в памяти]','planReady','c-write')
    d.msg('prep','audit','append(id, SEASON_MARKED_RITUAL_READY, actorId, null) → исключение','audit','c-write')
    d.msg('tx','api','«проект»: rollback; без save и успешного ответа',operand='c-write')
    d.msg('prep','plan','markRitualReady(actorId, clock.instant()) [изменение только в памяти]','planReady','c-save-error')
    d.msg('prep','audit','append(id, SEASON_MARKED_RITUAL_READY, actorId, null)','audit','c-save-error')
    d.msg('prep','repo','save(plan) → исключение','planSave','c-save-error')
    d.msg('tx','api','«проект»: rollback включая аудит; вернуть ошибку',operand='c-save-error')
    d.end(f);d.finish()
    return root


def source_inventory():
    paths = list((REPO / (JAVA)).rglob('*.java')) + [REPO / p for p in [
        'arch_docs/USE_CASE_SPECIFICATION.md','arch_docs/SYSTEM_REQUIREMENTS_SPECIFICATION.md',
        'arch_docs/VISION.md','docs/architecture/ST-00-specification-resolution.md',
        'docs/architecture/packages/README.md',PACKAGE_SOURCE,'frontend/src/features/preparation/PreparationPage.jsx']]
    return [{'path':p.relative_to(REPO).as_posix(),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
            for p in sorted(paths)]


def main(out):
    out=Path(out);out.mkdir(parents=True,exist_ok=True)
    root=build();ET.indent(root)
    ET.ElementTree(root).write(out/'sequences.xml',encoding='utf-8',xml_declaration=True)
    (out/'source-manifest.json').write_text(json.dumps({'scope':'UC-3; target design with explicit existing/proposed distinctions',
        'logicalPackageSource':PACKAGE_SOURCE,'sources':source_inventory()},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


if __name__ == '__main__':
    main(sys.argv[1])
