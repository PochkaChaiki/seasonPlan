"""Regression contracts for source extraction and complete, source-backed UML."""
import tempfile
import unittest
from pathlib import Path
import class_model as model


class InventoryTests(unittest.TestCase):
    def test_java_records_nested_types_and_source_signatures(self):
        with tempfile.TemporaryDirectory() as folder:
            Path(folder, 'Types.java').write_text('''package sample;
import java.util.List;
public interface Types {
  record Row(String id, List<Integer> values) {
    public Row { if (id == null) throw new IllegalArgumentException(); }
    private static String normalize(String value) { return value; }
  }
  enum Mode { ON, OFF; private Mode() {} }
  List<Row> read(String key);
}
''', encoding='utf-8')
            types = {c['id']: c for c in model.extract_java(Path(folder))}
        row = types['sample.Types.Row']
        self.assertEqual('record', row['kind'])
        self.assertEqual('public', row['visibility'])
        self.assertEqual('public', types['sample.Types.Mode']['visibility'])
        self.assertEqual([('id', 'String'), ('values', 'List<Integer>')],
                         [(a['name'], a['type']) for a in row['attributes']])
        self.assertTrue(all(a['readonly'] for a in row['attributes']))
        ctor = next(o for o in row['operations'] if o['constructor'])
        self.assertEqual(['id', 'values'], [p['name'] for p in ctor['parameters']])
        self.assertEqual('private', row['operations'][1]['visibility'])
        self.assertEqual(['ON', 'OFF'], types['sample.Types.Mode']['literals'])
        self.assertEqual([], types['sample.Types.Mode']['operations'][0]['parameters'])
        self.assertEqual('public', types['sample.Types']['operations'][0]['visibility'])
        self.assertTrue(types['sample.Types']['operations'][0]['abstract'])
        self.assertEqual('sample.Types', row['owner'])

    def test_frontend_named_functions_object_api_props_and_hook_state(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder, 'Panel.jsx')
            path.write_text('''export const api = { load: async (id) => id };
export default function Panel({ title }) {
 const [busy, setBusy] = useState(false);
 async function submit(event) { return event; }
 function enabled() { return true; }
 return <button onClick={() => submit(title)}>{title}</button>;
}''', encoding='utf-8')
            module = model.extract_frontend(Path(folder))[0]
        operations = {o['name']: o for o in module['operations']}
        self.assertIn('api.load', operations)
        self.assertIn('Panel.submit', operations)
        self.assertEqual('{ title }', operations['Panel']['parameters'][0]['name'])
        self.assertEqual('Promise<dynamic>', operations['api.load']['returns'])
        self.assertIn('Panel.busy', [a['name'] for a in module['attributes']])
        self.assertNotIn('setBusy', operations)  # Hook result, not a declared function.
        self.assertEqual('dynamic', operations['Panel.enabled']['returns'])
        self.assertEqual('frontend/src', module['package'])
        self.assertEqual(4, len(operations))  # Anonymous JSX callbacks deliberately implicit.

    def test_current_project_has_every_source_file_and_recent_types(self):
        inventory = model.inventory()
        actual = {c['source'] for c in inventory['classifiers']}
        expected = {p.relative_to(model.ROOT).as_posix() for root, suffixes in [
            (model.ROOT / 'backend/src/main/java', {'.java'}),
            (model.ROOT / 'frontend/src', {'.js', '.jsx'})]
            for p in root.rglob('*') if p.suffix in suffixes and '.test.' not in p.name}
        self.assertEqual(expected, actual)
        ids = {c['id'] for c in inventory['classifiers']}
        self.assertIn('com.seasonplan.lifecycle.domain.ApprovalCondition', ids)
        self.assertIn('com.seasonplan.lifecycle.api.NotificationController', ids)
        self.assertIn('com.seasonplan.lifecycle.application.port.ExecutionPorts.Rituals', ids)
        status = next(c for c in inventory['classifiers'] if c['id'].endswith('.SeasonPlanStatus'))
        self.assertEqual(8, len(status['literals']))
        self.assertNotIn('EMERGENCY', status['literals'])
        for edge in inventory['relationships']:
            self.assertIn(edge['from'], ids)
            self.assertIn(edge['to'], ids)
            self.assertNotEqual('composition', edge['kind'])

    def test_inherited_nested_types_and_implicit_public_member_types(self):
        data = model.inventory()
        types = {c['id']: c for c in data['classifiers']}
        base = 'com.seasonplan.lifecycle.'
        for name in ('NotificationOutbox.Event', 'NotificationOutbox.Channel', 'PollenTreeClient.Summary'):
            self.assertEqual('public', types[base+'application.port.'+name]['visibility'])
        summary = base+'application.port.PollenTreeClient.Summary'
        actual = {(e['from'],e['to'],e['kind']) for e in data['relationships']}
        self.assertIn((base+'adapter.fake.FakePollenTreeClient.Consumed',summary,'association'), actual)
        for name in ('adapter.fake.FakePollenTreeClient','adapter.pollentree.RestPollenTreeClient'):
            self.assertIn((base+name,summary,'dependency'), actual)

    def test_every_classifier_and_relation_has_a_detailed_diagram(self):
        data = model.inventory()
        diagrams = model.build_diagrams(data)
        detailed = {n['id'] for d in diagrams for n in d['nodes'] if n['detail']}
        self.assertEqual({c['id'] for c in data['classifiers']}, detailed)
        depicted = {e['id'] for d in diagrams for e in d['edges']}
        self.assertEqual({e['id'] for e in data['relationships']}, depicted)
        for d in diagrams:
            model.validate_geometry(d)


if __name__ == '__main__':
    unittest.main()
