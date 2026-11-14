"""Acceptance regressions for the executable, English-only UC-2 implementation view."""
import importlib
from pathlib import Path
import re
import sys
import tempfile
import unittest

ROOT = Path.cwd()
TOOLS = ROOT / 'docs/architecture/uc-2/tools'
sys.path.insert(0, str(TOOLS))
try:
    revision = importlib.import_module('implementation_model')
except ModuleNotFoundError:
    revision = None
import diagram_model


class ImplementationTests(unittest.TestCase):
    def setUp(self):
        self.diagram = revision.build_implementation() if revision else diagram_model.build_diagrams()[2]

    def test_every_displayed_string_is_english(self):
        strings = [self.diagram['name']] + [n['text'] for n in self.diagram['nodes']]
        strings += [e['guard'] for e in self.diagram['edges']]
        strings += [p['name'] for p in self.diagram['lanes']] + self.diagram['notes']
        for text in strings:
            self.assertNotRegex(text, '[\u0400-\u04ff]')

    def test_no_proposals_or_change_requests(self):
        for n in self.diagram['nodes']:
            self.assertNotRegex(n['text'].lower(), r'proposed|modification|\btodo\b|\u043f\u0440\u043e\u0435\u043a\u0442|\u0438\u0437\u043c\u0435\u043d\u0438\u0442\u044c')

    def test_actions_and_standard_activity_control_only(self):
        self.assertEqual(self.diagram['notes'], [])
        self.assertFalse(any(n['kind'] in {'object', 'class', 'note'} for n in self.diagram['nodes']))
        self.assertFalse(any(e['kind'] == 'object' for e in self.diagram['edges']))
        diagram_model.validate(self.diagram)

    def test_each_action_is_a_concrete_call_or_expression(self):
        for n in self.diagram['nodes']:
            if n['kind'] == 'action':
                self.assertRegex(n['text'], r'[\w$.]+\s*\(|\bthrow\b|\w+\s*=')

    def test_calls_have_source_backed_operations(self):
        self.assertIsNotNone(revision, 'implementation_model must replace the legacy target-design view')
        actions = [n for n in self.diagram['nodes'] if n['kind'] == 'action']
        self.assertGreaterEqual(sum(bool(n.get('callOwner')) for n in actions), 20)
        for n in actions:
            if n.get('callSource'):
                source = (ROOT/n['callSource']).read_text(encoding='utf-8')
                self.assertRegex(source, r'\b' + re.escape(n['callName']) + r'\s*\(')

    def test_delivery_actions_are_source_backed(self):
        nodes = {n['id']: n for n in self.diagram['nodes']}
        for ident in ('notify', 'receive'):
            self.assertTrue(nodes[ident].get('callOwner'), 'Delivery must call an implemented adapter')
            self.assertTrue((ROOT/nodes[ident]['callSource']).is_file())

    def test_generator_forces_visible_connectors_above_container_backgrounds(self):
        from revision_plugin import generate
        with tempfile.TemporaryDirectory() as scratch:
            generated = Path(scratch)/'ImplementationRevisionPlugin.java'
            generate(TOOLS/'ActivityProjectPlugin.java', generated)
            text = generated.read_text(encoding='utf-8')
        self.assertTrue('line.getLineModel().setColor(ink)' in text, 'Force visible connector ink')
        self.assertTrue('line.getLineModel().setWeight(1.5f)' in text, 'Force a nonzero stroke')
        self.assertIn('LineStyle.Style1', text, 'Do not inherit a None line style')
        self.assertTrue('c.setRequestRebuild(true)' in text, 'Rebuild connector drawing bounds after assigning points')
        self.assertGreaterEqual(text.count('setCoverConnector(false)'), 3, 'Swimlane, compartments and handler must not hide connectors')

    def test_rollback_and_response_are_not_successful_delivery(self):
        nodes = {n['id']: n for n in self.diagram['nodes']}
        self.assertEqual(nodes['rollback']['kind'], 'handler')
        self.assertEqual(nodes['after-commit']['kind'], 'fork')
        self.assertFalse(any(e['from']=='blocked' and e['kind']=='control' for e in self.diagram['edges']))
        self.assertNotEqual(nodes['rollback-body'].get('callName'), 'execute')


if __name__ == '__main__':
    unittest.main()
