"""Source-backed package diagram metadata and Graphviz geometry; no app changes."""
import copy
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
BASE = 'com.seasonplan.lifecycle'
APP = BASE + '.application'
DOT = Path('X:/apps/graphviz/dot.exe')
SOURCES = [
    'AGENTS.md', 'arch_docs/USE_CASE_SPECIFICATION.md',
    'arch_docs/SYSTEM_REQUIREMENTS_SPECIFICATION.md', 'arch_docs/VISION.md',
    'docs/architecture/ST-00-specification-resolution.md',
    'docs/architecture/POLLEN_TREE_EXTERNAL_SERVICE.md',
    'backend/README.md', 'backend/CHANGELOG.md', 'backend/pom.xml',
    'frontend/package.json', 'frontend/src/api/lifecycleApi.js',
]


def scan_sources():
    """Resolve project imports (including package wildcards) against declarations.

    This intentionally reports compile-time import dependencies, not calls, class
    ownership, Maven modules, transaction guarantees or deployed capabilities.
    """
    inventory, units = {}, []
    for path in sorted((ROOT / 'backend/src/main/java').rglob('*.java')):
        text = path.read_text(encoding='utf-8')
        package = re.search(r'^package\s+([\w.]+);', text, re.M).group(1)
        inventory.setdefault(package, []).append(package + '.' + path.stem)
        units.append((path, package, text))
    dependencies = {}
    for path, client, text in units:
        for imported in re.findall(r'^import\s+(?:static\s+)?([\w.*]+);', text, re.M):
            # Longest existing package prefix also handles nested classes/static imports.
            matches = [p for p in inventory if imported.startswith(p + '.')]
            if not matches:
                continue
            supplier = max(matches, key=len)
            if supplier != client:
                dependencies.setdefault((client, supplier), []).append(
                    str(path.relative_to(ROOT)).replace('\\', '/') + ': import ' + imported)
    return inventory, dependencies


def node(name, body, color='EAF3FB', w=420, h=280):
    return dict(name=name, body=body, color=color, w=w, h=h)


