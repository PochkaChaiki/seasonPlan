"""Complete source-backed classifier inventory and deterministic native UML geometry."""
import copy
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
TOOLS = Path(__file__).resolve().parent
DOT = Path('X:/apps/graphviz/dot.exe')


def extract_java(root):
    with tempfile.TemporaryDirectory(prefix='seasonplan-ast-') as temp:
        subprocess.run(['javac', '--release', '17', '-encoding', 'UTF-8', '-d', temp,
                        str(TOOLS / 'JavaSourceInventory.java')], check=True, capture_output=True)
        output = subprocess.run(['java', '-cp', temp, 'JavaSourceInventory', str(root)],
                                check=True, capture_output=True, encoding='utf-8').stdout
    result = []
    for e in ET.fromstring(output).findall('classifier'):
        c = dict(e.attrib)
        for key in ('abstract', 'final'):
            c[key] = c[key] == 'true'
        c['attributes'], c['operations'] = [], []
        c['literals'] = [v.get('name') for v in e.findall('literal')]
        c['imports'] = c['imports'].split(';')
        c['extends'] = [v.get('type') for v in e.findall('extends')]
        c['implements'] = [v.get('type') for v in e.findall('implements')]
        for a in e.findall('attribute'):
            item = dict(a.attrib)
            for key in ('static', 'readonly'):
                item[key] = item[key] == 'true'
            c['attributes'].append(item)
        for o in e.findall('operation'):
            item = dict(o.attrib)
            for key in ('constructor', 'static', 'abstract'):
                item[key] = item[key] == 'true'
            item['parameters'] = [dict(p.attrib) for p in o.findall('parameter')]
            c['operations'].append(item)
        result.append(c)
    return result


def extract_frontend(root):
    return json.loads(subprocess.run(['node', str(TOOLS / 'frontend_inventory.cjs'), str(root)],
                                    check=True, capture_output=True, encoding='utf-8').stdout)


def inventory():
    backend = extract_java(ROOT / 'backend/src/main/java')
    frontend = extract_frontend(ROOT / 'frontend/src')
    for c in backend:
        c['source'] = 'backend/src/main/java/' + c['source']
    for c in frontend:
        c['source'] = 'frontend/src/' + c['source']
    classifiers = backend + frontend
    by_id = {c['id']: c for c in classifiers}
    edges = {}

    def edge(a, b, kind, evidence, name=''):
        if a == b:
            return
        key = (a, b, kind)
        e = edges.setdefault(key, dict(id='r' + str(len(edges)), **{'from': a, 'to': b},
                                       kind=kind, name=name, evidence=[]))
        e['evidence'].append(evidence)
        if name and name not in e['name'].split(', '):
            e['name'] = ', '.join(filter(None, [e['name'], name]))

    def resolve_direct(c, token):
        if token in by_id:
            return token
        # Try enclosing declaration scopes before package/import candidates.
        scope = c['id']
        while scope.startswith(c['package']):
            if scope + '.' + token in by_id:
                return scope + '.' + token
            scope = scope.rsplit('.', 1)[0]
        if c['package'] + '.' + token in by_id:
            return c['package'] + '.' + token
        for imp in c['imports']:
            if imp.endswith('.*') and imp[:-1] + token in by_id:
                return imp[:-1] + token
            simple = imp.rsplit('.', 1)[-1]
            if token == simple and imp in by_id:
                return imp
            if token.startswith(simple + '.') and imp + token[len(simple):] in by_id:
                return imp + token[len(simple):]
        return None

    def resolve(c, token):
        direct = resolve_direct(c, token)
        if direct:
            return direct
        # Java member types are inherited, including by nested declarations of an
        # implementing class. Search project supertype chains, never guess by a
        # globally unique simple name.
        pending = []
        scope = c['id']
        while scope in by_id:
            pending.append(by_id[scope])
            scope = scope.rsplit('.', 1)[0]
        visited = set()
        while pending:
            context = pending.pop(0)
            if context['id'] in visited:
                continue
            visited.add(context['id'])
            for declared in context.get('extends', []) + context.get('implements', []):
                base = resolve_direct(context, declared.split('<', 1)[0])
                if base:
                    if base + '.' + token in by_id:
                        return base + '.' + token
                    pending.append(by_id[base])
        return None

    def references(c, text):
        return sorted({value for token in re.findall(r'[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*', text)
                       if (value := resolve(c, token)) and value != c['id']})

    for c in backend:
        for kind in ('extends', 'implements'):
            for name in c[kind]:
                for target in references(c, name):
                    edge(c['id'], target, 'realization' if kind == 'implements' else 'generalization', name)
        for a in c['attributes']:
            for target in references(c, a['type']):
                edge(c['id'], target, 'association', 'field ' + a['name'] + ': ' + a['type'], a['name'])
        for o in c['operations']:
            for target in references(c, o['returns'] + ' ' + ' '.join(p['type'] for p in o['parameters'])):
                if not any((c['id'], target, k) in edges for k in ('association', 'generalization', 'realization')):
                    edge(c['id'], target, 'dependency', 'signature ' + o['name'])
        # Exact project type tokens used in bodies, including static qualifiers.
        for target in references(c, c['declaration']):
            if not any((c['id'], target, k) in edges for k in ('association', 'generalization', 'realization', 'dependency')):
                edge(c['id'], target, 'dependency', 'source type reference')

    for c in frontend:
        for imp in c['imports']:
            if not imp.startswith('.'):
                continue
            import posixpath
            target = 'frontend/' + posixpath.normpath(posixpath.join(posixpath.dirname(c['id'][9:]), imp))
            if target in by_id:
                edge(c['id'], target, 'dependency', 'ES module import ' + imp)
    # HTTP boundaries are explicitly contracts, not direct Java calls or ownership.
    api = 'frontend/api/lifecycleApi.js'
    for target in ('com.seasonplan.lifecycle.api.LifecycleController', 'com.seasonplan.lifecycle.api.NotificationController'):
        edge(api, target, 'dependency', 'HTTP /api proxy prefix; backend root-relative REST routes', 'REST / JSON')
    for target in ('com.seasonplan.security.AuthController', 'com.seasonplan.security.AdminUserController'):
        edge('frontend/auth/AuthContext.jsx', target, 'dependency', 'HTTP auth/admin routes in AuthContext', 'REST / JSON')
    paths = [p for folder in ('backend/src/main/java', 'frontend/src') for p in (ROOT / folder).rglob('*')
             if p.is_file() and '.test.' not in p.name]
    paths += [ROOT / p for p in ('AGENTS.md', 'arch_docs/VISION.md', 'arch_docs/USE_CASE_SPECIFICATION.md',
                                'arch_docs/SYSTEM_REQUIREMENTS_SPECIFICATION.md',
                                'docs/architecture/ST-00-specification-resolution.md', 'backend/pom.xml', 'frontend/package.json')]
    return dict(classifiers=classifiers, relationships=list(edges.values()),
                sourceSHA256={p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(paths)})


