"""Source-backed target deployment and deterministic geometry (documentation only)."""
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
SOURCES = [
    'AGENTS.md', 'arch_docs/VISION.md', 'arch_docs/USE_CASE_SPECIFICATION.md',
    'arch_docs/SYSTEM_REQUIREMENTS_SPECIFICATION.md',
    'docs/architecture/ST-00-specification-resolution.md',
    'docs/architecture/POLLEN_TREE_EXTERNAL_SERVICE.md',
    'backend/pom.xml', 'frontend/package.json',
    'backend/src/main/resources/application.yml', 'backend/src/main/resources/application-prod.yml',
    'backend/src/main/resources/db/changelog/changes/002-season-coordination-schema.sql',
    'backend/src/main/java/com/seasonplan/lifecycle/config/SecurityConfiguration.java',
    'backend/src/main/java/com/seasonplan/lifecycle/config/LifecycleConfiguration.java',
    'backend/src/main/java/com/seasonplan/lifecycle/config/PollenTreeIntegrationConfiguration.java',
    'frontend/src/api/lifecycleApi.js', 'frontend/src/api/workflowStorage.js',
    'backend/src/main/java/com/seasonplan/lifecycle/api/LifecycleController.java',
]


def model():
    nodes = []
    edges = []

    def node(ident, name, box, parent='', stereotype='', kind='Node', status='target', text=''):
        n = dict(id=ident, name=name, type=kind, parent=parent, stereotype=stereotype, status=status,
                 x=box[0], y=box[1], w=box[2], h=box[3], text=text)
        nodes.append(n)
        return n

    def note(ident, text, box, parent=''):
        return node(ident, text, box, parent, kind='NOTE', text=text)

    def edge(ident, a, b, name, protocol, points, caption, evidence):
        edges.append(dict(id=ident, type='Association', **{'from': a, 'to': b}, name=name,
                          protocol=protocol, points=points, caption=caption, evidence=evidence))

    note('title', 'Deployment view · SeasonPlan / Helios\nПодробная диаграмма развертывания с указанием характеристик машин и интерфейсов взаимодействия', [40, 20, 3460, 110])
    node('client', 'Терминал пользователя', [40, 220, 500, 440], stereotype='device')
    note('client-facts', 'ОС: Windows / macOS / Unix; экран ≥1366×768\nCPU/RAM не нормированы; доступ к HTTPS', [70, 295, 440, 45], 'client')
    node('browser', 'Chrome / Firefox / Edge', [70, 350, 440, 260], 'client', 'executionEnvironment')
    note('browser-facts', 'React 18.3.1 исполняется в браузере\nСессия: Secure + HttpOnly cookie; CSRF\nНет доступа к БД и ключам интеграций', [95, 410, 390, 100], 'browser')
    node('browser-app', 'Загруженные HTML / JS / CSS', [95, 530, 390, 55], 'browser', kind='Artifact')

    node('operator', 'Станция оператора выпуска', [40, 1010, 500, 470], stereotype='device')
    note('operator-facts', 'Windows 10 Pro; CPU/RAM: не заданы\nJDK 17 + Maven; Node.js 24 + npm.cmd', [70, 1085, 440, 40], 'operator')
    node('build-runtime', 'JDK 17 / Maven / Node.js', [70, 1140, 440, 300], 'operator', 'executionEnvironment')
    note('build-facts', 'mvn package (Java release 17)\nnpm.cmd ci; npm.cmd run build\nРучная публикация по SSH/SFTP :22', [95, 1220, 390, 75], 'build-runtime')
    node('release', 'JAR + dist/ + migration bundle\none-shot Liquibase на Helios [ПРОЕКТ]\nowner-role только на время миграции\nЗатем runtime-role / SHA-256 / версия', [95, 1310, 390, 110], 'build-runtime', kind='Artifact')

    node('helios', 'Helios · helios.cs.ifmo.ru', [780, 180, 1400, 1650], stereotype='device', status='documented-host')
    note('host-facts', 'SRS §3.6.2: FreeBSD 14.4-STABLE / amd64 · Intel Xeon E5-2643 0 @ 3.30 GHz\n16 логических CPU · RAM 137381744640 байт (≈128 GiB) · системный раздел 456 GB\nЭто характеристики общего хоста, не квота SeasonPlan; минимум приложения из Vision: 2 vCPU / 4 GB / 20 GB SSD / 100 Mbit/s.', [810, 250, 1340, 130], 'helios')
    node('os', 'FreeBSD 14.4-STABLE · нативные процессы', [810, 410, 1340, 1380], 'helios', 'executionEnvironment')
    node('proxy', 'nginx · HTTPS + статические файлы [ПРОЕКТ]', [860, 480, 1240, 310], 'os', 'executionEnvironment', status='proposed')
    node('frontend', 'frontend/dist/\nindex.html + assets/*.js + *.css', [900, 580, 500, 100], 'proxy', kind='Artifact')
    node('proxy-config', 'seasonplan-site.conf + TLS certificate\n443/TLS 1.2+; strip /api/ → 127.0.0.1:8080\n/api/auth → /auth; / → dist/; SPA fallback\nКлючи TLS: только оператор / proxy\nПРОЕКТ: конфигурация не реализована', [1470, 570, 600, 180], 'proxy', kind='DeploymentSpecification', status='proposed')
    note('proxy-facts', 'Один origin для UI и API; доверенные Forwarded-заголовки\nNode.js и dev-сервер Vite на Helios не нужны', [900, 695, 500, 70], 'proxy')
    node('jvm', 'OpenJDK 17.0.12 · Spring Boot 3.2.12 / embedded Tomcat', [860, 950, 1240, 320], 'os', 'executionEnvironment')
    node('backend', 'seasonplan-backend-0.1.0-SNAPSHOT.jar', [890, 1040, 500, 75], 'jvm', kind='Artifact')
    note('backend-facts', 'Единый процесс / модульный монолит\nUC-1…UC-5, RBAC, сессии, REST\nOutbox / scheduler / PDF: целевой срез', [890, 1140, 500, 100], 'jvm')
    node('app-config', 'application-prod.yml + защищённое окружение\nSPRING_PROFILES_ACTIVE=prod\n127.0.0.1:8080; management:127.0.0.1:8081\nJDBC runtime-role; POLLEN_TREE_MODE=rest\nPOLLEN_TREE_BASE_URL / API_KEY; secret files\nSPRING_LIQUIBASE_ENABLED=false [ПРОЕКТ]\njava -jar …; daemon(8), restart: ПРОЕКТ', [1450, 1030, 620, 210], 'jvm', kind='DeploymentSpecification')
    node('postgres', 'PostgreSQL 14 · primary [размещение на Helios: ПРОЕКТ]', [860, 1440, 1240, 310], 'os', 'executionEnvironment', status='proposed')
    node('database', 'seasonplan: schema + durable data / PGDATA\nAuditLog, планы, задачи, сессии, approved reports', [890, 1530, 650, 85], 'postgres', kind='Artifact')
    note('database-facts', 'Пыльца хранится только в Pollen Tree, не в нашей БД\nLiquibase отдельно, owner-role [ПРОЕКТ]; SQL из JAR\nНовая UC-4/UC-5 схема и outbox требуют доработки', [890, 1630, 650, 100], 'postgres')
    node('db-config', 'postgresql.conf / pg_hba.conf [ПРОЕКТ]\n5432: loopback + allowlist recovery\nfsync=on; full_page_writes=on\nsynchronous_commit=remote_apply\nsynchronous_standby_names=…\nAudit: INSERT/SELECT; запрет UPDATE/DELETE/\nTRUNCATE; runtime-role не владелец', [1580, 1520, 490, 210], 'postgres', kind='DeploymentSpecification', status='proposed')

    external = [
        ('pollen', 'Внешний узел: Pollen Tree API', 200, 240,
         'I4 · REST / HTTPS :443 · TLS 1.2/1.3\nJSON UTF-8; X-API-Key; 2s connect / 3s read\nРезерв / подтверждение / расход; владелец ресурса\nREST-адаптер есть; live-контракт не подтвержден\nCPU / RAM / ОС: зона ответственности провайдера'),
        ('calendar', 'Внешний узел: Природный календарь', 490, 210,
         'I5 · REST / HTTPS :443 · TLS 1.2+\nJSON UTF-8; API-key или mTLS [ПРОЕКТ]\nОкна сезонов / ограничения территорий\nСейчас AllowingNaturalCalendarService — fake\nCPU / RAM / ОС и URL не заданы'),
        ('metrics', 'Внешний узел: система датчиков', 750, 210,
         'I6 · REST pull / HTTPS :443 · TLS 1.2+\nJSON UTF-8; API-key или mTLS [ПРОЕКТ]\nClimateMetric: опрос каждые 1–5 минут\nWSS :443 — допустимая, не выбранная альтернатива\nCPU / RAM / ОС и adapter не заданы'),
        ('mail', 'Внешний узел: почтовый шлюз [ПРОЕКТ]', 1010, 210,
         'I7 · SMTP submission :587 + STARTTLS\nTLS 1.2+ / SMTP AUTH, обязателен шифрованный канал\nEmail из durable outbox; in-app через REST\nST-00: уведомления; транспорт не реализован\nCPU / RAM / ОС и endpoint не заданы'),
    ]
    for ident, name, y, h, text in external:
        node(ident, name, [2850, y, 650, h], status='required-external')
        note(ident+'-facts', text, [2870, y+55, 610, h-70], ident)

    node('recovery', 'Отдельный recovery-хост [ПРОЕКТ]', [2650, 1470, 850, 650], stereotype='device', status='proposed')
    note('recovery-facts', 'Предложение: FreeBSD 14 / amd64; 2 vCPU / 4 GB RAM / ≥100 GB SSD\nНезависимый диск и питание; ≥100 Mbit/s; sizing / retention уточнить\nНужны разрешения DBA и отдельная машина; это не факт об Helios\nI9/I10: TLS verify-full + SCRAM-SHA-256', [2680, 1540, 790, 100], 'recovery')
    node('standby', 'PostgreSQL 14 · synchronous standby [ПРОЕКТ]', [2690, 1650, 770, 220], 'recovery', 'executionEnvironment', status='proposed')
    node('recovery-data', 'PGDATA + WAL · синхронная копия\nRPO=0 подтверждённых локальных commit\nпри отказе одного primary-хоста', [2720, 1720, 710, 115], 'standby', kind='Artifact', status='proposed')
    node('backup-runtime', 'cron + pg_basebackup / pg_receivewal [ПРОЕКТ]', [2690, 1920, 770, 170], 'recovery', 'executionEnvironment', status='proposed')
    node('backups', 'Base backup ≤4 часа + непрерывный WAL\nЗащищённый архив / восстановление PITR / restore drills', [2720, 1980, 710, 80], 'backup-runtime', kind='Artifact', status='proposed')

    note('operations', 'Границы и эксплуатация\n\nПРОЕКТ = целевое решение, не установленный сервис.\nСтандартные device / executionEnvironment — узлы UML 2;\nвложенный artifact — размещённый файл. Сплошные связи\nмежду узлами — CommunicationPath (Association),\nне последовательность вызовов; I1…I10 — реестр интерфейсов.\n\nNative nginx предложен только для TLS :443 и static UI;\nне выделяются микросервисы, контейнеры или отдельный UI runtime.\nНужны разрешение администратора на :443, сертификат,\nPostgreSQL / репликацию, btree_gist и service supervision.\n\nAudit: append-only транзакция + синхронный WAL; нет standby —\nзапись ждёт, нельзя автоматически ослаблять durability.\nБэкап каждые 4 часа сам по себе не даёт RPO=0.\nЗащита от злонамеренного DBA / гибели обоих хостов\nтребует независимого неизменяемого архива и политики доступа.\n\nI8: оператор → FreeBSD, ручная установка JAR / dist / config;\nSSH port 22 — проектное допущение. Management :8081 локален;\nсейчас /actuator denyAll; доступ health надо спроектировать.\nМиграции: отдельный one-shot Liquibase с owner-role;\nruntime не владелец и без Liquibase / owner credentials.', [40, 1530, 650, 600])
    note('constraints', 'Целевая конфигурация, не отчёт о развёртывании. SRS DC-11 и упоминания Docker в Vision для Helios переопределены явным требованием пользователя: только native deployment.\nНорматив: ST-00 — 50 активных пользователей; ≥100 событий/мин; p95 UI ≤3s, операции ≤5s; доступность ≥80%. Нагрузочная проверка и доступные квоты Helios ещё нужны.\nI1: HTTPS UI/API, JSON UTF-8; I2: HTTP только loopback, X-Forwarded-* заменяет proxy; I3: JDBC / PostgreSQL wire только loopback; I4–I6: внешние HTTPS API без browser-direct calls.\nI9: standby инициирует TLS replication к primary :5432 (WAL передаётся primary → standby); I10: basebackup/WAL pull TLS :5432, выделенная backup-role, allowlist; backups/replication не публичны.\nRR-1: RPO=0 здесь ограничен подтверждёнными локальными транзакциями и отказом одного хоста; внешняя операция Pollen Tree и local audit не имеют распределённой атомарности.\nСхемы / адаптеры UC-4/UC-5, SMTP, scheduler, recovery automation и proxy-config — целевые доработки. Fake-клиент пыльцы запрещён в prod; календарный fake надо заменить перед выпуском.', [40, 2200, 3460, 260])

    edge('I1', 'browser', 'proxy', 'I1 · HTTPS :443', 'HTTPS TCP 443 / TLS 1.2+ / HTML JS CSS; REST JSON UTF-8; session + CSRF',
         [[510, 470], [650, 470], [650, 620], [860, 620]], [555, 525, 290, 65], 'SRS DC-6/7; §3.6.4; SecurityConfiguration')
    edge('I2', 'proxy', 'jvm', 'I2 · HTTP 127.0.0.1:8080 / REST JSON', 'HTTP TCP 127.0.0.1:8080; same host only; trusted Forwarded headers',
         [[1280, 790], [1280, 950]], [1320, 835, 720, 65], 'application-prod.yml; application.yml; proposed native proxy')
    edge('I3', 'jvm', 'postgres', 'I3 · JDBC / PostgreSQL 127.0.0.1:5432', 'JDBC / PostgreSQL wire TCP 127.0.0.1:5432; local DB credentials',
         [[1280, 1270], [1280, 1440]], [1320, 1320, 720, 65], 'SRS §3.6.3/4; application.yml')
    for ident, target, y, lane, start, label in [
        ('I4', 'pollen', 320, 2280, 1000, 'I4 · HTTPS :443'),
        ('I5', 'calendar', 595, 2330, 1050, 'I5 · HTTPS :443'),
        ('I6', 'metrics', 855, 2380, 1100, 'I6 · HTTPS :443 / pull'),
        ('I7', 'mail', 1115, 2430, 1150, 'I7 · SMTP :587 / TLS'),
    ]:
        protocol = 'HTTPS TCP 443 / TLS 1.2+ / JSON UTF-8 / API-key or mTLS'
        if ident == 'I4': protocol = 'HTTPS TCP 443 / TLS 1.2/1.3 / JSON UTF-8 / X-API-Key'
        if ident == 'I7': protocol = 'SMTP submission TCP 587 / required STARTTLS TLS 1.2+ / SMTP AUTH; proposed'
        edge(ident, 'jvm', target, label, protocol, [[2100, start], [lane, start], [lane, y], [2850, y]],
             [2470, y-75, 350, 65], 'SRS DC-12; §3.6.3/4; ST-00 notifications; Pollen Tree integration handoff')
    edge('I8', 'build-runtime', 'os', 'I8 · SSH/SFTP :22', 'SSH / SFTP TCP 22 / operator public key; manual upload and restart; proposed',
         [[510, 1300], [650, 1300], [650, 1390], [810, 1390]], [545, 1405, 265, 70], 'User manual deployment instruction; native FreeBSD design assumption')
    edge('I9', 'standby', 'postgres', 'I9 · replication TLS :5432', 'PostgreSQL streaming replication TCP 5432 / TLS verify-full / SCRAM-SHA-256 / replication role / synchronous remote_apply; standby initiates',
         [[2690, 1760], [2350, 1760], [2350, 1570], [2100, 1570]], [2380, 1640, 280, 85], 'SRS RR-1/RR-4/RR-5; proposed single-host-failure RPO=0 design')
    edge('I10', 'backup-runtime', 'postgres', 'I10 · backup / WAL TLS :5432', 'PostgreSQL replication protocol TCP 5432 / TLS verify-full / SCRAM-SHA-256 / dedicated backup role; pg_basebackup + pg_receivewal',
         [[2690, 2010], [2280, 2010], [2280, 1680], [2100, 1680]], [2310, 1880, 350, 80], 'SRS RR-4/RR-5; proposed off-host basebackup at least every 4 hours and continuous WAL')
    return dict(id='01-deployment', name='Deployment view — native FreeBSD / Helios', type='DeploymentDiagram', nodes=nodes, edges=edges)


