"""Validate reopened native API state against the immutable source inventory."""
import hashlib
import html
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
from class_model import ROOT, validate_geometry


def validate_models(data, report):
    actual = {e.get('id'): e for e in report.findall('classifier')}
    assert set(actual) == {c['id'] for c in data['classifiers']}, 'Classifier identities differ'
    for c in data['classifiers']:
        e = actual[c['id']]
        for key in ('name', 'owner', 'package', 'kind', 'visibility', 'abstract', 'final'):
            expected = str(c[key]).lower() if isinstance(c[key], bool) else c[key]
            assert e.get(key) == expected, (c['id'], key, expected, e.get(key))
        for tag, keys, expected in [('attribute', ('name', 'type', 'visibility', 'static', 'readonly'), c['attributes']),
                                    ('operation', ('name', 'returns', 'visibility', 'static', 'abstract', 'constructor'), c['operations'])]:
            native = e.findall(tag)
            assert len(native) == len(expected), (c['id'], tag, len(native), len(expected))
            for source, saved in zip(expected, native):
                for key in keys:
                    value = str(source[key]).lower() if isinstance(source[key], bool) else source[key]
                    assert saved.get(key) == value, (c['id'], tag, key, value, saved.get(key))
                if tag == 'operation':
                    assert [p.attrib for p in saved.findall('parameter')] == source['parameters'], (c['id'],source['name'],'parameters')
        assert [l.get('name') for l in e.findall('literal')] == c['literals'], (c['id'],'literals')
    actual_edges = {e.get('id'): e for e in report.findall('relationship')}
    assert set(actual_edges) == {e['id'] for e in data['relationships']}, 'Relationship identities differ'
    for edge in data['relationships']:
        actual = actual_edges[edge['id']]
        reverse = edge['kind'] in ('realization', 'generalization')
        assert actual.get('from') == edge['to' if reverse else 'from'], edge
        assert actual.get('to') == edge['from' if reverse else 'to'], edge
        assert actual.get('kind') == edge['kind'], edge
        native_type = {'association':'Association', 'realization':'Realization', 'generalization':'Generalization', 'dependency':'Dependency'}[edge['kind']]
        assert actual.get('type') == native_type, (edge, actual.attrib)
        assert actual.get('name') == edge['name'], edge
        if edge['kind'] == 'association':
            assert actual.get('fromAggregation') in ('None','none'), actual.attrib
            assert actual.get('toAggregation') in ('None','none'), actual.attrib


def validate_svg(svg):
    assert svg.tag.endswith('svg'), 'Not a native SVG preview'
    text = ' '.join(svg.itertext())
    assert 'SeasonPlan source' not in text and 'snapshot::com::' not in text, 'Qualified owners crowd classifier names'


