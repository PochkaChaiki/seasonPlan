"""Source-backed UML2 communication specification; no application dependencies."""
import hashlib
import json
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]
JAVA = 'backend/src/main/java/com/seasonplan/lifecycle/'
PACKAGE_SOURCE = 'docs/architecture/packages/packages.xml'
DOT = r'X:\apps\graphviz\dot.exe'

# id, classifier, method, return, parameters, relative source, exact evidence
OPS = [
 ('generate','CompletionService','generate','SeasonReport','UUID planId;String title;ActorRole role;String actorId','application/completion/CompletionService.java','public SeasonReport generate('),
 ('approveComplete','CompletionService','approveAndComplete','SeasonReport','UUID planId;UUID reportId;ActorRole role;String actorId','application/completion/CompletionService.java','public SeasonReport approveAndComplete('),
 ('archive','CompletionService','archive','SeasonPlan','UUID planId;ActorRole role;String actorId','application/completion/CompletionService.java','public SeasonPlan archive('),
 ('view','CompletionService','view','Optional<SeasonReport>','UUID planId','application/completion/CompletionService.java','public Optional<SeasonReport> view('),
 ('export','CompletionService','export','CompletionService.Export','UUID planId;CompletionService.Format format','application/completion/CompletionService.java','public Export export('),
 ('snapshot','CompletionService','snapshot','SeasonReport.Snapshot','SeasonPlan plan','application/completion/CompletionService.java','private SeasonReport.Snapshot snapshot('),
 ('approved','CompletionService','approved','SeasonReport','UUID planId','application/completion/CompletionService.java','private SeasonReport approved('),
 ('planFind','SeasonPlanRepository','findById','Optional<SeasonPlan>','UUID id','application/port/SeasonPlanRepository.java','Optional<SeasonPlan> findById('),
 ('planSave','SeasonPlanRepository','save','SeasonPlan','SeasonPlan plan','application/port/SeasonPlanRepository.java','SeasonPlan save('),
 ('transaction','CommandTransaction','execute','T','UUID seasonPlanId;Supplier<T> command','application/port/CommandTransaction.java','<T> T execute('),
 ('nextVersion','CompletionPorts.Reports','nextVersion','int','UUID planId','application/port/CompletionPorts.java','int nextVersion('),
 ('saveDraft','CompletionPorts.Reports','saveDraft','void','SeasonReport report','application/port/CompletionPorts.java','void saveDraft('),
 ('latestDraft','CompletionPorts.Reports','latestDraft','Optional<SeasonReport>','UUID planId','application/port/CompletionPorts.java','Optional<SeasonReport> latestDraft('),
 ('latestApproved','CompletionPorts.Reports','latestApproved','Optional<SeasonReport>','UUID planId','application/port/CompletionPorts.java','Optional<SeasonReport> latestApproved('),
 ('appendApproved','CompletionPorts.Reports','appendApproved','void','SeasonReport report','application/port/CompletionPorts.java','void appendApproved('),
 ('requiredResults','CompletionPorts.Tasks','requiredResults','List<SeasonReport.TaskResult>','UUID planId','application/port/CompletionPorts.java','List<SeasonReport.TaskResult> requiredResults('),
 ('catalog','CompletionPorts.CatalogVersion','forPlan','String','UUID planId','application/port/CompletionPorts.java','String forPlan('),
 ('render','CompletionPorts.Exporter','render','byte[]','SeasonReport approved;String format','application/port/CompletionPorts.java','byte[] render('),
 ('metricList','ExecutionPorts.Metrics','forPlan','List<ClimateMetric>','UUID planId','application/port/ExecutionPorts.java','List<ClimateMetric> forPlan('),
 ('incidentList','ExecutionPorts.Incidents','forPlan','List<MagicIncident>','UUID planId','application/port/ExecutionPorts.java','List<MagicIncident> forPlan('),
 ('actionList','ExecutionPorts.Corrections','forPlan','List<CorrectionAction>','UUID planId','application/port/ExecutionPorts.java','List<CorrectionAction> forPlan('),
 ('ritualList','ExecutionPorts.Rituals','forPlan','List<RitualSession>','UUID planId','application/port/ExecutionPorts.java','List<RitualSession> forPlan('),
 ('usage','PollenService','usage','Optional<PollenUsage>','UUID planId','application/pollen/PollenService.java','public Optional<PollenUsage> usage('),
 ('summary','PollenTreeClient','summary','Optional<PollenTreeClient.Summary>','UUID planId','application/port/PollenTreeClient.java','Optional<Summary> summary('),
 ('blockers','SeasonReport.Snapshot','blockers','List<String>','','domain/SeasonReport.java','public List<String> blockers()'),
 ('kpis','SeasonReport.Snapshot','kpis','SeasonReport.Kpis','','domain/SeasonReport.java','public Kpis kpis()'),
 ('reportApprove','SeasonReport','approve','SeasonReport','String actorId;Instant now','domain/SeasonReport.java','public SeasonReport approve('),
 ('planComplete','SeasonPlan','complete','void','SeasonReport report;String actorId;Instant now','domain/SeasonPlan.java','public void complete('),
 ('planArchive','SeasonPlan','archive','void','SeasonReport report;String actorId;Instant now','domain/SeasonPlan.java','public void archive('),
 ('audit','AuditLogService','append','AuditLogEntry','UUID seasonPlanId;AuditAction action;String actorId;String comment','application/shared/AuditLogService.java','public AuditLogEntry append('),
]
INTERFACES = {'SeasonPlanRepository','CommandTransaction','CompletionPorts.Reports','CompletionPorts.Tasks',
              'CompletionPorts.CatalogVersion','CompletionPorts.Exporter','ExecutionPorts.Metrics',
              'ExecutionPorts.Incidents','ExecutionPorts.Corrections','ExecutionPorts.Rituals','PollenTreeClient'}