def rect(n):
    return n['x'], n['y'], n['w'], n['h']


def intersects(a, b):
    x, y, w, h = a
    u, v, s, t = b
    return x < u+s and x+w > u and y < v+t and y+h > v


def perimeter(p, n):
    x, y, w, h = rect(n)
    return ((p[0] in (x, x+w) and y <= p[1] <= y+h) or
            (p[1] in (y, y+h) and x <= p[0] <= x+w))


def validate_geometry(view):
    nodes = {n['id']: n for n in view['nodes']}

    def ancestors(ident):
        result = set()
        while nodes[ident]['parent']:
            ident = nodes[ident]['parent']
            assert ident not in result, 'containment cycle'
            result.add(ident)
        return result

    for n in nodes.values():
        assert n['w'] > 0 and n['h'] > 0, 'empty node'
        if n['parent']:
            p = nodes[n['parent']]
            assert (p['x'] <= n['x'] and p['y'] <= n['y'] and n['x']+n['w'] <= p['x']+p['w'] and
                    n['y']+n['h'] <= p['y']+p['h']), 'containment '+n['id']
    for i, a in enumerate(view['nodes']):
        for b in view['nodes'][i+1:]:
            if a['id'] not in ancestors(b['id']) and b['id'] not in ancestors(a['id']):
                assert not intersects(rect(a), rect(b)), 'node overlap '+a['id']+'/'+b['id']
    for e in view['edges']:
        ps = e['points']
        assert len(ps) >= 2 and perimeter(ps[0], nodes[e['from']]) and perimeter(ps[-1], nodes[e['to']]), 'endpoint '+e['id']
        exempt = ancestors(e['from']) | ancestors(e['to'])
        for a, b in zip(ps, ps[1:]):
            assert a != b and (a[0] == b[0] or a[1] == b[1]), 'nonorthogonal '+e['id']
            for ident, n in nodes.items():
                if ident in exempt:
                    continue
                x, y, w, h = rect(n)
                if a[0] == b[0]:
                    hit = x < a[0] < x+w and max(min(a[1], b[1]), y) < min(max(a[1], b[1]), y+h)
                else:
                    hit = y < a[1] < y+h and max(min(a[0], b[0]), x) < min(max(a[0], b[0]), x+w)
                assert not hit, 'route through node '+e['id']+'/'+ident
        for ident, n in nodes.items():
            if ident not in (ancestors(e['from']) | ancestors(e['to'])):
                assert not intersects(e['caption'], rect(n)), 'caption overlap '+e['id']+'/'+ident
    for i, a in enumerate(view['edges']):
        for b in view['edges'][i+1:]:
            assert not intersects(a['caption'], b['caption']), 'caption overlap '+a['id']+'/'+b['id']


def generate(directory):
    directory.mkdir(parents=True, exist_ok=True)
    view = model()
    validate_geometry(view)
    document = ET.Element('deployment', id=view['id'], name=view['name'])
    for n in view['nodes']:
        ET.SubElement(document, 'element', {k: str(v) for k, v in n.items()})
    for e in view['edges']:
        values = {k: str(v) for k, v in e.items() if k not in ('points', 'caption')}
        values['points'] = ';'.join(','.join(map(str, p)) for p in e['points'])
        values['caption'] = ','.join(map(str, e['caption']))
        ET.SubElement(document, 'path', values)
    ET.indent(document)
    ET.ElementTree(document).write(directory/'deployment.xml', encoding='utf-8', xml_declaration=True)
    manifest = dict(view=view, sourceSHA256={p: hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in SOURCES})
    (directory/'source-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')


if __name__ == '__main__':
    generate(Path(sys.argv[1]))
