"""Read-only validation of the separately reopened native VP communication project."""
import hashlib
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from communication_model import REPO, validate_geometry

PROJECT='SeasonPlan-UC5-communications.vpp'


def ints(value):return tuple(map(int,value.split(',')))
def rect(e):return tuple(int(e.get(a)) for a in ('x','y','w','h'))
def arrow_rect(e):
    x,y,w,h=rect(e)
    return (x,y,w,h)
def caption_rect(e):
    x,y,w,h=rect(e)
    return (x,y,w,h-12)


def on_perimeter(p,r,tolerance=3):
    x,y,w,h=r;px,py=p
    return (x-tolerance<=px<=x+w+tolerance and y-tolerance<=py<=y+h+tolerance and
            min(abs(px-x),abs(px-x-w),abs(py-y),abs(py-y-h))<=tolerance)


def check_link(spec,native,key,people):
    for end in ('from','to'):
        assert native.get(end)==key+':'+spec.get(end),(spec.attrib,native.attrib)
        assert native.get(end+'Shape')==key+':'+spec.get(end)
    pts=[ints(p) for p in native.get('points').split(';')]
    assert len(set(pts))>=2
    assert on_perimeter(pts[0],people[spec.get('from')]),(spec.get('id'),'from',pts,people)
    assert on_perimeter(pts[-1],people[spec.get('to')]),(spec.get('id'),'to',pts,people)
    for a,b in zip(pts,pts[1:]):assert a[0]==b[0] or a[1]==b[1],(spec.get('id'),pts)
    expected=[ints(p) for p in spec.get('points').split(';')]
    assert len(pts)==len(expected),(spec.get('id'),pts,expected)
    assert all(abs(a-b)<=2 for p,q in zip(pts,expected) for a,b in zip(p,q)),(spec.get('id'),pts,expected)


def check_message(spec,native,key):
    expected='CommunicationReverseMessage' if spec.get('reverse')=='true' else 'CommunicationMessage'
    assert native.get('shapeType')==expected,native.attrib
    for end in ('from','to'):assert native.get(end)==key+':'+spec.get(end)
    assert native.get('number')==spec.get('number')
    assert native.get('link')==spec.get('link')
    assert native.get('name')==spec.get('text')
    assert native.get('guard')==spec.get('guard')
    assert native.get('iteration')==spec.get('iteration')
    assert native.get('captionSide')=='1'
    assert native.get('operation','')==('op:'+spec.get('operation') if spec.get('operation') else '')


