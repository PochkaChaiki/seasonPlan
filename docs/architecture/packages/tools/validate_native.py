"""Validate a separate-process VP reopen against the source/layout manifest."""
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
from package_model import ROOT, boundary, intersects, validate_geometry


def check(value, message):
    if not value:
        raise AssertionError(message)


def numbers(value):
    return tuple(map(int,value.split(',')))


def validate_heading_notes(diagram, spec):
    for kind, text in [('title', spec['name']+'\n'+spec['intro']), ('legend', spec['notes'])]:
        matches=[s for s in diagram.findall('shape') if s.get('id')==kind+':'+spec['id']]
        check(len(matches)==1 and matches[0].get('name')==text and matches[0].get('documentation')==text,
              'Wrong '+kind+' text in '+spec['id'])


def validate(directory):
    project=directory/'SeasonPlan-packages.vpp'
    check(project.is_file(),'Missing native project: '+str(project))
    check(project.read_bytes().startswith(b'SQLite format 3'),'Not a native VPP database')
    manifest=json.loads((directory/'source-manifest.json').read_text(encoding='utf-8'))
    native=ET.parse(directory/'native-reopened.xml').getroot()
    check(len(native.findall('diagram'))==2,'Expected two native package views')
    package_models={m.get('id'):m for m in native.findall('model')}
    for p in manifest['packageInventory']:
        check(p in package_models,'Missing package model '+p)
        if '.' in p:
            check(package_models[p].get('parent')==p.rsplit('.',1)[0],'Incorrect package ownership '+p)
    results=[]
    for spec in manifest['views']:
        matches=[d for d in native.findall('diagram') if d.get('id')==spec['id']]
        check(len(matches)==1,'Missing or repeated diagram '+spec['id'])
        d=matches[0]
        check(d.get('type')=='PackageDiagram','Not a native package diagram')
        check(d.get('name')==spec['name'],'Diagram name changed')
        shapes={s.get('id'):s for s in d.findall('shape') if s.get('type')=='Package'}
        edges={e.get('id'):e for e in d.findall('edge')}
        check(set(shapes)==set(spec['nodes']),'Wrong native package identities')
        check(set(edges)=={e['id'] for e in spec['edges']},'Wrong dependency identities')
        for ident,n in spec['nodes'].items():
            s=shapes[ident]
            check(s.get('name')==n['name'],'Wrong package name '+ident)
            check(numbers(s.get('bounds'))==tuple(n[k] for k in ('x','y','w','h')),'Moved/resized package '+ident+': '+s.get('bounds'))
            notes=[s for s in d.findall('shape') if s.get('id')=='body:'+ident]
            check(len(notes)==1 and notes[0].get('name')==n['body'],'Lost body note '+ident)
            check(notes[0].get('parent')==ident,'Lost body-note model ownership '+ident)
        actual=dict(nodes={k:dict(n) for k,n in spec['nodes'].items()},edges=[])
        label_bounds=[]
        for expected in spec['edges']:
            e=edges[expected['id']]
            check(e.get('type')=='Dependency','Relationship is not UML Dependency')
            for native_key,wanted in [('from',expected['from']),('to',expected['to']),
                                       ('fromShape',expected['from']),('toShape',expected['to'])]:
                check(e.get(native_key)==wanted,'Wrong endpoint '+expected['id']+'/'+native_key)
            check(e.get('name')==expected['name'],'Lost relationship name')
            ps=[numbers(p) for p in e.get('points').split(';')]
            actual['edges'].append(dict(expected,points=ps))
            if expected['name']:
                check(e.get('captionSide')=='1','Caption not in normal draggable mode')
                rect=numbers(e.get('caption'));check(rect[2]>0 and rect[3]>0,'Empty caption')
                x,y,w,h=rect
                for n in spec['nodes'].values():
                    check(not (x<n['x']+n['w'] and x+w>n['x'] and y<n['y']+n['h'] and y+h>n['y']), 'Caption overlaps package')
                label_bounds.append(rect)
        validate_geometry(actual)
        notes=[s for s in d.findall('shape') if s.get('type')=='NOTE']
        check(len(notes)==len(spec['nodes'])+2,'Unexpected note count')
        check(all(s.get('name','').strip() for s in notes),'Empty/unregistered native note')
        check(all(s.get('documentation')==s.get('name') for s in notes),
              'NOTE documentation must contain only visible prose, not identity metadata')
        validate_heading_notes(d, spec)
        for suffix in ('.png','.svg'):
            path=directory/(spec['id']+suffix)
            check(path.is_file() and path.stat().st_size>1000,'Missing native reopened export')
        svg=(directory/(spec['id']+'.svg')).read_text(encoding='utf-8')
        check('Evaluation' in svg or 'evaluation' in svg,'Expected VP evaluation-export provenance')
        results.append(dict(diagram=spec['id'],packages=len(shapes),dependencies=len(edges),notes=len(notes),normalNameCaptions=len(label_bounds)))
    for relative,wanted in manifest['sourceSHA256'].items():
        check(hashlib.sha256((ROOT/relative).read_bytes()).hexdigest()==wanted,'Source changed during authoring: '+relative)
    hashes={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in directory.iterdir()
            if p.suffix in {'.vpp','.svg','.png','.xml'} or p.name=='source-manifest.json'}
    receipt=dict(status='PASS',projectSHA256=hashes[project.name],diagrams=results,
                 checks=['Source import dependencies and evidence manifest','Native package model hierarchy',
                         'Two genuine PackageDiagram views; Dependency relationships',
                         'Separate-process save/reopen; exact package bounds and note text',
                         'Correct model and shape endpoints; orthogonal perimeter-attached routes',
                         'No package overlaps or routes through package interiors',
                         'Normal SIDE_NONE relationship-name captions and clear caption bounds',
                         'PNG/SVG exported from separately reopened native project',
                         'Source hashes unchanged during generation'],
                 limits=['No full desktop GUI selection/drag automation; caption mode checked via Open API only',
                         'Diagrams document code and known gaps, not live provider or deployed runtime verification',
                         'Java import graph, not a complete call graph; third-party/library edges omitted',
                         'Native exports retain Visual Paradigm evaluation watermarks'],sha256=hashes)
    (directory/'validation.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(receipt,ensure_ascii=True,indent=2))


if __name__=='__main__':
    validate(Path(sys.argv[1]))
