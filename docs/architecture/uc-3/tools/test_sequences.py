"""Regression contract for the UC-3 documentation generator (standard library only)."""
import unittest
from pathlib import Path
import sequence_model as model


class SequenceContractTest(unittest.TestCase):
    def setUp(self):
        self.root = model.build()
        self.diagrams = {d.get('id'): d for d in self.root.findall('diagram')}

    def test_three_views_and_four_referenced_details(self):
        self.assertEqual(set(self.diagrams), {'01-use-case', '02-logical', '03-implementation',
                         '03a-resources', '03b-tasks', '03c-assignments', '03d-readiness'})
        refs = self.diagrams['03-implementation'].findall('ref')
        self.assertEqual({r.get('target') for r in refs},
                         {'03a-resources', '03b-tasks', '03c-assignments', '03d-readiness'})

    def test_logical_uses_existing_system_packages_and_frontend_initiator(self):
        source = model.ET.parse(model.REPO/'docs/architecture/packages/packages.xml').getroot()
        source_packages = {p.get('id'): p for p in source.find("diagram[@id='01-system']").findall('package')}
        packages = {p.get('id'): p for p in self.root.findall('packages/package')}
        expected = {'frontend', 'api', 'security', 'service', 'application', 'domain',
                    'persistence', 'pollentree', 'database', 'provider'}
        self.assertEqual(set(packages), expected)
        for key, package in packages.items():
            if key not in {'database', 'provider'}:
                original = source_packages[package.get('source')]
                self.assertEqual(package.get('name'), 'frontend' if key == 'frontend' else original.get('name'))
        logical = self.diagrams['02-logical']
        self.assertEqual({l.get('package') for l in logical.findall('lifeline')}, expected)
        self.assertTrue(all(l.get('kind') != 'actor' for l in logical.findall('lifeline')))
        self.assertEqual(logical.find('lifeline').get('name'), 'frontend')
        self.assertEqual(logical.find('message').get('from'), 'frontend')
        for l in logical.findall('lifeline'):
            self.assertEqual(l.get('name'), packages[l.get('package')].get('name'))
        self.assertFalse(any(p.get('name') in {'fake', 'config'} for p in packages.values()))

    def test_use_case_actors_are_all_left_of_system(self):
        lines = self.diagrams['01-use-case'].findall('lifeline')
        actors = [l for l in lines if l.get('kind') == 'actor']
        systems = [l for l in lines if l.get('kind') != 'actor']
        self.assertEqual({l.get('id') for l in actors}, {'coordinator', 'guardian', 'planner'})
        self.assertLess(max(int(l.get('x')) for l in actors), min(int(l.get('x')) for l in systems))
        symbols = self.diagrams['01-use-case'].findall('actor-symbol')
        self.assertEqual({s.get('lifeline') for s in symbols}, {l.get('id') for l in actors})
        self.assertTrue(all(int(s.get('h')) <= 70 for s in symbols))

    def test_native_lifelines_and_bounded_executions_in_every_view(self):
        for d in self.diagrams.values():
            bars = d.findall('activation')
            self.assertTrue(bars, d.get('id'))
            objects = {l.get('id') for l in d.findall('lifeline') if l.get('kind') != 'actor'}
            self.assertEqual({a.get('lifeline') for a in bars}, objects)
            self.assertTrue(all(int(a.get('h')) >= 24 for a in bars))
            for a in bars:
                self.assertNotEqual(a.get('lifeline'), 'planner')
            for m in d.findall('message'):
                for end in ('from', 'to'):
                    if m.get(end) in objects:
                        self.assertIn(m.get(end+'Activation'), {a.get('id') for a in bars})

    def test_message_endpoints_order_operands_and_space(self):
        for d in self.diagrams.values():
            lines = {l.get('id') for l in d.findall('lifeline')}
            operands = {o.get('id') for f in d.findall('fragment') for o in f.findall('operand')}
            ys = []
            for m in d.findall('message'):
                self.assertIn(m.get('from'), lines)
                self.assertIn(m.get('to'), lines)
                ys.append(int(m.get('y')))
                if m.get('operand'):
                    self.assertIn(m.get('operand'), operands)
                if m.get('kind') == 'reply':
                    self.assertTrue(m.get('replyTo'))
            self.assertEqual(ys, sorted(set(ys)))
            self.assertTrue(all(b-a >= 58 for a, b in zip(ys, ys[1:])))

    def test_alternatives_and_business_gate_are_explicit(self):
        text = model.ET.tostring(self.root, encoding='unicode')
        for required in ['CONFIRMED', 'APPROVED', 'RITUAL_READY', 'CRITICAL',
                         'COMPLETED', 'RITUAL_LEAD', 'rollback', 'Подтверждение участия',
                         'нехватка', 'конфликт', '«проект»']:
            self.assertIn(required, text)
        for key in ['01-use-case', '02-logical', '03d-readiness']:
            self.assertTrue(self.diagrams[key].findall('fragment'))
        self.assertNotIn('ACTIVE', ''.join(m.get('text') for d in self.diagrams.values() for m in d.findall('message')))

    def test_actual_operations_have_source_evidence_proposals_are_marked(self):
        operations = {o.get('id'): o for o in self.root.findall('operations/operation')}
        self.assertGreater(len(operations), 25)
        for op in operations.values():
            if op.get('status') == 'existing':
                source = model.REPO / op.get('source')
                self.assertTrue(source.is_file(), source)
                self.assertIn(op.get('evidence'), source.read_text(encoding='utf-8'))
            else:
                self.assertEqual(op.get('status'), 'proposed')
        for d in self.diagrams.values():
            for m in d.findall('message'):
                if m.get('operation'):
                    op = operations[m.get('operation')]
                    if op.get('status') == 'proposed':
                        self.assertIn('«проект»', m.get('text'))
                    target = next(l for l in d.findall('lifeline') if l.get('id') == m.get('to'))
                    self.assertEqual(op.get('owner'), target.get('type'))

    def test_review_regressions(self):
        overview = self.diagrams['03-implementation']
        self.assertTrue(any('/mark-ritual-ready' in m.get('text') for m in overview.findall('message')))
        ref = next(r for r in overview.findall('ref') if r.get('target') == '03d-readiness')
        self.assertIn('pollen', ref.get('covered').split(','))
        for key in ['03a-resources','03b-tasks','03c-assignments']:
            self.assertIn('APPROVED', self.diagrams[key].get('postcondition',''))
            self.assertNotIn('RITUAL_READY iff', self.diagrams[key].get('postcondition',''))
        resources = self.diagrams['03a-resources']
        self.assertTrue(any(f.get('id') == 'r-decision-opt' and f.get('operator') == 'opt'
                            for f in resources.findall('fragment')))
        failed_resource = [m for m in resources.findall('message') if m.get('operand') == 'r-bad']
        self.assertTrue(any(m.get('operation') == 'clientConfirm' for m in failed_resource))
        tasks = self.diagrams['03b-tasks']
        self.assertTrue(any(f.get('operator') == 'break' for f in tasks.findall('fragment')))
        self.assertFalse(any(f.get('id') == 't-errors' for f in tasks.findall('fragment')))
        confirmation = self.diagrams['03c-assignments']
        self.assertTrue(any(f.get('id') == 'a-confirm-conflict' and f.get('operator') == 'break'
                            for f in confirmation.findall('fragment')))
        for key, operand in [('02-logical','l-failure'),('03d-readiness','c-write')]:
            failed = [m for m in self.diagrams[key].findall('message') if m.get('operand') == operand]
            self.assertGreaterEqual(len(failed), 3)
            self.assertTrue(any('append' in m.get('text') or 'INSERT' in m.get('text') for m in failed))

    def test_manifest_hashes_reproducible(self):
        inventory = model.source_inventory()
        self.assertGreater(len(inventory), 20)
        self.assertTrue(all(len(i['sha256']) == 64 for i in inventory))
        self.assertEqual(model.ET.tostring(model.build()), model.ET.tostring(model.build()))


if __name__ == '__main__':
    unittest.main()