def build_views(inventory, dependencies):
    nodes = {
        'frontend': node('frontend/src',
            'React 18 · browser client\nauth / components / api\nfeatures: seasonPlanning,\nplanReview, preparation\nHTTP client: lifecycleApi.js\nNo direct database access', 'E8F3FC'),
        BASE+'.api': node('api',
            'com.seasonplan.lifecycle.api\nLifecycleController\nLifecycleApiModels / errors\nREST routes: planning, approval,\npollen reservations, preparation\nUC-4 / UC-5 routes absent', 'E8F3FC'),
        'com.seasonplan.security': node('security',
            'com.seasonplan.security\nAuthController / AdminUserController\nLocalUserAdministrationService\nSecurityCurrentActorProvider\nSession authentication / CSRF\nJDBC users and administration', 'F0EBF9'),
        BASE+'.service': node('service',
            'com.seasonplan.lifecycle.service\nSeasonPlanService\nCompatibility facade\nDelegates UC-1 / UC-2 / UC-3\nNot an independent domain module', 'EAF6EF'),
        APP: node('application',
            'com.seasonplan.lifecycle.application.*\nUC-1 planning / UC-2 approval\nUC-3 preparation / UC-4 execution\nUC-5 completion / pollen\nport / shared / UseCaseSupport\nUC-4 / UC-5: core-only services\nPollenService is the pollen boundary', 'EAF6EF'),
        BASE+'.domain': node('domain',
            'com.seasonplan.lifecycle.domain\nSeasonPlan / PollenReservation\nMagicTask / FairyAssignment\nRitualSession / ClimateMetric\nMagicIncident / CorrectionAction\nSeasonReport / AuditLogEntry\nRules, enums, immutable snapshots\nOnly JDK dependencies', 'F0F6E5'),
        BASE+'.persistence': node('persistence',
            'com.seasonplan.lifecycle.persistence\nJdbc*Repository / JdbcCommandTransaction\nPlans, tasks, assignments, audit\nJDBC → PostgreSQL 14 / Liquibase\nAppend-only audit protections\nNo local pollen reservation writes\nUC-4 / UC-5 adapters absent', 'FFF2DF'),
        BASE+'.adapter.fake': node('fake',
            'lifecycle.adapter.fake\nInMemory*Repository\nAllowingNaturalCalendarService: stub\nNotificationService: stub\nFakePollenTreeClient: dev/test only\nNot production provider adapters', 'FFF2DF'),
        BASE+'.adapter.pollentree': node('pollentree',
            'lifecycle.adapter.pollentree\nRestPollenTreeClient\nImplements PollenTreeClient\nHTTPS / JSON → external Pollen Tree\nProposed provider contract\nLive provider compatibility unverified\nNo domain → adapter dependency', 'FFF2DF'),
        BASE+'.config': node('config',
            'com.seasonplan.lifecycle.config\nLifecycleConfiguration\nSecurityConfiguration\nPollenTreeIntegrationConfiguration\nSpring composition / RBAC / profiles\nWires ports to implementations\nNot all core services are wired', 'F0EBF9'),
    }
    edges = {}
    visible = set(nodes)
    for (a,b), evidence in dependencies.items():
        aa = APP if a.startswith(APP+'.') else a
        bb = APP if b.startswith(APP+'.') else b
        if aa in visible and bb in visible and aa != bb:
            edges.setdefault((aa,bb), []).extend(evidence)
    overview_edges = [dict(id='system-'+str(i), **{'from':a,'to':b}, name='', evidence=ev)
                      for i,((a,b),ev) in enumerate(sorted(edges.items()))]
    for supplier, name in [(BASE+'.api','REST / JSON'), ('com.seasonplan.security','Auth / JSON')]:
        overview_edges.append(dict(id='web-'+supplier.rsplit('.',1)[-1],
            **{'from':'frontend','to':supplier}, name=name,
            evidence=['frontend/src/api/lifecycleApi.js', 'frontend/src/auth/AuthContext.jsx']))

    usecases = {
        APP: node('application',
            'Direct package contents only:\nUseCaseSupport\nShared command lookup/copy helpers\nOwns the eight child packages\nshown in this view', 'EAF6EF'),
        APP+'.planning': node('planning',
            'UC-1 · Планирование сезона\nPlanningService\nCreate / edit / submit / list\nNaturalCalendarService port (stub wired)\nRequired fields / intra-plan windows\nSearch / cross-plan conflicts: missing\nFR-1–6; FR-82–85: target', 'EAF6EF'),
        APP+'.approval': node('approval',
            'UC-2 · Согласование\nApprovalService\nReview / approve / return\nReads pollen through PollenService\nverify / conditions: implementation gap\nFR-7–9, FR-13, FR-19', 'EAF6EF'),
        APP+'.preparation': node('preparation',
            'UC-3 · Подготовка к запуску\nMagicTaskService / FairyAssignmentService\nFairySchedulePolicy\nReadinessChecklistService\nPreparationService → RITUAL_READY\nPollenService for resource checks\nFR-10, FR-24–40', 'EAF6EF'),
        APP+'.execution': node('execution',
            'UC-4 · Выполнение и контроль\nRitualService / MonitoringService\nIncidentResponseService\nRitual / metrics / incidents / corrections\ncore-only; adapters and routes missing\nPollen consumption: sibling pollen\nFR-11–12, FR-41–68, FR-90–91', 'EAF6EF'),
        APP+'.completion': node('completion',
            'UC-5 · Завершение сезона\nCompletionService\nReport draft / approval / export contract\nComplete / archive / KPI snapshot\nPollenService for consumption data\ncore-only; no renderer or REST wiring\nFR-69–77', 'EAF6EF'),
        APP+'.pollen': node('pollen',
            'Cross-use-case PollenService\nReservation request / confirm / reject\nConsumption / audit / warnings\nOnly application owner of PollenTreeClient\nCONFIRMED is the canonical state\nProvider owns reservations and spending\nConsumption REST/outbox wiring missing\nFR-14–23', 'EAF6EF'),
        APP+'.shared': node('shared',
            'AuditLogService / ValidationSupport\nAccess and not-found exceptions\nPollenTreeUnavailableException\nAppend-only audit application service\nFR-13, FR-78–81\nNo concrete persistence dependency', 'EBF1F8'),
        APP+'.port': node('port',
            'Framework-independent interfaces\nSeasonPlan / task / assignment repositories\nAuditLogRepository / CommandTransaction\nCurrentActorProvider / PollenTreeClient\nNaturalCalendarService\nPlannerNotifications / NotificationOutbox\nExecutionPorts / CompletionPorts\nImplementations belong outside core', 'EBF1F8'),
        BASE+'.domain': copy.deepcopy(nodes[BASE+'.domain']),
    }
    app_edges = [dict(id='application-'+str(i), **{'from':a,'to':b}, name='', evidence=ev)
                 for i,((a,b),ev) in enumerate(sorted(dependencies.items()))
                 if a in usecases and b in usecases]
    return [dict(id='01-system',name='1. SeasonPlan — Архитектура пакетов',nodes=nodes,edges=overview_edges,
                 intro='Обзор существующего кода · application.* агрегирован в один пакет',
                 notes='UML Dependency: клиент → используемый пакет; REST-стрелки обозначают HTTP-контракт, остальные — зависимости импортов Java.\nЭто пакеты, не микросервисы и не отдельные Maven-модули. Фреймворки, PostgreSQL и внешние API указаны в пояснениях, не выданы за пакеты приложения.\nNatural Calendar сейчас — разрешающий stub; датчики, доставка outbox и PDF/HTML-рендереры не реализованы. Pollen Tree: HTTPS-адаптер есть, контракт ещё предложен.\nST-00 имеет приоритет над устаревшими SRS/UC: только 8 статусов SeasonPlan; CORRECTION/EMERGENCY — операционные условия, не статусы.'),
            dict(id='02-application',name='2. SeasonPlan — Прецеденты и контракты',nodes=usecases,edges=app_edges,
                 intro='Точные прямые импорты пакетов приложения · база: com.seasonplan.lifecycle.application',
                 notes='planning, approval, preparation, execution, completion, pollen, port, shared — реальные дочерние пакеты application; domain — соседний пакет lifecycle.\nПрямоугольник application показывает только UseCaseSupport, НЕ агрегирует детей в этом виде. Общее владение зафиксировано в нативной модели пакетов.\nСтрелки — прямые зависимости импортов, не порядок исполнения UC и не переходы статусов. Разные предметные сущности пока находятся в одном реальном domain.\nAPPROVED требует CONFIRMED-резерв; RITUAL_READY — обязательные задачи/чек-лист; ACTIVE — успешный ритуал; COMPLETED запрещён при открытом CRITICAL-инциденте.\nAuditLog неизменяем. Запись в БД не заменяет Pollen Tree. Полная атомарность/outbox и незавершённые вертикальные срезы не заявлены как реализованные.')]


