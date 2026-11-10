"""Regression tests for the source-backed package documentation (stdlib only)."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
import package_model as model
import validate_native as native


class PackageModelTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.inventory, cls.dependencies = model.scan_sources()
        cls.views = model.build_views(cls.inventory, cls.dependencies)

    def test_every_production_java_package_is_inventoried(self):
        self.assertEqual(len(self.inventory), 18)
        self.assertIn(model.BASE + '.application.execution', self.inventory)
        self.assertIn(model.BASE + '.application.completion', self.inventory)
        self.assertIn('com.seasonplan.security', self.inventory)
        self.assertIn('com.seasonplan', self.inventory)

    def test_core_dependencies_point_inward(self):
        for (client, supplier), evidence in self.dependencies.items():
            self.assertTrue(evidence)
            if client == model.BASE + '.domain':
                self.fail('Domain has an internal outgoing dependency')
            if '.application' in client:
                self.assertTrue(supplier.startswith(model.BASE + '.application') or
                                supplier == model.BASE + '.domain', (client, supplier))

    def test_pollen_has_one_application_boundary(self):
        pollen = model.BASE + '.application.pollen'
        for sibling in ('approval', 'preparation', 'completion'):
            self.assertIn((model.BASE + '.application.' + sibling, pollen), self.dependencies)
        self.assertIn((pollen, model.BASE + '.application.port'), self.dependencies)
        self.assertNotIn(model.BASE + '.application.port.PollenReservationRepository',
                         {name for names in self.inventory.values() for name in names})

    def test_system_and_application_views_cover_real_packages(self):
        self.assertEqual([v['id'] for v in self.views], ['01-system', '02-application'])
        overview, application = self.views
        self.assertIn('frontend', overview['nodes'])
        self.assertIn(model.BASE + '.adapter.fake', overview['nodes'])
        self.assertIn(model.BASE + '.adapter.pollentree', overview['nodes'])
        self.assertEqual(len(application['nodes']), 10)
        expected = {(a, b) for a, b in self.dependencies
                    if a in application['nodes'] and b in application['nodes']}
        self.assertEqual({(e['from'], e['to']) for e in application['edges']}, expected)
        self.assertEqual([e['name'] for e in overview['edges'] if e['from'] == 'frontend'],
                         ['REST / JSON', 'Auth / JSON'])

    def test_contracts_and_gaps_are_explicit(self):
        text = '\n'.join(n['body'] for v in self.views for n in v['nodes'].values())
        for token in ('UC-1', 'UC-2', 'UC-3', 'UC-4', 'UC-5', 'PollenService',
                      'NaturalCalendarService', 'NotificationOutbox', 'ExecutionPorts',
                      'CompletionPorts', 'CorrectionAction', 'AuditLogEntry', 'CONFIRMED'):
            self.assertIn(token, text)
        self.assertIn('core-only', text)
        self.assertIn('stub', text)
        self.assertNotIn('CORRECTION →', text)

    def test_planning_does_not_claim_target_search_or_conflict_checks(self):
        text = self.views[1]['nodes'][model.APP+'.planning']['body']
        self.assertIn('Create / edit / submit / list', text)
        self.assertIn('Search / cross-plan conflicts: missing', text)
        self.assertIn('FR-82–85: target', text)

    def test_note_validation_rejects_stale_title_and_legend(self):
        spec = self.views[0]
        for changed in ('title', 'legend'):
            diagram = ET.Element('diagram')
            for kind, text in [('title', spec['name']+'\n'+spec['intro']), ('legend', spec['notes'])]:
                ET.SubElement(diagram, 'shape', id=kind+':'+spec['id'], type='NOTE',
                              name='stale' if kind==changed else text,
                              documentation='stale' if kind==changed else text)
            with self.assertRaisesRegex(AssertionError, 'Wrong '+changed+' text'):
                native.validate_heading_notes(diagram, spec)

    def test_graphviz_layout_and_routes_are_valid(self):
        for view in self.views:
            laid_out = model.layout(view)
            model.validate_geometry(laid_out)
            self.assertTrue(all(n['w'] > 0 and n['h'] > 0
                                for n in laid_out['nodes'].values()))

    def test_authoritative_sources_exist(self):
        for relative in model.SOURCES:
            self.assertTrue((model.ROOT / relative).is_file(), relative)


if __name__ == '__main__':
    unittest.main()