def validate(directory):
    directory=Path(directory)
    data=json.loads((directory/'source-manifest.json').read_text(encoding='utf-8'))
    report=ET.parse(directory/'native-reopened.xml').getroot()
    validate_models(data,report)
    for c in data['classifiers']:
        actual=next(e for e in report.findall('classifier') if e.get('id')==c['id'])
        required={'interface':'Interface','enum':'Enumeration','record':'record'}.get(c['kind'])
        if required: assert required in actual.get('stereotypes').split(';'), (c['id'],required)
    edges={e['id']:e for e in data['relationships']}
    native={e.get('id'):e for e in report.findall('diagram')}
    assert set(native)=={d['id'] for d in data['diagrams']}, 'Diagram set differs'
    captions=0
    index=['<!doctype html><meta charset="utf-8"><title>SeasonPlan class model index</title>',
           '<style>body{font:16px system-ui;max-width:1100px;margin:40px auto}li{margin:14px 0}code{font-size:13px}</style>',
           '<h1>SeasonPlan — complete class model</h1><p>All previews were exported from a separately reopened native .vpp. '
           'Member views show complete declarations; relationship views hide their compartments. Evaluation watermarks are retained.</p><ol>']
    for d in data['diagrams']:
        saved=native[d['id']]
        assert saved.get('type')=='ClassDiagram', saved.attrib
        shapes={e.get('id'):e for e in saved.findall('shape')}
        assert set(shapes)=={n['id'] for n in d['nodes']}|{'note:'+d['id']}, (d['id'],'shape identities')
        actual_nodes=[]
        for n in d['nodes']:
            s=shapes[n['id']];bounds=[int(v) for v in s.get('bounds').split(',')]
            assert bounds==[n[k] for k in ('x','y','w','h')], (d['id'],n['id'],bounds,n)
            assert s.get('attributes')==str(int(n['detail'])) and s.get('operations')==str(int(n['detail'])), s.attrib
            actual_nodes.append(dict(id=n['id'],**dict(zip(('x','y','w','h'),bounds))))
        note=shapes['note:'+d['id']]
        assert note.get('documentation') and note.get('name')==note.get('documentation'), note.attrib
        saved_edges={e.get('id'):e for e in saved.findall('edge')}
        assert set(saved_edges)=={e['id'] for e in d['edges']}, d['id']
        actual_edges=[]
        for e in d['edges']:
            s=saved_edges[e['id']];source=edges[e['id']]
            reverse=source['kind'] in ('realization','generalization')
            a,b=(source['to'],source['from']) if reverse else (source['from'],source['to'])
            assert s.get('from')==s.get('fromShape')==a and s.get('to')==s.get('toShape')==b, s.attrib
            ps=[tuple(map(int,p.split(','))) for p in s.get('points').split(';')]
            expected=[tuple(p) for p in e['points']]
            assert ps==(list(reversed(expected)) if reverse else expected), (d['id'],e['id'],'routes changed',ps,expected)
            actual_edges.append(dict(id=e['id'],**{'from':a,'to':b},points=ps))
            x,y,w,h=map(int,s.get('drawingBounds').split(','))
            assert w>0 and h>0 and all(x-3<=px<=x+w+3 and y-3<=py<=y+h+3 for px,py in ps), ('clipped connector',s.attrib)
            if source['name']:
                assert s.get('captionSide')=='1' and s.get('captionVisible')=='true', s.attrib
                _,_,w,h=map(int,s.get('caption').split(','));assert w>10 and h>10, s.attrib
                captions+=1
        validate_geometry(dict(nodes=actual_nodes,edges=actual_edges))
        for extension in ('svg','png'):
            assert (directory/'previews'/f'{d["id"]}.{extension}').stat().st_size>1000, d['id']
        svg=ET.parse(directory/'previews'/f'{d["id"]}.svg').getroot()
        validate_svg(svg)
        names=', '.join(n['id'].rsplit('.',1)[-1] for n in d['nodes'] if n['detail'])
        index.append(f'<li><b>{html.escape(d["name"])}</b> — <a href="previews/{d["id"]}.svg">SVG</a> · '
                     f'<a href="previews/{d["id"]}.png">PNG</a><br><code>{html.escape(names)}</code></li>')
    for path,digest in data['sourceSHA256'].items():
        assert hashlib.sha256((ROOT/path).read_bytes()).hexdigest()==digest, ('Source changed during generation',path)
    index.append('</ol>')
    (directory/'index.html').write_text('\n'.join(index),encoding='utf-8')
    receipt=dict(status='passed',project='SeasonPlan-all-classes.vpp',
        projectSHA256=hashlib.sha256((directory/'SeasonPlan-all-classes.vpp').read_bytes()).hexdigest(),
        classifiers=len(data['classifiers']),javaClassifiers=sum(not c['id'].startswith('frontend/') for c in data['classifiers']),
        frontendModules=sum(c['id'].startswith('frontend/') for c in data['classifiers']),
        attributes=sum(len(c['attributes']) for c in data['classifiers']),operations=sum(len(c['operations']) for c in data['classifiers']),
        enumerationLiterals=sum(len(c['literals']) for c in data['classifiers']),relationships=len(data['relationships']),diagrams=len(data['diagrams']),
        namedCaptionsInNormalMode=captions,
        verification='Separate VP process; native member signatures, ownership, types, relationship models/endpoints/routes/bounds, '
                     'notes, caption mode, source hashes and native SVG/PNG exports. No full desktop or mouse-drag automation.',
        sourceManifestSHA256=hashlib.sha256((directory/'source-manifest.json').read_bytes()).hexdigest())
    (directory/'validation.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(receipt,indent=2))


if __name__=='__main__':
    validate(sys.argv[1])