def boundary(p, r, tolerance=2):
    x,y,w,h=r;px,py=p
    return x-tolerance<=px<=x+w+tolerance and y-tolerance<=py<=y+h+tolerance and min(abs(px-x),abs(px-x-w),abs(py-y),abs(py-y-h))<=tolerance


def intersects(a,b,r):
    x,y,w,h=r
    if a[0] == b[0]:
        return x+2<a[0]<x+w-2 and max(min(a[1],b[1]),y+2)<min(max(a[1],b[1]),y+h-2)
    return y+2<a[1]<y+h-2 and max(min(a[0],b[0]),x+2)<min(max(a[0],b[0]),x+w-2)


def layout(original):
    view = copy.deepcopy(original)
    ids = {key:'n'+str(i) for i,key in enumerate(view['nodes'])}
    lines = ['digraph packages {', 'graph [rankdir=TB,splines=ortho,nodesep=0.9,ranksep=1.1,pad=0,bgcolor="white"];',
             'node [shape=box,fixedsize=true,label=""];', 'edge [arrowhead=none,arrowtail=none];']
    for key,n in view['nodes'].items():
        lines.append(f'{ids[key]} [width={n["w"]/72},height={n["h"]/72}];')
    for e in view['edges']:
        lines.append(f'{ids[e["from"]]} -> {ids[e["to"]]};')
    lines.append('}')
    data = json.loads(subprocess.run([str(DOT), '-Tjson'],input='\n'.join(lines),
                                    text=True,capture_output=True,check=True).stdout)
    height = float(data['bb'].split(',')[3])
    offset_x, offset_y = 80, 180
    for obj in data['objects']:
        key = next(k for k,v in ids.items() if v == obj['name'])
        n = view['nodes'][key];cx,cy=map(float,obj['pos'].split(','))
        n['x']=round(cx-n['w']/2)+offset_x;n['y']=round(height-cy-n['h']/2)+offset_y
    for edge, obj in zip(view['edges'], data['edges']):
        points = []
        for token in obj['pos'].split():
            x,y=map(float,token.split(','));p=(round(x)+offset_x,round(height-y)+offset_y)
            if not points or p!=points[-1]:
                points.append(p)
        # Cubic orthogonal splines contain redundant control points.
        simple=[]
        for p in points:
            if len(simple)>1 and ((simple[-2][0]==simple[-1][0]==p[0]) or (simple[-2][1]==simple[-1][1]==p[1])):
                simple[-1]=p
            else:
                simple.append(p)
        edge['points']=simple
    view['width']=round(float(data['bb'].split(',')[2]))+160
    view['height']=round(height)+420
    validate_geometry(view)
    return view


