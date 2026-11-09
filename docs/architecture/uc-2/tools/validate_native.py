"""Read-only regression validation of a SEPARATE-PROCESS VP reopen/export receipt."""
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
from diagram_model import build_diagrams, PACKAGES, validate


def check(condition, message):
    if not condition:
        raise AssertionError(message)


def numbers(text):
    return tuple(int(v) for v in text.split(','))


def boundary(point, rect):
    x,y,w,h=rect;px,py=point
    return x-2 <= px <= x+w+2 and y-2 <= py <= y+h+2 and min(abs(px-x),abs(px-x-w),abs(py-y),abs(py-y-h)) <= 2


def intersects(a,b,rect):
    x,y,w,h=rect
    if a[0]!=b[0] and a[1]!=b[1]:
        return max(min(a[0],b[0]),x+2)<min(max(a[0],b[0]),x+w-2) and max(min(a[1],b[1]),y+2)<min(max(a[1],b[1]),y+h-2)
    if a[0] == b[0]:
        return x+2<a[0]<x+w-2 and max(min(a[1],b[1]),y+2)<min(max(a[1],b[1]),y+h-2)
    return y+2<a[1]<y+h-2 and max(min(a[0],b[0]),x+2)<min(max(a[0],b[0]),x+w-2)


def main(directory):
    project=directory/'SeasonPlan-UC2-activities.vpp'
    check(project.is_file(),f'Missing native project: {project}')
    check(project.read_bytes().startswith(b'SQLite format 3'), 'Not a native SQLite VPP database')
    native=ET.parse(directory/'native-reopened.xml').getroot()
    diagrams=native.findall('diagram')
    check(len(diagrams)==4, 'Expected three activities and one package reference')
    results=[]
    for expected in build_diagrams():
        validate(expected)
        found=[d for d in diagrams if d.get('name')==expected['name']]
        check(len(found)==1, f"Missing diagram {expected['id']}")
        d=found[0]
        shapes={n.get('id'):n for n in d.findall('shape') if n.get('id')}
        edges={e.get('id'):e for e in d.findall('edge') if e.get('id')}
        check(len(edges)==len(expected['edges']),f"Control flow count {expected['id']}")
        bounds={n['id']:numbers(shapes[n['id']].get('bounds')) for n in expected['nodes']}
        type_map=dict(initial='InitialNode', final='ActivityFinalNode', action='ActivityAction', decision='DecisionNode', merge='MergeNode', object='ObjectNode', handler='StructuredActivityNode', fork='ForkNode', join='JoinNode')
        for n in expected['nodes']:
            shape=shapes[n['id']]
            check(shape.get('type')==type_map[n['kind']],f"Wrong UML2 type {n['id']}: {shape.get('type')}")
            wanted=(n['x'],n['y'],(n['w']+19)//20*20,(n['h']+19)//20*20)
            check(bounds[n['id']]==wanted,f"Moved/resized {expected['id']}/{n['id']}: {bounds[n['id']]} != {wanted}")
            if n['text']:
                check(shape.get('name')==n['text'],f"Lost action text {n['id']}")
            if n.get('parent'):
                check(shape.get('parent')==n['parent'],f"Lost structured handler ownership {n['id']}")
            if n['kind']=='object':
                check(shape.get('typeRef')==n['type'],f"Wrong native object type {n['id']}")
            if n['kind'] in {'initial','final','fork','join'}:
                check(shape.get('fill')=='-16777216',f"UML control node must be black: {n['id']}")
            partition=shapes[n['lane']]
            check(n['id'] in partition.get('contains','').split(','),f"Missing partition membership {n['id']}")
        for e in expected['edges']:
            edge=edges[e['id']]
            for field in ('from','to','guard'):
                check(edge.get(field)==e[field],f"Wrong {field}: {e['id']}")
            wanted_type={'control':'ControlFlow','object':'ActivityObjectFlow','exception':'ExceptionHandler'}[e['kind']]
            check(edge.get('type')==wanted_type,f"Wrong UML2 flow type: {e['id']}")
            if e['kind']=='object':
                check(edge.get('nativeFromType')=='OutputPin',f"ObjectFlow must originate at typed OutputPin: {e['id']}")
            if e['kind']=='exception':
                check(edge.get('nativeToType')=='InputPin' and edge.get('exceptionTypes')=='1',f"Missing typed exception input/catch type {e['id']}")
            check(edge.get('fromShape')==e['from'] and edge.get('toShape')==e['to'],f"Unattached {e['id']}")
            points=[numbers(p) for p in edge.get('points').split(';')]
            check(len(set(points))>=2,f"Empty route {e['id']}")
            check(boundary(points[0],bounds[e['from']]) and boundary(points[-1],bounds[e['to']]),f"Off-perimeter {expected['id']}/{e['id']}: {points}")
            for a,b in zip(points,points[1:]):
                if e['kind']!='exception':
                    check(a[0]==b[0] or a[1]==b[1],f"Diagonal {e['id']}")
                for ident,rect in bounds.items():
                    if next(n for n in expected['nodes'] if n['id']==ident)['kind']=='handler':
                        continue
                    check(not intersects(a,b,rect),f"Route through {ident}: {expected['id']}/{e['id']}")
            if e['guard']:
                check(edge.get('captionSide')=='1',f"Nonstandard guard caption mode {e['id']}")
        if expected['id']=='02-logical':
            for p in PACKAGES:
                check(shapes[p['id']].get('represents')=='package-'+p['id'],f"Partition/package mismatch {p['id']}")
        if expected['id']=='03-implementation':
            for ident,op in {'load':'SeasonPlanService.get','history':'AuditLogService.entriesFor','verify':'ApprovalService.verify','pollen-read':'RestPollenTreeClient.findLatestBySeasonPlanId','approve':'ApprovalService.approve','return':'ApprovalService.returnForRevision','audit':'AuditLogService.append','save':'JdbcSeasonPlanRepository.save','outbox':'NotificationOutbox.enqueue'}.items():
                check(shapes[ident].get('operation')==op,f"Missing native CallOperationAction {ident}")
            check('List<ApprovalCondition> conditions' in shapes['approve'].get('parameters',''),'Missing typed target condition parameter')
            for e in expected['edges']:
                if e['kind']=='object':
                    check(shapes[e['from']].get('returnType')==shapes[e['to']].get('typeRef'),'Call result and ObjectNode types disagree')
        if expected['id']=='03-implementation':
            check(not shapes['rollback-body'].get('operation'),'Rollback must not call execute again')
        notes=[s for s in d.findall('shape') if s.get('type')=='NOTE']
        check(len(notes)>=3 and all(s.get('name','').strip() for s in notes),'Empty/unregistered native note')
        check((directory/(expected['id']+'.svg')).is_file(),'Missing reopened native SVG')
        results.append(dict(diagram=expected['id'],nodes=len(expected['nodes']),controlFlows=sum(e['kind']=='control' for e in expected['edges']),objectFlows=sum(e['kind']=='object' for e in expected['edges']),exceptionHandlers=sum(e['kind']=='exception' for e in expected['edges']),partitions=len(expected['lanes']),nativeCallOperationActions=sum(bool(s.get('operation')) for s in shapes.values())))
    files={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in directory.iterdir() if p.suffix in {'.vpp','.svg','.png','.xml'}}
    receipt=dict(status='PASS',projectSHA256=files[project.name],diagrams=results,
                 checks=['UML2 node/control-flow model types','action texts and bounds after separate-process reopen','model and shape endpoints','perimeter attachment and obstacle clearance; orthogonal normal flows, lightning exception handlers','real partition membership and package represents links','native CallOperationAction operation references','registered nonempty notes','native reopened SVG/PNG exports'],
                 limits=['No full desktop GUI interaction automation','No live provider or application runtime verification','Target design includes explicitly marked unimplemented operations'],sha256=files)
    (directory/'validation.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(receipt,ensure_ascii=True,indent=2))


if __name__=='__main__':
    main(Path(sys.argv[1]))
