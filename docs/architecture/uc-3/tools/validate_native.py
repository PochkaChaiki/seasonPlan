"""Validate reopened native VP objects/geometry, never mutate the VPP database."""
import hashlib
import json
import sys
from pathlib import Path
import xml.etree.ElementTree as ET


def ints(text):
    return tuple(map(int,text.split(',')))


def check_message(spec, native, key, lines):
    assert native.get('type') == 'Message', native.attrib
    for end in ('from','to'):
        expected = key+':'+spec.get(end)
        assert native.get(end) == expected, (spec.get('id'),end,native.attrib)
        assert native.get(end+'Shape') == expected, (spec.get('id'),end+'Shape')
    pts = [ints(p) for p in native.get('points').split(';')]
    assert len(set(pts)) >= 2
    for end,point in [('from',pts[0]),('to',pts[-1])]:
        x,y,w,h = lines[spec.get(end)]
        assert abs(point[0]-(x+w//2)) <= 5, (spec.get('id'),end,point,(x,y,w,h))
        assert y+70 <= point[1] <= y+h
    assert abs(pts[0][1]-int(spec.get('y'))) <= 2, (spec.get('id'),pts)
    assert native.get('name') == spec.get('text')
    assert native.get('captionSide') == '1'
    cap = ints(native.get('caption'))
    assert cap[2] > 40 and cap[3] >= 20
    if spec.get('operation'):
        assert native.get('operation') == 'op:'+spec.get('operation'), spec.get('id')
    for end in ('from', 'to'):
        assert native.get(end+'Activation','') == spec.get(end+'Activation',''), (spec.get('id'),end+'Activation')
    if spec.get('kind') == 'reply':
        assert native.get('actionType') == 'ActionTypeReturn', native.attrib


def check_activation(spec, native, key):
    assert native.get('type') == 'Activation', native.attrib
    assert native.get('lifeline') == key+':'+spec.get('lifeline'), native.attrib
    assert ints(native.get('bounds')) == tuple(int(spec.get(a)) for a in ('x','y','w','h')), spec.attrib


def validate(folder):
    folder=Path(folder)
    spec=ET.parse(folder/'sequences.xml').getroot()
    native=ET.parse(folder/'native-reopened.xml').getroot()
    ds={d.get('id'):d for d in native.findall('diagram')}
    assert set(ds) == {'00-packages'} | {d.get('id') for d in spec.findall('diagram')}
    operations={o.get('id'):o for o in native.findall('operation')}
    for op in spec.findall('operations/operation'):
        actual=operations['op:'+op.get('id')]
        assert actual.get('owner') == 'type:'+op.get('owner')
        assert actual.get('name') == op.get('name')
        assert actual.get('returns') == op.get('returns')
        expected=[]
        if op.get('parameters'):
            expected=[(p.rsplit(' ',1)[1],p.rsplit(' ',1)[0]) for p in op.get('parameters').split(';')]
        assert [(p.get('name'),p.get('type')) for p in actual.findall('parameter')] == expected, op.attrib
    counts={}
    for d in spec.findall('diagram'):
        key=d.get('id');nd=ds[key]
        interaction=next(i for i in native.findall('interaction') if i.get('id') == 'interaction:'+key)
        assert interaction.get('precondition') == d.get('precondition')
        assert interaction.get('postcondition') == d.get('postcondition')
        assert nd.get('type') == 'InteractionDiagram', nd.attrib
        shapes={s.get('id'):s for s in nd.findall('shape')}
        lines={}
        for line in d.findall('lifeline'):
            actual=shapes[key+':'+line.get('id')]
            assert actual.get('type') == 'InteractionLifeLine', actual.attrib
            assert actual.get('name') == line.get('name'), actual.attrib
            expected=tuple(int(line.get(a)) for a in ['x','y','w','h'])
            lines[line.get('id')]=ints(actual.get('bounds'))
            assert lines[line.get('id')] == expected, (key,line.get('id'),lines[line.get('id')],expected)
            if line.get('kind') == 'actor':
                assert actual.get('classifierType') == 'Actor'
                assert actual.get('classifier') == key+':actor-type:'+line.get('id')
            else:
                prefix=key+':' if key in ('01-use-case','02-logical') else ''
                assert actual.get('classifier') == 'type:'+prefix+line.get('type')
                assert actual.get('classifierPackage') == 'package:'+line.get('package')
            if key == '02-logical':
                assert actual.get('classifierType') != 'Actor'
                assert actual.get('showClassifier') == 'false'
        for symbol in d.findall('actor-symbol'):
            actual=shapes[symbol.get('id')]
            assert actual.get('type') == 'Actor'
            assert ints(actual.get('bounds')) == tuple(int(symbol.get(a)) for a in ('x','y','w','h'))
            assert int(symbol.get('h')) <= 70
            assert actual.get('shapeParent') == key+':'+symbol.get('lifeline')
        assert {s.get('id') for s in shapes.values() if s.get('type') == 'Activation'} == {a.get('id') for a in d.findall('activation')}
        for activation in d.findall('activation'):
            check_activation(activation, shapes[activation.get('id')], key)
        objects={l.get('id') for l in d.findall('lifeline') if l.get('kind') != 'actor'}
        assert {a.get('lifeline') for a in d.findall('activation')} == objects
        messages={m.get('id'):m for m in nd.findall('message')}
        assert len(messages) == len(d.findall('message'))
        for m in d.findall('message'):
            check_message(m,messages[m.get('id')],key,lines)
            if m.get('operand'):
                # VP stores messages in its relationship container; operand coverage is the native messages list.
                operand=shapes[m.get('operand')]
                assert m.get('id') in operand.get('messages').split(',')
                _,oy,_,oh=ints(operand.get('bounds'))
                ys=[ints(p)[1] for p in messages[m.get('id')].get('points').split(';')]
                assert min(ys) >= oy+40 and max(ys) <= oy+oh-8, (m.get('id'),'operand clearance')
            if m.get('replyTo'):
                assert messages[m.get('replyTo')].get('returnMessage') == m.get('id')
        for f in d.findall('fragment'):
            actual=shapes[f.get('id')]
            assert actual.get('type') == 'CombinedFragment'
            assert actual.get('operator') == f.get('operator')
            assert int(actual.get('covered')) == len(lines)
            if f.get('parent'):
                assert actual.get('parent') == f.get('parent')
            for o in f.findall('operand'):
                actual=shapes[o.get('id')]
                assert actual.get('type') == 'InteractionOperand'
                assert actual.get('parent') == f.get('id')
                assert actual.get('guard') == o.get('guard')
        for ref in d.findall('ref'):
            actual=shapes[key+':ref:'+ref.get('target')]
            assert actual.get('type') == 'InteractionOccurrence'
            assert actual.get('refersTo') == 'frame:'+ref.get('target')
            assert int(actual.get('covered')) == len(ref.get('covered').split(','))
        for suffix,text in [('title',d.get('name')),('notes',d.get('notes'))]:
            note=shapes[key+':'+suffix]
            assert note.get('type') == 'NOTE'
            assert note.get('name') == text and note.get('documentation') == text
        for ext in ('png','svg'):
            assert (folder/(key+'.'+ext)).stat().st_size > 1000
        counts[key]={'lifelines':len(lines),'activations':len(d.findall('activation')),'messages':len(messages),'fragments':len(d.findall('fragment')),'interactionUses':len(d.findall('ref'))}
    packages={s.get('id'):s for s in ds['00-packages'].findall('shape') if s.get('type')=='Package'}
    assert set(packages) == {'package:'+p.get('id') for p in spec.findall('packages/package')}
    source=ET.parse(Path(__file__).resolve().parents[4]/spec.find('packages').get('source')).getroot()
    canonical={p.get('id'):p for p in source.find("diagram[@id='01-system']").findall('package')}
    for p in spec.findall('packages/package'):
        if p.get('source'):
            original=canonical[p.get('source')]
            assert p.get('name') == ('frontend' if p.get('id') == 'frontend' else original.get('name'))
        assert p.get('name') not in {'fake','config'}
        assert packages['package:'+p.get('id')].get('name') == p.get('name')
        body=next(s for s in ds['00-packages'].findall('shape') if s.get('id') == 'body:'+p.get('id'))
        assert body.get('name') == p.get('body') and body.get('documentation') == p.get('body')
    logical=spec.find("diagram[@id='02-logical']")
    assert all(l.get('kind') != 'actor' for l in logical.findall('lifeline'))
    assert logical.find('message').get('from') == 'frontend'
    assert {l.get('package') for l in logical.findall('lifeline')} == {p.get('id') for p in spec.findall('packages/package')}
    report={'result':'PASS','project':'SeasonPlan-UC3-sequences.vpp',
        'sha256':hashlib.sha256((folder/'SeasonPlan-UC3-sequences.vpp').read_bytes()).hexdigest(),
        'diagrams':counts,'packageCount':len(packages),'typedOperationCount':len(operations),
        'checks':['separate-process native save/reopen','native UML interaction/actor-classified lifeline/message models',
                  'native bounded activations on every system participant, with message links and persisted bounds',
                  'logical view uses existing system packages, no actors, frontend initiates',
                  'message model/shape endpoints and attachment to lifeline center',
                  'unchanged lifeline bounds','typed call operations and parameter signatures',
                  'native reply action and call linkage','combined-fragment operators, nesting and guarded operand membership / geometry',
                  'per-interaction persisted preconditions and postconditions',
                  'InteractionUse native target-frame references and coverage','registered nonempty notes',
                  'normal caption mode','native reopened PNG/SVG exports'],
        'limits':['No full desktop drag/move automation; caption mode and persisted geometry checked through Open API.',
                  'Activation segments show local message/ref processing, not elapsed method duration or continuous execution across HTTP requests.',
                  'Target design: explicitly marked proposed calls and wiring are not implemented application functionality.',
                  'Pollen Tree provider compatibility, distributed consistency and production deployment not certified.']}
    (folder/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('PASS:',sum(v['messages'] for v in counts.values()),'native messages;',len(ds),'diagrams;',len(operations),'typed operations')
    return report


if __name__=='__main__':
    validate(sys.argv[1])
