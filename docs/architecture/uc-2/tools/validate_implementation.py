"""Validate the edited-copy implementation and preservation of the user's other views."""
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET
from implementation_model import build_implementation
from validate_native import boundary, check, intersects, numbers


def assert_exported_flows(path, expected):
    svg = ET.parse(path).getroot()
    painted = Counter(json.loads(e.get('data'))['modelType'] for e in svg.iter() if e.get('data'))
    for kind, native_type in [('control', 'ControlFlow'), ('exception', 'ExceptionHandler')]:
        wanted = sum(e['kind'] == kind for e in expected['edges'])
        check(painted[native_type] == wanted, f'Invisible SVG flows: {native_type}: {painted[native_type]} != {wanted}')


def main(directory):
    project = directory/'SeasonPlan-UC2-implementation.vpp'
    check(project.read_bytes().startswith(b'SQLite format 3'), 'Not a native VPP')
    before = ET.parse(directory/'original-native.xml').getroot()
    after = ET.parse(directory/'native-reopened.xml').getroot()
    check(len(after.findall('diagram')) == len(before.findall('diagram')), 'Added an unwanted diagram')
    preserved = []
    for old in before.findall('diagram'):
        if old.get('name','').startswith('3.'):
            continue
        matches = [d for d in after.findall('diagram') if d.get('name') == old.get('name')]
        check(len(matches) == 1, 'Lost original diagram')
        old.tail = matches[0].tail = None
        check(ET.tostring(old) == ET.tostring(matches[0]), 'Changed unrelated diagram: '+old.get('name'))
        preserved.append(old.get('name'))
    expected = build_implementation()
    assert_exported_flows(directory/'03-implementation.svg', expected)
    matches = [d for d in after.findall('diagram') if d.get('name') == expected['name']]
    check(len(matches) == 1, 'Missing implementation activity')
    d = matches[0]
    shapes = {n.get('id'): n for n in d.findall('shape') if n.get('id')}
    edges = {e.get('id'): e for e in d.findall('edge') if e.get('id')}
    check(len(edges) == len(expected['edges']), 'Unexpected flows')
    for shape in d.findall('shape'):
        check(shape.get('type') not in {'NOTE','Class','ObjectNode','OutputPin'}, 'Unexpected non-activity detail')
        check(not re.search('[\u0400-\u04ff]',shape.get('name','')), 'Non-English implementation text')
        check(not re.search(r'proposed|modification|\btodo\b',shape.get('name',''),re.I), 'Placeholder description')
    bounds = {n['id']: numbers(shapes[n['id']].get('bounds')) for n in expected['nodes']}
    types = dict(initial='InitialNode',final='ActivityFinalNode',action='ActivityAction',decision='DecisionNode',merge='MergeNode',handler='StructuredActivityNode',fork='ForkNode',join='JoinNode')
    for n in expected['nodes']:
        s = shapes[n['id']]
        check(s.get('type') == types[n['kind']], 'Wrong UML2 node type')
        check(s.get('name') == n['text'], 'Lost concrete expression: '+n['id'])
        check(bounds[n['id']] == (n['x'],n['y'],(n['w']+19)//20*20,(n['h']+19)//20*20), 'Changed node geometry: '+n['id'])
        check(n['id'] in shapes[n['lane']].get('contains','').split(','), 'Lost partition membership')
        if n.get('callOwner'):
            check(s.get('operation') == n['callOwner']+'.'+n['callName'], 'Lost native operation link: '+n['id'])
            check(s.get('parameters','') == n.get('callParameters',''), 'Wrong call parameters: '+n['id'])
            check(s.get('returnType') == n['callReturn'], 'Wrong call result: '+n['id'])
        if n['kind'] in {'initial','final','fork','join'}:
            check(s.get('fill') == '-16777216', 'Nonstandard control-node fill')
        if n['kind'] == 'action':
            check(s.get('fill') == '-1641988', 'Action does not use the implemented-call fill')
    for e in expected['edges']:
        actual = edges[e['id']]
        ink = str((255<<24)+(30<<16)+(40<<8)+55-(1<<32))
        check(actual.get('lineColor') == ink and float(actual.get('lineWeight','0')) >= 1.5 and actual.get('lineTransparency') == '0' and actual.get('lineStyle') == 'Style1', 'Invisible connector style: '+e['id'])
        for attr in ('from','to','guard'):
            check(actual.get(attr) == e[attr], 'Wrong '+attr+': '+e['id'])
        check(not re.search('[\u0400-\u04ff]',actual.get('guard','')), 'Non-English guard')
        check(actual.get('type') == ('ExceptionHandler' if e['kind']=='exception' else 'ControlFlow'), 'Wrong flow model')
        check(actual.get('fromShape') == e['from'] and actual.get('toShape') == e['to'], 'Detached flow')
        points = [numbers(p) for p in actual.get('points').split(';')]
        bx,by,bw,bh = numbers(actual.get('lineBounds','0,0,0,0'))
        check(bw>0 and bh>0 and all(bx<=x<=bx+bw and by<=y<=by+bh for x,y in points), 'Connector drawing bounds clip its route: '+e['id'])
        check(boundary(points[0],bounds[e['from']]) and boundary(points[-1],bounds[e['to']]), 'Off-perimeter flow')
        for a,b in zip(points,points[1:]):
            if e['kind'] != 'exception':
                check(a[0] == b[0] or a[1] == b[1], 'Diagonal normal flow')
            for n in expected['nodes']:
                if n['kind'] != 'handler':
                    check(not intersects(a,b,bounds[n['id']]), 'Flow crosses node '+n['id'])
        if e['guard']:
            check(actual.get('captionSide') == '1', 'Nonstandard guard caption mode')
    check((directory/'03-implementation.svg').is_file() and (directory/'03-implementation.png').is_file(), 'Missing native previews')
    files = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in directory.iterdir() if p.suffix in {'.vpp','.xml','.svg','.png'}}
    receipt = dict(status='PASS',projectSHA256=files[project.name],preservedViews=preserved,implementation=dict(actions=sum(n['kind']=='action' for n in expected['nodes']),nodes=len(expected['nodes']),flows=len(edges),nativeCalls=sum(bool(n.get('callOwner')) for n in expected['nodes'])),checks=['English-only calls and expressions; no placeholders, class diagram, object nodes or notes','Concrete source-backed operation links with typed parameters/results','Original unrelated native views unchanged','Separate-process native reopen and exports','UML2 flow types, partition membership, black control nodes and attached obstacle-clearing routes'],limits=['No full desktop GUI automation','No live Pollen Tree or email-provider verification'],sha256=files)
    (directory/'validation.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(receipt,ensure_ascii=True,indent=2))


if __name__ == '__main__':
    main(Path(sys.argv[1]))