def member_lines(c):
    lines = [c['name']]
    lines += [a['name'] + ': ' + a['type'] for a in c['attributes']]
    for o in c['operations']:
        lines.append(o['name'] + '(' + ', '.join(p['name'] + ': ' + p['type'] for p in o['parameters']) + ')' +
                     (': ' + o['returns'] if o['returns'] else ''))
    return lines + c['literals']


def build_diagrams(data):
    groups = {}
    for c in data['classifiers']:
        groups.setdefault(c['package'], []).append(c)
    result = []
    for package, classifiers in sorted(groups.items()):
        suffix = package.replace('com.seasonplan.', '').replace('frontend/src/', 'frontend/').replace('/', '.').strip('.')
        for start in range(0, len(classifiers), 6):
            cs = classifiers[start:start+6]
            nodes, y = [], 150
            for row in range(0, len(cs), 2):
                x, height = 60, 0
                for c in cs[row:row+2]:
                    lines = member_lines(c)
                    w = max(460, min(3600, max(len(line) for line in lines) * 8 + 85))
                    # Wrapped long members receive extra height. Class model remains unabridged.
                    count = sum(max(1, (len(line)*8+60)//(w-45)+1) for line in lines)
                    h = max(130, count*21 + 90)
                    nodes.append(dict(id=c['id'], detail=True, x=x, y=y, w=w, h=h))
                    x += w + 70
                    height = max(height, h)
                y += height + 80
            result.append(dict(id=f'{len(result)+1:02d}-members', name=f'{suffix} — members {start//6+1}',
                               nodes=nodes, edges=[], intro='Full declared members · source snapshot · no generated boilerplate'))
        # Separate collaboration view avoids routing lines through dense member compartments.
        outgoing = [e for e in data['relationships'] if e['from'] in {c['id'] for c in classifiers}]
        if outgoing:
            # Small focal groups bound graph size while showing every relationship at least once.
            primary_ids = [c['id'] for c in classifiers if any(e['from'] == c['id'] for e in outgoing)]
            for start in range(0, len(primary_ids), 4):
                focus = set(primary_ids[start:start+4])
                es = [copy.deepcopy(e) for e in outgoing if e['from'] in focus]
                ids = sorted(focus | {e['to'] for e in es})
                result.append(graph_layout(dict(id=f'{len(result)+1:02d}-relations', name=f'{suffix} — relationships {start//4+1}',
                    nodes=[dict(id=i, detail=False, w=max(330, len(data_name(data,i))*9+55), h=95) for i in ids],
                    edges=es, intro='Source-backed relationships · member compartments hidden only in this view')))
    return result


def data_name(data, ident):
    return next(c['name'] for c in data['classifiers'] if c['id'] == ident)


def graph_layout(view):
    ids = {n['id']: 'n'+str(i) for i,n in enumerate(view['nodes'])}
    lines = ['digraph classes { graph [rankdir=LR,splines=ortho,nodesep=0.9,ranksep=3.8,pad=0];',
             'node [shape=box,fixedsize=true,label=""]; edge [arrowhead=none,arrowtail=none];']
    for n in view['nodes']:
        lines.append(f'{ids[n["id"]]} [width={n["w"]/72},height={n["h"]/72}];')
    for e in view['edges']:
        lines.append(f'{ids[e["from"]]} -> {ids[e["to"]]};')
    lines.append('}')
    graph = json.loads(subprocess.run([str(DOT), '-Tjson'], input='\n'.join(lines), text=True, capture_output=True, check=True).stdout)
    height = float(graph['bb'].split(',')[3])
    for n,obj in zip(view['nodes'], graph['objects']):
        cx,cy = map(float,obj['pos'].split(','))
        n['x'],n['y'] = round(cx-n['w']/2)+60, round(height-cy-n['h']/2)+150
    for e,obj in zip(view['edges'],graph['edges']):
        ps = []
        for token in obj['pos'].split():
            x,y = map(float,token.split(',')); p = (round(x)+60, round(height-y)+150)
            if ps and p == ps[-1]:
                continue
            if len(ps)>1 and (ps[-2][0]==ps[-1][0]==p[0] or ps[-2][1]==ps[-1][1]==p[1]):
                ps[-1]=p
            else:
                ps.append(p)
        e['points'] = ps
    validate_geometry(view)
    return view


def boundary(point, rect):
    x,y,w,h = rect; a,b = point
    return x-2<=a<=x+w+2 and y-2<=b<=y+h+2 and min(abs(a-x), abs(a-x-w), abs(b-y), abs(b-y-h))<=2


def validate_geometry(view):
    rects = {n['id']: (n['x'],n['y'],n['w'],n['h']) for n in view['nodes']}
    for ident,r in rects.items():
        x,y,w,h = r
        assert w>0 and h>0, ident
        for other,q in rects.items():
            if ident<other:
                assert not (x<q[0]+q[2] and x+w>q[0] and y<q[1]+q[3] and y+h>q[1]), (ident,other)
    for e in view['edges']:
        ps=e['points']; assert len(ps)>1 and len(set(ps))>1, e
        assert boundary(ps[0],rects[e['from']]) and boundary(ps[-1],rects[e['to']]), e
        for a,b in zip(ps,ps[1:]):
            assert a[0]==b[0] or a[1]==b[1], e
            for ident,(x,y,w,h) in rects.items():
                hit = (x+2<a[0]<x+w-2 and max(min(a[1],b[1]),y+2)<min(max(a[1],b[1]),y+h-2)) if a[0]==b[0] else (
                    y+2<a[1]<y+h-2 and max(min(a[0],b[0]),x+2)<min(max(a[0],b[0]),x+w-2))
                assert not hit, (ident,e)


def generate(output):
    output = Path(output); output.mkdir(parents=True,exist_ok=True)
    data = inventory(); data['diagrams'] = build_diagrams(data)
    (output/'source-manifest.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    root = ET.Element('class-project')
    def attrs(values):
        return {k: str(v).lower() if isinstance(v,bool) else str(v) for k,v in values.items() if not isinstance(v,(list,dict))}
    for c in data['classifiers']:
        ce = ET.SubElement(root,'classifier',attrs({k:v for k,v in c.items() if k!='declaration'}))
        for a in c['attributes']: ET.SubElement(ce,'attribute',attrs(a))
        for o in c['operations']:
            oe=ET.SubElement(ce,'operation',attrs(o))
            for p in o['parameters']: ET.SubElement(oe,'parameter',attrs(p))
        for literal in c['literals']: ET.SubElement(ce,'literal',name=literal)
    for e in data['relationships']:
        ET.SubElement(root,'relationship',attrs({**e,'evidence':'\n'.join(e['evidence'])}))
    for d in data['diagrams']:
        de=ET.SubElement(root,'diagram',attrs(d))
        for n in d['nodes']: ET.SubElement(de,'node',attrs(n))
        for e in d['edges']:
            ET.SubElement(de,'edge',id=e['id'],points=';'.join(f'{x},{y}' for x,y in e['points']))
    ET.indent(root); ET.ElementTree(root).write(output/'classes.xml',encoding='utf-8',xml_declaration=True)
    print(f"Inventory: {len(data['classifiers'])} classifiers, {sum(len(c['operations']) for c in data['classifiers'])} operations, "
          f"{len(data['relationships'])} relationships, {len(data['diagrams'])} diagrams")


if __name__ == '__main__':
    generate(sys.argv[1])