def validate(folder):
    folder=Path(folder)
    spec=ET.parse(folder/'communications.xml').getroot()
    native=ET.parse(folder/'native-reopened.xml').getroot()
    diagrams={d.get('id'):d for d in native.findall('diagram')}
    assert set(diagrams)=={d.get('id') for d in spec.findall('diagram')}
    operations={o.get('id'):o for o in native.findall('operation')}
    for op in spec.findall('operations/operation'):
        actual=operations['op:'+op.get('id')]
        for name in ('name','returns','visibility'):assert actual.get(name)==op.get(name),(op.attrib,actual.attrib)
        assert actual.get('owner')=='type:'+op.get('owner')
        params=[] if not op.get('parameters') else [tuple(p.rsplit(' ',1)) for p in op.get('parameters').split(';')]
        assert [(p.get('type'),p.get('name')) for p in actual.findall('parameter')]==params
        assert actual.get('documentation').startswith(op.get('status'))
    counts={}
    for d in spec.findall('diagram'):
        key=d.get('id'); nd=diagrams[key]
        assert nd.get('type')=='CommunicationDiagram',nd.attrib
        assert nd.get('sequenceNumbers')=='true'
        assert nd.get('precondition')==d.get('precondition')
        assert nd.get('postcondition')==d.get('postcondition')
        people={p.get('id'):p for p in nd.findall('participant')}
        assert set(people)=={key+':'+p.get('id') for p in d.findall('participant')}
        bounds={}
        for p in d.findall('participant'):
            actual=people[key+':'+p.get('id')]
            bounds[p.get('id')]=ints(actual.get('bounds'))
            assert bounds[p.get('id')]==rect(p),(p.attrib,actual.attrib)
            if p.get('kind')=='actor':
                assert actual.get('type')=='InteractionActor'
                assert actual.get('classifierType')=='Actor'
            else:
                assert actual.get('type')=='InteractionLifeLine'
                assert actual.get('classifierType')=='Class'
                prefix=key+':' if d.get('view')!='implementation' else ''
                assert actual.get('classifier')=='type:'+prefix+p.get('type')
                assert actual.get('package')=='package:'+p.get('package')
        links={e.get('id'):e for e in nd.findall('link')}
        assert set(links)=={l.get('id') for l in d.findall('link')}
        for link in d.findall('link'):check_link(link,links[link.get('id')],key,bounds)
        messages={e.get('id'):e for e in nd.findall('message')}
        assert set(messages)=={m.get('id') for m in d.findall('message')}
        for m in d.findall('message'):
            actual=messages[m.get('id')];check_message(m,actual,key)
            assert m.get('id') in links[m.get('link')].get('messages').split(',')
            assert ints(actual.get('bounds'))==arrow_rect(m),(m.attrib,actual.attrib)
            assert ints(actual.get('caption'))==caption_rect(m),(m.attrib,actual.attrib)
            assert actual.get('actionType')==('ActionTypeReturn' if m.get('kind')=='reply' else 'ActionTypeCall')
            if m.get('detail'):assert m.get('detail') in actual.get('detailDiagrams').split(',')
        first_reply={}
        for m in d.findall('message'):
            if m.get('replyTo'):first_reply.setdefault(m.get('replyTo'),m.get('id'))
        for call,reply in first_reply.items():assert messages[call].get('returnMessage')==reply
        notes={n.get('id'):n for n in nd.findall('note')}
        for suffix,text in [('title',d.get('name')),('notes',d.get('notes'))]:
            n=notes[key+':'+suffix]
            assert n.get('name')==text and n.get('documentation')==text
        validate_geometry(d)
        for ext in ('png','svg'):assert (folder/(key+'.'+ext)).stat().st_size>1000
        counts[key]={'participants':len(people),'links':len(links),'messages':len(messages)}
    for p in spec.findall('packages/package'):
        actual=next(q for q in native.findall('package') if q.get('id')=='package:'+p.get('id'))
        assert actual.get('name')==p.get('name')
    manifest=json.loads((folder/'source-manifest.json').read_text(encoding='utf-8'))
    for path,digest in manifest['sourceHashes'].items():
        assert hashlib.sha256((REPO/path).read_bytes()).hexdigest()==digest,('source changed',path)
    result={'result':'PASS','project':PROJECT,'sha256':hashlib.sha256((folder/PROJECT).read_bytes()).hexdigest(),
            'specificationSha256':hashlib.sha256((folder/'communications.xml').read_bytes()).hexdigest(),
            'reopenedInspectionSha256':hashlib.sha256((folder/'native-reopened.xml').read_bytes()).hexdigest(),
            'diagrams':counts,'typedOperationCount':len(operations),
            'checks':['separate-process native save/reopen','native CommunicationDiagram (not legacy Collaboration or sequence)',
                      'real typed participants and Actor symbols','IInteractionLifeLineLink endpoint/shape agreement and perimeter geometry',
                      'native IMessage membership, forward/reverse views, sequence numbers, guards, iteration and call operations',
                      'native reply actions and first-success reply linkage',
                      'typed operation signatures, visibility and source evidence','persisted bounds and nonoverlapping nodes/message labels',
                      'native classifier subdiagram navigation to source-snapshot detail','registered notes with persisted prose',
                      'native reopened PNG/SVG exports','input source SHA-256 provenance'],
            'limits':['No full desktop mouse drag/move automation; native Open API/persistence checks only.',
                      'Target API/UI, report/source persistence adapters, composition and real PDF/HTML renderers are not implemented.',
                      'Local transaction does not guarantee an atomic snapshot across Pollen Tree and local sources.',
                      'Provider contract/deployment not certified; VP evaluation watermarks retained.']}
    (folder/'validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('PASS:',len(diagrams),'communication diagrams;',sum(c['messages'] for c in counts.values()),'messages;',len(operations),'typed operations')
    return result


if __name__=='__main__':validate(sys.argv[1])
