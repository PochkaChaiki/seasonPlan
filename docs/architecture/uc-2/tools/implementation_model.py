"""Source-backed implementation activity; the historical two upper views are untouched."""
import copy
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import diagram_model as base

HERE = Path(__file__).resolve().parent


def build_implementation():
    d = copy.deepcopy(base.build_diagrams()[2])
    d['name'] = '3. UC-2 - Implementation'
    d['subtitle'] = 'Executable UC-2 implementation'
    d['notes'] = []
    names = ['Client', 'API / security', 'Application', 'Domain / ports', 'Adapters']
    for lane, name in zip(d['lanes'], names):
        lane['name'] = name
    d['nodes'] = [n for n in d['nodes'] if n['kind'] != 'object']
    d['edges'] = [e for e in d['edges'] if e['kind'] != 'object']
    actions = json.loads((HERE/'implementation_actions.json').read_text(encoding='utf-8'))
    for n in d['nodes']:
        if n['kind'] in {'action', 'handler'}:
            n.update(actions[n['id']])
    guards = {
        ('access','load'): "CHIEF_GUARDIAN",
        ('access','denied'): 'else',
        ('review','status'): "action !== 'return'",
        ('review','comment-merge'): "action === 'return'",
        ('status','verify-command'): 'SUBMITTED',
        ('status','review-merge'): 'VERIFIED',
        ('decision','approval-merge'): "action === 'approve' && conditions.length === 0",
        ('decision','conditional'): "action === 'approve' && conditions.length > 0",
        ('decision','comment-merge'): 'action == RETURN',
        ('reservation','approve'): 'CONFIRMED',
        ('reservation','pollen-block'): 'REJECTED && !CONFIRMED',
        ('reservation','blocked'): 'else',
        ('comment-check','return-tx'): 'comment != null && !comment.isBlank()',
        ('comment-check','comment-error'): 'else',
    }
    commit_order = {('result-merge','audit'): ('result-merge','save'),
                    ('audit','save'): ('save','audit'), ('save','outbox'): ('audit','outbox')}
    for e in d['edges']:
        e['guard'] = guards.get((e['from'],e['to']), '')
        e['from'], e['to'] = commit_order.get((e['from'],e['to']), (e['from'],e['to']))
    base.validate(d)
    return d


def export(path):
    d = build_implementation()
    for n in d['nodes']:
        n['w'] = (n['w']+19)//20*20
        n['h'] = (n['h']+19)//20*20
    base.validate(d)
    base.route(d)
    root = ET.Element('project', name='SeasonPlan UC-2 implementation')
    el = ET.SubElement(root,'diagram',id=d['id'],name=d['name'],width=str(60+len(d['lanes'])*d['laneWidth']),height=str(d['height']))
    for i,p in enumerate(d['lanes']):
        ET.SubElement(el,'lane',id=p['id'],name=p['name'],x=str(60+i*d['laneWidth']),width=str(d['laneWidth']))
    for n in d['nodes']:
        ne = ET.SubElement(el,'node',**{k:str(v) for k,v in n.items() if k != 'text'})
        ne.text = n['text']
    for e in d['edges']:
        ET.SubElement(el,'edge',**{k:str(v) for k,v in e.items() if k != 'points'},points=';'.join(f'{x},{y}' for x,y in e['points']))
    ET.indent(root)
    ET.ElementTree(root).write(path,encoding='utf-8',xml_declaration=True)


if __name__ == '__main__':
    import sys
    export(Path(sys.argv[1]))
