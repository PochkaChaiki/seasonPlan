"""UC-5 communication-diagram contract, documentation tooling only."""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
import communication_model as model


class CommunicationContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = model.build()
        cls.diagrams = {d.get('id'): d for d in cls.root.findall('diagram')}
        cls.ops = {o.get('id'): o for o in cls.root.findall('operations/operation')}

    def test_three_abstraction_levels_and_native_communication_spec(self):
        self.assertEqual(self.root.tag, 'uc5-communications')
        self.assertEqual(set(self.diagrams), {'01-use-case', '02-logical', '03-generation',
                                            '03a-sources', '03b-approval', '03c-publication'})
        self.assertEqual({d.get('view') for d in self.diagrams.values()}, {'use-case', 'logical', 'implementation'})
        for d in self.diagrams.values():
            self.assertTrue(d.get('precondition'))
            self.assertTrue(d.get('postcondition'))
            self.assertTrue(d.findall('message'))
            self.assertFalse(d.findall('activation'))
            self.assertFalse(d.findall('fragment'))

    def test_logical_participants_reuse_actual_packages_not_service_inventions(self):
        canonical = ET.parse(model.REPO / model.PACKAGE_SOURCE).getroot().find("diagram[@id='01-system']")
        canonical = {p.get('id'): p for p in canonical.findall('package')}
        packages = {p.get('id'): p for p in self.root.findall('packages/package')}
        logical = self.diagrams['02-logical']
        self.assertEqual({p.get('package') for p in logical.findall('participant')}, set(packages))
        for p in packages.values():
            if p.get('source'):
                self.assertIn(p.get('source'), canonical)
        self.assertEqual(logical.find('message').get('from'), 'frontend')
        self.assertFalse(any(p.get('kind') == 'actor' for p in logical.findall('participant')))
        self.assertNotIn('service', packages)  # No fabricated UC5 compatibility-facade delegation.

    def test_abstract_has_both_actors_no_methods(self):
        d = self.diagrams['01-use-case']
        self.assertEqual({p.get('name') for p in d.findall('participant') if p.get('kind') == 'actor'},
                         {'Главный хранитель', 'Фея-планировщик'})
        self.assertFalse(any(m.get('operation') for m in d.findall('message')))
        self.assertFalse(any('(' in m.get('text') for m in d.findall('message')))
        system=next(p for p in d.findall('participant') if p.get('id')=='system')
        for p in d.findall('participant'):
            if p.get('kind')=='actor':self.assertLess(int(p.get('x')),int(system.get('x')))

    def test_all_calls_have_valid_links_unique_number_and_operation(self):
        used = set()
        for d in self.diagrams.values():
            people = {p.get('id') for p in d.findall('participant')}
            links = {l.get('id'): l for l in d.findall('link')}
            numbers = []
            for m in d.findall('message'):
                self.assertIn(m.get('from'), people)
                self.assertIn(m.get('to'), people)
                link = links[m.get('link')]
                self.assertEqual({m.get('from'), m.get('to')}, {link.get('from'), link.get('to')})
                numbers.append(m.get('number'))
                if m.get('operation'):
                    self.assertIn(m.get('operation'), self.ops)
                    used.add(m.get('operation'))
            self.assertEqual(len(numbers), len(set(numbers)))
        self.assertEqual(used, set(self.ops))

    def test_existing_operations_have_exact_source_evidence(self):
        for op in self.ops.values():
            if op.get('status') == 'existing':
                source = (model.REPO / op.get('source')).read_text(encoding='utf-8')
                self.assertIn(op.get('evidence'), source, op.attrib)
            else:
                self.assertEqual(op.get('status'), 'proposed')

    def test_required_uc5_behavior_and_no_extra_lifecycle_status(self):
        messages = [m for d in self.diagrams.values() for m in d.findall('message')]
        used = {m.get('operation') for m in messages}
        self.assertTrue({'generate', 'approveComplete', 'archive', 'view', 'export', 'snapshot',
                         'blockers', 'kpis', 'usage', 'summary', 'render', 'reportApprove',
                         'planComplete', 'planArchive', 'audit', 'transaction'} <= used)
        self.assertEqual(self.root.get('scope'), 'ACTIVE → COMPLETED → ARCHIVED')
        for d in self.diagrams.values():
            for m in d.findall('message'):
                self.assertNotIn('CORRECTION', m.get('text'))
                self.assertNotIn('EMERGENCY', m.get('text'))

    def test_source_snapshot_and_pollen_are_read_only_and_versioned(self):
        d = self.diagrams['03a-sources']
        ids = {m.get('operation') for m in d.findall('message')}
        self.assertTrue({'requiredResults', 'metricList', 'incidentList', 'actionList', 'ritualList',
                         'catalog', 'usage', 'summary', 'blockers', 'kpis'} <= ids)
        self.assertFalse(any('save' in m.get('text') or 'INSERT' in m.get('text') for m in d.findall('message')))
        self.assertIn('sourceVersion', d.get('notes'))
        self.assertIn('pollen-totals', d.get('notes'))
        self.assertIn('503', d.get('notes'))

    def test_approval_rechecks_latest_and_stale_sources_before_mutations(self):
        d = self.diagrams['03b-approval']
        ops = [m.get('operation') for m in d.findall('message')]
        self.assertLess(ops.index('latestDraft'), ops.index('reportApprove'))
        self.assertLess(ops.index('snapshot'), ops.index('reportApprove'))
        self.assertLess(ops.index('blockers'), ops.index('planComplete'))
        self.assertIn('current.equals(draft.snapshot())', d.get('notes'))
        writes = {'reportApprove', 'planComplete', 'appendApproved', 'planSave', 'audit'}
        for m in d.findall('message'):
            if m.get('operation') in writes:
                self.assertIn('valid', m.get('guard'))
        self.assertIn('ROLLBACK', d.get('notes'))
        self.assertIn('COMMIT', d.get('postcondition'))

    def test_generation_is_blocked_draft_not_early_completion(self):
        d = self.diagrams['03-generation']
        used = {m.get('operation') for m in d.findall('message')}
        self.assertIn('saveDraft', used)
        self.assertNotIn('planComplete', used)
        self.assertIn('blockers', d.get('postcondition'))
        self.assertTrue(any(m.get('iteration') == 'true' for m in d.findall('message')))
        api = next(p for p in d.findall('participant') if p.get('id') == 'api')
        self.assertEqual(api.get('status'), 'proposed')

    def test_publication_uses_only_approved_snapshot_and_archive_is_separate(self):
        d = self.diagrams['03c-publication']
        render = next(m for m in d.findall('message') if m.get('operation') == 'render')
        self.assertIn('approved', render.get('text'))
        self.assertIn('approved', render.get('guard'))
        self.assertIn('latestDraft.version', d.get('notes'))
        for m in d.findall('message'):
            if m.get('operation') in {'planArchive', 'planSave', 'audit'}:
                self.assertIn('archiveValid', m.get('guard'))
        self.assertIn('immutable', d.get('postcondition'))

    def test_api_success_responses_are_native_reply_messages(self):
        for key, number in [('03-generation','1.2'), ('03b-approval','1.2'),
                            ('03c-publication','3.2')]:
            m=next(m for m in self.diagrams[key].findall('message') if m.get('number')==number)
            self.assertEqual(m.get('kind'),'reply')
            self.assertTrue(m.get('replyTo'))

    def test_transaction_ranges_include_audits_and_exclude_api_success(self):
        for key, scope in [('03-generation','1.1.1–1.1.5'), ('03b-approval','1.1.1–1.1.10'),
                           ('03c-publication','3.1.1–3.1.7')]:
            self.assertIn(scope, self.diagrams[key].get('notes'))
        self.assertNotIn('1.2–1.7', self.diagrams['03-generation'].get('notes'))

    def test_callback_messages_are_nested_under_transaction_call(self):
        for key in ('03-generation','03b-approval'):
            for m in self.diagrams[key].findall('message'):
                if m.get('operation') and m.get('operation') not in {'generate','approveComplete','transaction'}:
                    self.assertTrue(m.get('number').startswith('1.1.'),m.attrib)
        for m in self.diagrams['03c-publication'].findall('message'):
            if m.get('number').startswith('3') and m.get('operation') not in {'','archive','transaction'}:
                self.assertTrue(m.get('number').startswith('3.1.'),m.attrib)

    def test_native_message_names_stay_single_line_to_avoid_arrow_overlay(self):
        for d in self.diagrams.values():
            for m in d.findall('message'):
                self.assertNotIn('\n',m.get('text'),m.attrib)

    def test_links_do_not_cut_other_links_message_text(self):
        for d in self.diagrams.values():
            for l in d.findall('link'):
                pts=[tuple(map(int,p.split(','))) for p in l.get('points').split(';')]
                for message in d.findall('message'):
                    if message.get('link')==l.get('id'):continue
                    x,y,w,h=model.rect(message)
                    for a,b in zip(pts,pts[1:]):
                        horizontal=a[1]==b[1] and y<a[1]<y+h and min(a[0],b[0])<x+w and max(a[0],b[0])>x
                        vertical=a[0]==b[0] and x<a[0]<x+w and min(a[1],b[1])<y+h and max(a[1],b[1])>y
                        self.assertFalse(horizontal or vertical,(d.get('id'),l.get('id'),message.get('number')))

    def test_geometry_avoids_unrelated_nodes_and_caption_overlaps(self):
        for d in self.diagrams.values():
            model.validate_geometry(d)
            links={l.get('id'):l for l in d.findall('link')}
            for l in links.values():
                if l.get('from')==l.get('to'):
                    self.assertEqual(len(l.get('points').split(';')),4)
            for m in d.findall('message'):
                self.assertEqual(m.get('reverse'),str(m.get('from')!=links[m.get('link')].get('from')).lower())


if __name__ == '__main__':
    unittest.main()