def package_definitions():
    source = ET.parse(REPO / PACKAGE_SOURCE).getroot().find("diagram[@id='01-system']")
    canonical = {p.get('id'): p for p in source.findall('package')}
    selected = [('frontend','frontend'),('api','com.seasonplan.lifecycle.api'),
                ('security','com.seasonplan.security'),('application','com.seasonplan.lifecycle.application'),
                ('domain','com.seasonplan.lifecycle.domain'),('persistence','com.seasonplan.lifecycle.persistence'),
                ('pollentree','com.seasonplan.lifecycle.adapter.pollentree')]
    return [(key, 'frontend' if key == 'frontend' else canonical[ident].get('name'), ident)
            for key, ident in selected] + [('provider','Pollen Tree','')]


def op_package(owner):
    return 'domain' if owner in {'SeasonReport','SeasonReport.Snapshot','SeasonPlan'} else 'application'


class Diagram:
    def __init__(self, root, key, name, view, pre, post, notes):
        self.e = ET.SubElement(root, 'diagram', id=key, name=name, view=view,
                               precondition=pre, postcondition=post, notes=notes)
        self.links = {}

    def participant(self, key, name, typ, pkg='application', kind='object', status='existing'):
        ET.SubElement(self.e, 'participant', id=key, name=name, type=typ, package=pkg,
                      kind=kind, status=status)

    def msg(self, number, a, b, text, op='', guard='', iteration=False, detail='', reply_to=''):
        pair = tuple(sorted((a,b)))
        if pair not in self.links:
            key = self.e.get('id') + ':link:' + ':'.join(pair)
            is_actor_b=any(p.get('id')==b and p.get('kind')=='actor' for p in self.e.findall('participant'))
            self.links[pair] = ET.SubElement(self.e, 'link', id=key,
                                            **{'from':b if is_actor_b else a,'to':a if is_actor_b else b})
        link = self.links[pair]
        if reply_to and guard:
            text='['+guard+'] '+text  # VP does not render Return.recurrenceCondition in this view.
        ET.SubElement(self.e, 'message', id=self.e.get('id')+':m:'+number,
                      number=number, **{'from':a,'to':b,'text':text,'operation':op,
                      'guard':guard,'iteration':str(iteration).lower(),
                      'link':link.get('id'),'detail':detail,
                      'kind':'reply' if reply_to else 'call',
                      'replyTo':self.e.get('id')+':m:'+reply_to if reply_to else ''})

    def finish(self):
        layout(self.e)
        return self.e


def _point_inside(p, r, pad=0):
    x,y,w,h = r
    return x-pad < p[0] < x+w+pad and y-pad < p[1] < y+h+pad


def _overlap(a,b):
    x,y,w,h=a; xx,yy,ww,hh=b
    return x < xx+ww and xx < x+w and y < yy+hh and yy < y+h


def rect(e):
    return tuple(int(e.get(a)) for a in ('x','y','w','h'))