def validate_geometry(view):
    bounds={k:(n['x'],n['y'],n['w'],n['h']) for k,n in view['nodes'].items()}
    for key,r in bounds.items():
        x,y,w,h=r
        if w<=0 or h<=0:raise AssertionError('Nonpositive '+key)
        for other,q in bounds.items():
            if key<other and x<q[0]+q[2] and x+w>q[0] and y<q[1]+q[3] and y+h>q[1]:
                raise AssertionError('Overlapping packages '+key+' / '+other)
    for e in view['edges']:
        ps=e['points']
        if len(ps)<2 or not boundary(ps[0],bounds[e['from']]) or not boundary(ps[-1],bounds[e['to']]):
            raise AssertionError('Unattached '+str(e))
        for a,b in zip(ps,ps[1:]):
            if a[0]!=b[0] and a[1]!=b[1]:raise AssertionError('Diagonal '+str(e))
            for ident,r in bounds.items():
                if intersects(a,b,r):raise AssertionError('Route through '+ident+' '+str(e))


def generate(directory):
    directory=Path(directory);directory.mkdir(parents=True,exist_ok=True)
    inventory,deps=scan_sources();views=[layout(v) for v in build_views(inventory,deps)]
    root=ET.Element('package-project')
    for view in views:
        d=ET.SubElement(root,'diagram',{k:str(view[k]) for k in ('id','name','width','height','intro','notes')})
        for ident,n in view['nodes'].items():
            ET.SubElement(d,'package',id=ident,**{k:str(v) for k,v in n.items()})
        for e in view['edges']:
            ET.SubElement(d,'dependency',id=e['id'],client=e['from'],supplier=e['to'],name=e['name'],
                          points=';'.join(f'{x},{y}' for x,y in e['points']),evidence='\n'.join(e['evidence']))
    ET.indent(root)
    ET.ElementTree(root).write(directory/'packages.xml',encoding='utf-8',xml_declaration=True)
    source_paths=sorted(set(SOURCES+[str(p.relative_to(ROOT)).replace('\\','/')
                  for p in (ROOT/'backend/src/main/java').rglob('*.java')]+[
                  str(p.relative_to(ROOT)).replace('\\','/') for p in (ROOT/'frontend/src').rglob('*') if p.is_file()]))
    manifest=dict(packageInventory=inventory,views=views,
                  sourceSHA256={p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in source_paths},
                  dependencyPolicy='Direct project Java imports; overview aggregates application descendants. REST edges are HTTP dependencies. External libraries omitted.')
    (directory/'source-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Generated',[(v['id'],len(v['nodes']),len(v['edges'])) for v in views])


if __name__=='__main__':
    generate(sys.argv[1])
