"""Validate actual separate-process Open API inspection; never modify VPP SQLite."""
import copy
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
from deployment_model import ROOT, validate_geometry


def check(value, message):
    if not value:
        raise AssertionError(message)


def numbers(text):
    return list(map(int, text.split(',')))


def validate_tree(view, native):
    diagrams = native.findall('diagram')
    check(len(diagrams) == 1, 'Expected one detailed deployment view')
    d = diagrams[0]
    check(d.get('type') == 'DeploymentDiagram', 'Not a native DeploymentDiagram')
    check(d.get('name') == view['name'] and d.get('id') == view['id'], 'Wrong diagram identity')
    models = {m.get('id'): m for m in native.findall('model')}
    shapes = {s.get('id'): s for s in d.findall('shape')}
    check(set(shapes) == {n['id'] for n in view['nodes']}, 'Wrong native shape inventory')
    actual = copy.deepcopy(view)
    for n in actual['nodes']:
        ident = n['id']
        m, s = models[ident], shapes[ident]
        check(m.get('type') == n['type'] and s.get('type') == n['type'], 'Wrong model type '+ident)
        check(m.get('name') == n['name'] and s.get('name') == n['name'], 'Wrong model name '+ident)
        parent = n['parent'] or 'root'
        check(m.get('parent') == parent and s.get('parent') == parent, 'Wrong model parent '+ident)
        check(s.get('shapeParent') == n['parent'], 'Wrong shape parent '+ident)
        check(numbers(s.get('bounds')) == [n[k] for k in ('x', 'y', 'w', 'h')], 'Changed native bounds '+ident)
        if n['type'] == 'Node':
            check(m.get('stereotype', '') == n['stereotype'], 'Wrong node stereotype '+ident)
        if n['type'] == 'NOTE':
            check(s.get('documentation') == n['text'] == n['name'], 'Lost/changed native note '+ident)
        if n['type'] in ('Artifact', 'DeploymentSpecification'):
            dep = models.get('deploy:'+ident)
            check(dep is not None and dep.get('type') == 'Deployment' and dep.get('from') == n['parent'] and dep.get('to') == ident,
                  'Missing/reversed native deployment '+ident)
    edges = {e.get('id'): e for e in d.findall('edge')}
    check(set(edges) == {e['id'] for e in view['edges']}, 'Wrong CommunicationPath inventory')
    for e in actual['edges']:
        n = edges[e['id']]
        check(n.get('type') == 'Association', 'CommunicationPath must be UML Association '+e['id'])
        check(n.get('name') == e['name'], 'Changed path name '+e['id'])
        for field, expected in [('from', e['from']), ('to', e['to']), ('fromShape', e['from']), ('toShape', e['to'])]:
            check(n.get(field) == expected, 'Wrong endpoint '+e['id']+'/'+field)
        check(n.get('captionSide') == '1', 'Noninteractive caption mode '+e['id'])
        check(n.get('documentation') == e['protocol']+'\nEvidence: '+e['evidence'], 'Lost path interface documentation')
        e['points'] = [numbers(p) for p in n.get('points').split(';')]
        e['caption'] = numbers(n.get('caption'))
        check(e['caption'][2] > 0 and e['caption'][3] > 0, 'Empty caption')
    validate_geometry(actual)
    return dict(nodes=sum(n['type'] == 'Node' for n in view['nodes']),
                artifacts=sum(n['type'] == 'Artifact' for n in view['nodes']),
                deploymentSpecifications=sum(n['type'] == 'DeploymentSpecification' for n in view['nodes']),
                notes=sum(n['type'] == 'NOTE' for n in view['nodes']), communicationPaths=len(edges))


def validate(directory):
    project = directory/'SeasonPlan-deployment.vpp'
    check(project.is_file() and project.read_bytes().startswith(b'SQLite format 3'), 'Not a native VPP database')
    manifest = json.loads((directory/'source-manifest.json').read_text(encoding='utf-8'))
    counts = validate_tree(manifest['view'], ET.parse(directory/'native-reopened.xml').getroot())
    for suffix in ('.svg', '.png'):
        preview = directory/(manifest['view']['id']+suffix)
        check(preview.is_file() and preview.stat().st_size > 1000, 'Missing reopened native preview')
    for relative, expected in manifest['sourceSHA256'].items():
        check(hashlib.sha256((ROOT/relative).read_bytes()).hexdigest() == expected, 'Source changed during authoring: '+relative)
    hashes = {str(p.relative_to(directory)).replace('\\', '/'): hashlib.sha256(p.read_bytes()).hexdigest()
              for p in sorted(directory.rglob('*')) if p.is_file() and p.name != 'validation.json' and p.suffix in {'.vpp', '.xml', '.png', '.svg', '.json', '.py', '.java', '.ps1'}}
    receipt = dict(status='PASS', projectSHA256=hashes[project.name], diagram=counts,
                   checks=['Native UML DeploymentDiagram; Node device/executionEnvironment stereotypes; actual Artifact and DeploymentSpecification models',
                           'Native Deployment relations (VP API target -> artifact convention) and semantic + shape containment',
                           'CommunicationPaths are native Associations between deployment targets, with source-backed interface documentation',
                           'Separate-process save/reopen: names, exact bounds, ownership, registered note text, connector model and shape endpoints',
                           'Attached orthogonal routes; no unrelated shape intersections; clear normal SIDE_NONE name captions',
                           'SVG and PNG exported from separately reopened native project; source SHA-256 unchanged'],
                   limits=['Target architecture, not a live deployment or load/TLS/provider/backup/failover test',
                           'Proposed native nginx, PostgreSQL placement, recovery host, SMTP and adapters require infrastructure approval/implementation',
                           'RPO=0 limited to acknowledged local transactions under single-primary-host failure; no distributed Pollen Tree/audit atomicity',
                           'No full desktop GUI drag test; normal caption mode and native model editability checked via Open API',
                           'Native exports retain Visual Paradigm evaluation watermarks'], sha256=hashes)
    (directory/'validation.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(dict(status='PASS', projectSHA256=receipt['projectSHA256'], diagram=counts), indent=2))


if __name__ == '__main__':
    validate(Path(sys.argv[1]))