def layout(diagram):
    """Graphviz reserves a real label block; native messages occupy its rows."""
    lines = ['digraph communication {',
             'graph [rankdir=LR, splines=ortho, ranksep=1.1, nodesep=0.7, pad=0.3, bgcolor=white];',
             'node [shape=box, fixedsize=true, width=5.4, height=1.15, fontname=Arial];',
             'edge [arrowhead=none, arrowtail=none];']
    people = diagram.findall('participant')
    for p in people:
        lines.append(json.dumps(p.get('id'))+' [label='+json.dumps(p.get('name'))+'];')
    blocks = {}
    for link in diagram.findall('link'):
        messages = [m for m in diagram.findall('message') if m.get('link') == link.get('id')]
        heights=[]
        for m in messages:
            # Number/guard are native properties, not duplicated in IMessage.name.
            # VP centers multiline names over the arrow, crossing their lower row.
            # Prefer a wider, single-line native caption rather than faux wrapping.
            label = m.get('text').replace('\n',' ')
            m.set('text', label)
            rows = 1 + (1 if m.get('guard') else 0)
            heights.append(max(55, rows*25+22))
        longest=max(max(map(len,m.get('text').splitlines()))+len(m.get('guard'))+
                    len(m.get('number'))+(len(m.get('operation'))+4 if m.get('operation') else 0) for m in messages)
        width=max(720,round(longest*8.5)+80)
        height=sum(heights)+16
        blocks[link.get('id')]=(messages,width,height,heights)
        label=f'<<TABLE BORDER="0" CELLBORDER="0" CELLPADDING="0" FIXEDSIZE="TRUE" WIDTH="{width}" HEIGHT="{height}"><TR><TD> </TD></TR></TABLE>>'
        if link.get('from') == link.get('to'):
            dummy='self_'+link.get('from')
            lines.append(json.dumps(dummy)+f' [label="", width={width/72}, height={height/72}];')
            lines.append(json.dumps(link.get('from'))+' -> '+json.dumps(dummy)+
                         ' [id='+json.dumps(link.get('id'))+'];')
        else:
            lines.append(json.dumps(link.get('from'))+' -> '+json.dumps(link.get('to'))+
                         ' [id='+json.dumps(link.get('id'))+', label='+label+'];')
    lines.append('}')
    with tempfile.TemporaryDirectory(prefix='uc5-layout-') as tmp:
        dot=Path(tmp)/'input.dot'; dot.write_text('\n'.join(lines),encoding='utf-8')
        result=subprocess.run([DOT,'-Tjson',str(dot)],check=True,capture_output=True,encoding='utf-8')
    data=json.loads(result.stdout)
    _,_,width,height=map(float,data['bb'].split(','))
    ox,oy=100,170
    for item in data['objects']:
        if item['name'].startswith('self_'): continue
        p=next(p for p in people if p.get('id') == item['name'])
        cx,cy=map(float,item['pos'].split(',')); w=round(float(item['width'])*72); h=round(float(item['height'])*72)
        for key,value in zip(('x','y','w','h'),(round(cx-w/2)+ox,round(height-cy-h/2)+oy,w,h)):
            p.set(key,str(value))
    for edge in data['edges']:
        link=next(l for l in diagram.findall('link') if l.get('id') == edge['id'])
        points=[]
        for drawing in edge['_draw_']:
            if drawing['op']=='b':
                for x,y in drawing['points']:
                    p=(round(x)+ox,round(height-y)+oy)
                    if not points or points[-1]!=p: points.append(p)
        cleaned=[]
        for p in points:
            while len(cleaned)>1 and ((cleaned[-2][0]==cleaned[-1][0]==p[0]) or (cleaned[-2][1]==cleaned[-1][1]==p[1])):
                cleaned.pop()
            cleaned.append(p)
        if link.get('from') == link.get('to'):
            # A layout-only dummy reserves the loop caption, never a UML participant.
            owner=next(p for p in people if p.get('id')==link.get('from'))
            px,py,pw,ph=rect(owner)
            first=(px+pw,py+ph//2-12)
            far=px+pw+20
            cleaned=[first,(far,first[1]),(far,first[1]+24),(first[0],first[1]+24)]
            item=next(o for o in data['objects'] if o['name']=='self_'+link.get('from'))
            cx,cy=map(float,item['pos'].split(','))
        else:
            cx,cy=map(float,edge['lp'].split(','))
        link.set('points',';'.join(','.join(map(str,p)) for p in cleaned))
        messages,bw,bh,heights=blocks[link.get('id')]
        x=round(cx-bw/2)+ox; y=round(height-cy-bh/2)+oy
        for m,mh in zip(messages,heights):
            m.set('reverse',str(m.get('from')!=link.get('from')).lower())
            for key,value in zip(('x','y','w','h'),(x,y,bw,mh)):
                m.set(key,str(value))
            y+=mh
    # dot's ortho router does not reserve edge-label obstacles. Repair two
    # constrained package-view corridors explicitly, then regression-check them.
    if diagram.get('id')=='02-logical':
        by_id={p.get('id'):p for p in people}
        for link in diagram.findall('link'):
            if {link.get('from'),link.get('to')}=={'api','security'}:
                ax,ay,aw,ah=rect(by_id['api']);sx,sy,sw,sh=rect(by_id['security'])
                points=[(ax+aw//2,ay),(ax+aw//2,sy+sh//2),(sx,sy+sh//2)]
                link.set('points',';'.join(','.join(map(str,p)) for p in points))
            if {link.get('from'),link.get('to')}=={'application','domain'}:
                ax,ay,aw,ah=rect(by_id['application']);dx,dy,dw,dh=rect(by_id['domain'])
                points=[(ax+aw,ay+ah//3),(dx-20,ay+ah//3),(dx-20,dy+dh//2),(dx,dy+dh//2)]
                link.set('points',';'.join(','.join(map(str,p)) for p in points))
    diagram.set('width',str(round(width)+2*ox))
    diagram.set('height',str(round(height)+oy+430))
    validate_geometry(diagram)


def validate_geometry(d):
    people={p.get('id'):rect(p) for p in d.findall('participant')}
    items=list(people.items())
    for i,(key,r) in enumerate(items):
        assert r[2]>0 and r[3]>0
        for other,s in items[i+1:]: assert not _overlap(r,s),(d.get('id'),key,other)
    messages=d.findall('message')
    for i,m in enumerate(messages):
        for key,r in items:
            assert not _overlap(rect(m),r),(d.get('id'),m.get('id'),'caption-node',key)
        for n in messages[i+1:]:
            assert not _overlap(rect(m),rect(n)),(d.get('id'),m.get('id'),'caption-caption',n.get('id'))
    for link in d.findall('link'):
        pts=[tuple(map(int,p.split(','))) for p in link.get('points').split(';')]
        assert len(set(pts))>=2
        for a,b in zip(pts,pts[1:]):
            assert a[0]==b[0] or a[1]==b[1],('nonorthogonal',pts)
            for key,r in items:
                if key in (link.get('from'),link.get('to')): continue
                for step in range(21):
                    p=(a[0]+(b[0]-a[0])*step/20,a[1]+(b[1]-a[1])*step/20)
                    assert not _point_inside(p,r),(d.get('id'),link.get('id'),'route-node',key)


def build():
    root=ET.Element('uc5-communications',version='1',scope='ACTIVE → COMPLETED → ARCHIVED')
    packages=ET.SubElement(root,'packages',source=PACKAGE_SOURCE)
    for key,name,source in package_definitions():
        ET.SubElement(packages,'package',id=key,name=name,source=source)
    operations=ET.SubElement(root,'operations')
    for key,owner,name,returns,params,source,evidence in OPS:
        ET.SubElement(operations,'operation',id=key,owner=owner,name=name,returns=returns,parameters=params,
                      source=JAVA+source,evidence=evidence,status='existing',package=op_package(owner),
                      visibility='private' if key in {'snapshot','approved'} else 'public',
                      interface=str(owner in INTERFACES).lower())

    d=Diagram(root,'01-use-case','1. UC-5 — Абстрактное описание','use-case',
        'SeasonPlan ACTIVE; пользователи авторизованы; завершение требует успешного ритуала и обязательных работ.',
        'После подтверждения результатов: COMPLETED и неизменяемый утверждённый отчёт; при блокировках ACTIVE. Архивирование — отдельное расширение.',
        'UML 2 Communication: номера задают порядок; [условие] — guard, * — повтор. Это не диаграмма последовательности: вертикальная координата не означает время.\n'
        'Фея-планировщик уточняет только представление отчёта своего сезона; работы и инциденты исправляются в UC-4. Неполные данные допускают черновик с замечаниями, но запрещают завершение.\n'
        'Главный хранитель подтверждает результаты и отдельно архивирует. Пыльца читается только через Древо; новые расходы здесь не регистрируются. Все критические успешные команды сопровождаются неизменяемым аудитом.\n'
        'ST-00: ровно восемь статусов SeasonPlan; критическое состояние — условие, не дополнительный статус. Экспорт — только из утверждённой версии.')
    d.participant('chief','Главный хранитель','Главный хранитель','frontend','actor')
    d.participant('planner','Фея-планировщик','Фея-планировщик','frontend','actor')
    d.participant('system','система','SeasonPlanSystem')
    d.participant('tree','Древо Пыльцы','PollenTree','provider')
    d.msg('1','chief','system','Инициировать завершение: сформировать итоговый черновик')
    d.msg('1.1','system','tree','Получить подтверждённый объём, фактический расход и версию источника')
    d.msg('1.2','system','system','Собрать задачи, ритуалы, метрики, инциденты; вычислить показатели')
    d.msg('1.3','system','planner','Показать черновик и причины блокировки; запросить уточнение',guard='данные неполны')
    d.msg('2','planner','system','Уточнить заголовок и повторно сформировать черновик',guard='нужно уточнение',iteration=True)
    d.msg('3','chief','system','Подтвердить результаты последнего черновика')
    d.msg('3.1','system','system','Повторно проверить работы, CRITICAL и неизменность источников')
    d.msg('3.2','system','chief','Отклонить завершение; показать все блокировки',guard='есть блокировки или источники изменились')
    d.msg('3.3','system','system','Утвердить снимок; сохранить COMPLETED и аудит атомарно',guard='проверки пройдены; запись успешна')
    d.msg('4','planner','system','Просмотреть отчёт; выгрузить PDF или HTML',guard='отчёт утверждён')
    d.msg('5','chief','system','Архивировать завершённый сезон и сохранить аудит',guard='нет CRITICAL; утверждена последняя версия')
    d.finish()

    d=Diagram(root,'02-logical','2. UC-5 — Взаимодействие пакетов','logical',
        'Начальная команда от frontend; авторизация на api; SeasonPlan ACTIVE для первого завершения.',
        'Локальные отчёт, статус и аудит согласованно сохранены после COMMIT; ошибки не завершают сезон.',
        'Уровень packages/01-system: application агрегирует completion, pollen, shared и port; domain — один существующий пакет. service не участвует: UC-5 фасад там отсутствует.\n'
        'Линии жизни — представители подсистем в реальных пакетах, не экземпляры UML Package. pollentree — HTTPS-адаптер; Pollen Tree — внешняя система, не Java-пакет.\n'
        '«проект»: REST/UI для UC-5, JDBC отчётов и источников, resource-visibility для чтения и composition wiring отсутствуют. api/security/application/domain/persistence/pollentree — существующие пакеты.\n'
        'valid = актуальный последний DRAFT, полный снимок, нет открытых CRITICAL, обязательные работы завершены. Ошибка проверки/записи → ROLLBACK; успех только после COMMIT.\n'
        'generate не меняет ACTIVE; approve может утвердить новую версию при COMPLETED без повторного перехода; export читает только APPROVED, archive — отдельная команда.')
    for key,name,source in package_definitions():
        d.participant(key,name,key+'Subsystem',key)
    d.msg('1','frontend','api','«проект» запросить черновик или его повторное формирование')
    d.msg('1.1','api','security','Аутентификация, RBAC и видимость SeasonPlan')
    d.msg('1.2','api','application','«проект» вызвать команду формирования UC-5')
    d.msg('1.2.1','application','persistence','Транзакция с блокировкой плана; прочитать итоговые источники')
    d.msg('1.2.2','application','pollentree','PollenService → PollenTreeClient: чтение расхода')
    d.msg('1.2.2.1','pollentree','provider','HTTPS GET usage; API key / mTLS, JSON')
    d.msg('1.2.3','application','domain','Создать снимок и DRAFT; рассчитать blockers и KPI')
    d.msg('1.2.4','application','persistence','«проект адаптеров» сохранить DRAFT и INSERT AuditLog; COMMIT')
    d.msg('2','frontend','api','«проект» подтвердить результаты последнего черновика')
    d.msg('2.1','api','application','Главный хранитель: повторно проверить источники и блокировки')
    d.msg('2.1.1','application','domain','Утвердить снимок; ACTIVE → COMPLETED',guard='valid')
    d.msg('2.1.2','application','persistence','APPEND отчёта + UPDATE плана + INSERT аудита; COMMIT',guard='valid и записи успешны')
    d.msg('2.2','api','frontend','Ошибки проверки / устаревшие данные / сбой записи',guard='not valid или исключение; ROLLBACK')
    d.msg('3','frontend','api','«проект» просмотреть / экспортировать / отдельно архивировать')
    d.msg('3.1','api','application','Чтение видимой версии; экспорт APPROVED; санкция архивирования')
    d.msg('3.1.1','application','persistence','Прочитать APPROVED; для archive сохранить ARCHIVED и аудит',guard='archive: COMPLETED, нет CRITICAL/нового DRAFT')
    d.finish()

    d=Diagram(root,'03-generation','3. Реализация — формирование и уточнение отчёта','implementation',
        'ACTIVE или COMPLETED; FAIRY_PLANNER — создатель плана либо CHIEF_GUARDIAN. ARCHIVED запрещён.',
        'Новый версионный DRAFT + blockers + аудит после COMMIT. Статус SeasonPlan не изменён; прежний APPROVED immutable.',
        'api: CompletionController «проект» — отсутствующая REST-граница; CurrentActor берётся из сессии. Конкретные URL не выдаются за существующие.\n'
        'tx: CommandTransaction → JdbcCommandTransaction; plans: SeasonPlanRepository → JdbcSeasonPlanRepository. reports: существующий CompletionPorts.Reports, JDBC-адаптер «проект».\n'
        '1.1.1–1.1.5 исполняются внутри command-callback (вложенная нумерация); execute ждёт его завершения. Вызов Supplier.get свёрнут. Граница transactions.execute; BEGIN + SELECT FOR UPDATE, затем COMMIT. Любое исключение → ROLLBACK черновика и аудита, без успешного ответа.\n'
        'snapshot(plan) раскрыт на 3a; классификатор CompletionService связан с этой поддиаграммой. new SeasonReport(UUID, planId, nextVersion, title, snapshot, snapshot.blockers(), DRAFT, actorId, now, null, null).\n'
        'Полный или неполный снимок сохраняется как DRAFT; недоступное Древо бросает исключение, а не превращается в missing data. Повтор: новый snapshot/version и замена только DRAFT.\n'
        'При генерации после COMPLETED изменяется только отчёт, не lifecycle. title — существующий параметр представления; дополнительные настройки не выдуманы.')
    d.participant('api','controller «проект»','CompletionController','api',status='proposed')
    d.participant('completion','completion','CompletionService')
    d.participant('tx','transactions','CommandTransaction')
    d.participant('plans','plans','SeasonPlanRepository')
    d.participant('reports','reports','CompletionPorts.Reports')
    d.participant('audit','audit','AuditLogService')
    d.msg('1','api','completion','generate(planId, title, role, actorId)','generate',guard='CHIEF или владелец FAIRY_PLANNER')
    d.msg('1.1','completion','tx','execute(planId, command)','transaction')
    d.msg('1.1.1','completion','plans','findById(planId)','planFind')
    d.msg('1.1.2','completion','completion','snapshot(plan) — см. 3a','snapshot',detail='03a-sources')
    d.msg('1.1.3','completion','reports','nextVersion(planId)','nextVersion')
    d.msg('1.1.4','completion','reports','saveDraft(report)','saveDraft',guard='snapshot построен; DRAFT')
    d.msg('1.1.5','completion','audit','append(planId, SEASON_REPORT_GENERATED, actorId, report.id)','audit')
    d.msg('1.2','completion','api','Вернуть SeasonReport DRAFT с blockers',guard='COMMIT успешен',reply_to='1')
    d.msg('2','api','completion','generate(planId, revisedTitle, role, actorId)','generate',guard='нужно уточнение; не ARCHIVED',iteration=True)
    d.finish()

    d=Diagram(root,'03a-sources','3a. Реализация — снимок итоговых источников','implementation',
        'Вызов CompletionService.snapshot(plan) из generate или approveAndComplete, внутри command transaction.',
        'Возвращён immutable Snapshot; источники только читаются. Отсутствующие итоги — blockers; отказ провайдера — исключение.',
        'Это детализация операции snapshot, а не новая HTTP-команда. completion вызывает существующие порты; реализации UC-4/UC-5 источников в runtime ещё не подключены.\n'
        'catalog — CompletionPorts.CatalogVersion; requiredResults включает отсутствующие обязательные категории каталога. forPlan у metrics/incidents/actions/rituals — разные интерфейсы и разные объекты.\n'
        'Списки сортируются по id; Snapshot копирует коллекции. plan даёт seasonType, территории, phenomena и targets; версия каталога сохраняется вместе с данными.\n'
        'PollenService.usage → PollenTreeClient.summary → RestPollenTreeClient HTTPS GET /season-plans/{id}/usage. Summary.snapshot сохраняет sourceVersion; нет прямых запросов pollen в PostgreSQL.\n'
        '404 usage → null в Snapshot → pollen-totals; timeout/5xx/некорректный ответ → PollenTreeUnavailableException, на целевой API 503; отчёт и success-audit не сохраняются.\n'
        'blockers: ritual-success, completed-tasks, completed-corrections, critical-incidents, climate-metrics, pollen-totals. KPI: actual-confirmed, actual/confirmed, инциденты по тяжести, mean deviation, critical count, ritual success rate.\n'
        'kpis() — вычисляемая проекция при просмотре/рендеринге; snapshot() сам её не вызывает. Read-model вызов 2 показан отдельно, не как побочный эффект построения снимка.')
    for key,typ in [('completion','CompletionService'),('catalog','CompletionPorts.CatalogVersion'),
                    ('tasks','CompletionPorts.Tasks'),('metrics','ExecutionPorts.Metrics'),
                    ('incidents','ExecutionPorts.Incidents'),('actions','ExecutionPorts.Corrections'),
                    ('rituals','ExecutionPorts.Rituals'),('pollen','PollenService'),('client','PollenTreeClient')]:
        d.participant(key,key,typ)
    d.participant('snapshot','snapshot','SeasonReport.Snapshot','domain')
    d.participant('tree','Pollen Tree','PollenTree','provider')
    d.msg('1.1','completion','catalog','forPlan(plan.id)','catalog')
    d.msg('1.2','completion','tasks','requiredResults(plan.id)','requiredResults')
    d.msg('1.3','completion','metrics','forPlan(plan.id)','metricList')
    d.msg('1.4','completion','incidents','forPlan(plan.id)','incidentList')
    d.msg('1.5','completion','actions','forPlan(plan.id)','actionList')
    d.msg('1.6','completion','rituals','forPlan(plan.id)','ritualList')
    d.msg('1.7','completion','pollen','usage(plan.id)','usage')
    d.msg('1.7.1','pollen','client','summary(planId) → Summary.snapshot()','summary')
    d.msg('1.7.1.1','client','tree','HTTPS GET /season-plans/{id}/usage')
    d.msg('1.8','completion','snapshot','blockers() после new Snapshot(...)','blockers')
    d.msg('2','completion','snapshot','kpis() для проекции просмотра / рендера','kpis',guard='отдельное чтение готового снимка')
    d.finish()

    d=Diagram(root,'03b-approval','3b. Реализация — утверждение и COMPLETED','implementation',
        'CHIEF_GUARDIAN; ACTIVE либо COMPLETED; имеется последний DRAFT. Для первого завершения ACTIVE.',
        'Только после COMMIT: immutable APPROVED; при ACTIVE также COMPLETED и два audit-события. Ошибка → прежнее состояние.',
        'valid = найден последний DRAFT с reportId; current.blockers пуст; current.equals(draft.snapshot()). Проверки источников повторяются непосредственно в команде.\n'
        'snapshot(plan) — та же поддиаграмма 3a, включая версию Древа и все источники. Старый reportId, изменённые данные, незавершённые задачи/ритуал/actions или открытый CRITICAL запрещают все сообщения [valid].\n'
        'UseCaseSupport.plan проверяет ACTIVE/COMPLETED; UseCaseSupport.copy отделяет изменяемый план. draft.approve создаёт новый APPROVED record; approved-снимки не обновляются.\n'
        '1.1.1–1.1.10 — command-callback внутри execute; Supplier.get свёрнут. Любая ошибка проверки или записи, включая appendApproved/plans.save/audit.append → ROLLBACK всей локальной команды; не посылать успех.\n'
        'reports.appendApproved обязан участвовать в CommandTransaction; production JDBC-адаптер отсутствует. Порты источников также требуют реальной композиции. Древо только читается и не входит в общую ACID-транзакцию.\n'
        'completing = исходный статус ACTIVE. Для уточнённого отчёта при COMPLETED утверждается новая версия без повторного lifecycle-перехода и без SEASON_PLAN_COMPLETED.\n'
        'Домен SeasonPlan.complete повторно требует APPROVED правильного плана и отсутствие блокировок снимка. Критические audit-записи append-only; ни UPDATE, ни DELETE AuditLog не допускаются.')
    for key,typ,pkg in [('api','CompletionController','api'),('completion','CompletionService','application'),
                        ('tx','CommandTransaction','application'),('plans','SeasonPlanRepository','application'),
                        ('reports','CompletionPorts.Reports','application'),('current','SeasonReport.Snapshot','domain'),
                        ('draft','SeasonReport','domain'),('plan','SeasonPlan','domain'),('audit','AuditLogService','application')]:
        d.participant(key,'controller «проект»' if key=='api' else key,typ,pkg,status='proposed' if key=='api' else 'existing')
    d.msg('1','api','completion','approveAndComplete(planId, reportId, CHIEF_GUARDIAN, actorId)','approveComplete')
    d.msg('1.1','completion','tx','execute(planId, command)','transaction')
    d.msg('1.1.1','completion','plans','findById(planId); copy(plan)','planFind')
    d.msg('1.1.2','completion','reports','latestDraft(planId); require draft.id == reportId','latestDraft')
    d.msg('1.1.3','completion','completion','snapshot(plan) — повторная проверка, см. 3a','snapshot',detail='03a-sources')
    d.msg('1.1.4','completion','current','blockers(); compare current == draft.snapshot','blockers')
    d.msg('1.1.5','completion','draft','approve(actorId, now) → approved','reportApprove',guard='valid')
    d.msg('1.1.6','completion','plan','complete(approved, actorId, now)','planComplete',guard='valid and completing')
    d.msg('1.1.7','completion','reports','appendApproved(approved)','appendApproved',guard='valid')
    d.msg('1.1.8','completion','audit','append(planId, SEASON_REPORT_APPROVED, actorId, approved.id)','audit',guard='valid')
    d.msg('1.1.9','completion','plans','save(plan)','planSave',guard='valid and completing')
    d.msg('1.1.10','completion','audit','append(planId, SEASON_PLAN_COMPLETED, actorId, approved.id)','audit',guard='valid and completing')
    d.msg('1.2','completion','api','Вернуть approved после успешного завершения execute',guard='valid and COMMIT successful',reply_to='1')
    d.msg('1.3','completion','api','DomainValidationException / NotFound / ошибка записи',guard='not valid or exception; ROLLBACK',reply_to='1')
    d.finish()

    d=Diagram(root,'03c-publication','3c. Реализация — просмотр, экспорт, архив','implementation',
        'Видимый пользователю SeasonPlan; экспорт требует сохранённый APPROVED. Архивирование: только CHIEF_GUARDIAN и COMPLETED.',
        'Экспорт из immutable approved snapshot, versioned filename; архивирование и аудит после COMMIT; архив read-only.',
        '1, 2 и 3 — НЕ один длинный запрос, а независимые команды. Target controller обеспечивает resource visibility/RBAC до view/export; сами read-методы CompletionService role не принимают.\n'
        'view возвращает newer DRAFT, если он новее последнего APPROVED, иначе APPROVED; возможен Optional.empty. export никогда не использует этот DRAFT, не читает live sources и не меняет состояние.\n'
        'approved(planId) проверяет существование latestApproved и Status.APPROVED. Exporter — существующий интерфейс, но PDF/HTML-renderer отсутствует. CompletionService.Export копирует byte[].\n'
        'Файл season-{planId}-v{version}.pdf / .html; MIME application/pdf либо text/html; charset=UTF-8. kpis вычисляется только по переданному снимку.\n'
        'archiveValid = COMPLETED, нет operational CRITICAL, approved сохранён, нет latestDraft.version > approved.version. При новом DRAFT сначала approveAndComplete, без повторного COMPLETED.\n'
        '3.1.1–3.1.7 выполняются в command-callback внутри tx.execute; любая ошибка → ROLLBACK и прежний COMPLETED. reports/incident runtime-адаптеры и API — «проект»; JdbcCommandTransaction/plans/audit существуют.\n'
        'Открытые CRITICAL блокируют архивирование. Утверждённые отчёты и AuditLog append-only; archive не удаляет источники. Историческая аналитика — последующее использование, не дополнительный шаг закрытия.')
    for key,typ,pkg in [('api','CompletionController','api'),('completion','CompletionService','application'),
                        ('plans','SeasonPlanRepository','application'),('reports','CompletionPorts.Reports','application'),
                        ('exporter','CompletionPorts.Exporter','application'),('tx','CommandTransaction','application'),
                        ('incidents','ExecutionPorts.Incidents','application'),('plan','SeasonPlan','domain'),
                        ('audit','AuditLogService','application')]:
        d.participant(key,'controller «проект»' if key=='api' else key,typ,pkg,status='proposed' if key=='api' else 'existing')
    d.msg('1','api','completion','view(planId)','view')
    d.msg('1.1','completion','plans','findById(planId)','planFind')
    d.msg('1.2','completion','reports','latestApproved(planId)','latestApproved')
    d.msg('1.3','completion','reports','latestDraft(planId); выбрать newer DRAFT или APPROVED','latestDraft')
    d.msg('2','api','completion','export(planId, HTML | PDF)','export')
    d.msg('2.1','completion','plans','findById(planId)','planFind')
    d.msg('2.2','completion','completion','approved(planId)','approved')
    d.msg('2.2.1','completion','reports','latestApproved(planId)','latestApproved')
    d.msg('2.3','completion','exporter','render(approved, extension) → bytes','render',guard='approved snapshot exists')
    d.msg('3','api','completion','archive(planId, CHIEF_GUARDIAN, actorId)','archive')
    d.msg('3.1','completion','tx','execute(planId, command)','transaction')
    d.msg('3.1.1','completion','plans','findById(planId); require COMPLETED; copy(plan)','planFind')
    d.msg('3.1.2','completion','incidents','forPlan(planId); reject any critical()','incidentList')
    d.msg('3.1.3','completion','completion','approved(planId)','approved')
    d.msg('3.1.3.1','completion','reports','latestApproved(planId)','latestApproved')
    d.msg('3.1.4','completion','reports','latestDraft(planId); reject unapproved newer revision','latestDraft')
    d.msg('3.1.5','completion','plan','archive(approved, actorId, now)','planArchive',guard='archiveValid')
    d.msg('3.1.6','completion','plans','save(plan)','planSave',guard='archiveValid')
    d.msg('3.1.7','completion','audit','append(planId, SEASON_PLAN_ARCHIVED, actorId, approved.id)','audit',guard='archiveValid')
    d.msg('3.2','completion','api','SeasonPlan ARCHIVED либо ошибка без изменения статуса',guard='успех только после COMMIT; иначе ROLLBACK',reply_to='3')
    d.finish()
    return root


def generate(output):
    folder=Path(output); folder.mkdir(parents=True,exist_ok=True)
    root=build(); ET.indent(root)
    ET.ElementTree(root).write(folder/'communications.xml',encoding='utf-8',xml_declaration=True)
    sources={JAVA+op[5] for op in OPS}
    sources.update(['AGENTS.md','arch_docs/USE_CASE_SPECIFICATION.md','arch_docs/SYSTEM_REQUIREMENTS_SPECIFICATION.md',
                    'arch_docs/VISION.md','docs/architecture/ST-00-specification-resolution.md',
                    'docs/architecture/POLLEN_TREE_EXTERNAL_SERVICE.md',PACKAGE_SOURCE,
                    'backend/src/test/java/com/seasonplan/lifecycle/application/CompletionUseCaseTest.java',
                    JAVA+'persistence/JdbcCommandTransaction.java',JAVA+'api/LifecycleController.java',
                    JAVA+'config/LifecycleConfiguration.java'])
    manifest={'scope':root.get('scope'),'sourceHashes':{p:hashlib.sha256((REPO/p).read_bytes()).hexdigest() for p in sorted(sources)},
              'references':['https://knowhow.visual-paradigm.com/openapi/create-communication-diagram/',
                            'https://www.visual-paradigm.com/guide/uml-unified-modeling-language/what-is-communication-diagram/',
                            'https://www.omg.org/spec/UML/2.5.1'],
              'diagramCount':len(root.findall('diagram')),'operationCount':len(OPS),
              'runtimeGaps':['UC5 UI/REST and read authorization wiring','Completion/execution persistence adapters and composition',
                             'Real PDF/HTML renderer','Provider compatibility and cross-system source consistency']}
    (folder/'source-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


if __name__=='__main__':
    generate(sys.argv[1])
