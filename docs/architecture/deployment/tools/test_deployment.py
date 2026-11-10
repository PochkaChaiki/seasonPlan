"""Regression contract for the native-FreeBSD UML 2 deployment delivery."""
import copy
import unittest
from deployment_model import model, validate_geometry


class DeploymentContractTest(unittest.TestCase):
    def setUp(self):
        self.view = model()
        self.nodes = {n['id']: n for n in self.view['nodes']}

    def test_native_uml_families_and_no_container_runtime(self):
        self.assertEqual('DeploymentDiagram', self.view['type'])
        self.assertTrue({'Node', 'Artifact', 'DeploymentSpecification'} <= {n['type'] for n in self.nodes.values()})
        for n in self.nodes.values():
            if n['type'] != 'NOTE':
                self.assertNotRegex(n['name'].lower(), r'docker|container|kubernetes|podman|linux')
        self.assertEqual('device', self.nodes['helios']['stereotype'])
        self.assertEqual('executionEnvironment', self.nodes['os']['stereotype'])

    def test_exact_documented_host_characteristics_not_application_allocation(self):
        text = self.nodes['host-facts']['text']
        for expected in ['FreeBSD 14.4-STABLE', 'amd64', 'E5-2643', '3.30', '16', '137381744640', '456', 'не квота']:
            self.assertIn(expected, text)
        self.assertIn('17.0.12', self.nodes['jvm']['name'])

    def test_native_ownership_deploys_files_to_execution_environments(self):
        for artifact, target in [('frontend', 'proxy'), ('proxy-config', 'proxy'), ('backend', 'jvm'), ('app-config', 'jvm'), ('database', 'postgres'), ('recovery-data', 'standby'), ('backups', 'backup-runtime')]:
            self.assertEqual(target, self.nodes[artifact]['parent'])
        self.assertEqual('os', self.nodes['jvm']['parent'])
        self.assertEqual('helios', self.nodes['os']['parent'])
        self.assertIn('seasonplan-backend-0.1.0-SNAPSHOT.jar', self.nodes['backend']['name'])
        self.assertIn('dist/', self.nodes['frontend']['name'])

    def test_communication_paths_are_associations_between_nodes(self):
        for e in self.view['edges']:
            self.assertEqual('Association', e['type'])
            self.assertEqual('Node', self.nodes[e['from']]['type'])
            self.assertEqual('Node', self.nodes[e['to']]['type'])
            self.assertTrue(e['protocol'])
            self.assertTrue(e['evidence'])

    def test_interface_security_and_locality(self):
        edges = {e['id']: e for e in self.view['edges']}
        for key in ['I1', 'I4', 'I5', 'I6']:
            self.assertIn('443', edges[key]['protocol'])
            self.assertIn('TLS', edges[key]['protocol'])
        self.assertIn('127.0.0.1:8080', edges['I2']['protocol'])
        self.assertIn('127.0.0.1:5432', edges['I3']['protocol'])
        self.assertIn('22', edges['I8']['protocol'])
        self.assertEqual(('jvm', 'pollen'), (edges['I4']['from'], edges['I4']['to']))
        self.assertEqual(('standby', 'postgres'), (edges['I9']['from'], edges['I9']['to']))
        self.assertIn('API', edges['I4']['protocol'])

    def test_target_assumptions_and_audit_durability_are_explicit(self):
        text = '\n'.join(n['name'] for n in self.nodes.values())
        for expected in ['ПРОЕКТ', 'remote_apply', '4 часа', 'RPO=0', 'одного', 'не реализован', 'не подтвержден', 'Node.js', 'TRUNCATE', 'Pollen Tree', 'SMTP']:
            self.assertIn(expected, text)
        self.assertEqual('proposed', self.nodes['recovery']['status'])
        self.assertEqual('proposed', self.nodes['proxy']['status'])
        self.assertEqual('required-external', self.nodes['calendar']['status'])

    def test_migration_owner_is_separate_from_unprivileged_runtime(self):
        self.assertIn('SPRING_LIQUIBASE_ENABLED=false', self.nodes['app-config']['name'])
        self.assertIn('one-shot Liquibase', self.nodes['release']['name'])
        self.assertIn('owner-role', self.nodes['release']['name'])
        self.assertIn('не владелец', self.nodes['db-config']['name'])

    def test_replication_authentication_and_wal_receiver_are_explicit(self):
        self.assertIn('pg_receivewal', self.nodes['backup-runtime']['name'])
        for e in self.view['edges']:
            if e['id'] in ('I9', 'I10'):
                self.assertIn('SCRAM-SHA-256', e['protocol'])
                self.assertIn('verify-full', e['protocol'])

    def test_proxy_preserves_existing_frontend_and_backend_routes(self):
        self.assertIn('strip /api/', self.nodes['proxy-config']['name'])
        self.assertIn('/auth', self.nodes['proxy-config']['name'])

    def test_geometry_has_containment_attached_routes_and_clear_captions(self):
        validate_geometry(self.view)

    def test_geometry_rejects_broken_endpoint(self):
        bad = copy.deepcopy(self.view)
        bad['edges'][0]['points'][0] = [1, 1]
        with self.assertRaisesRegex(AssertionError, 'endpoint'):
            validate_geometry(bad)

    def test_geometry_rejects_route_through_its_own_endpoint(self):
        bad = copy.deepcopy(self.view)
        e = next(e for e in bad['edges'] if e['id'] == 'I2')
        e['points'] = [[1280, 790], [1280, 770], [1420, 770], [1420, 950]]
        with self.assertRaisesRegex(AssertionError, 'route through node'):
            validate_geometry(bad)

    def test_geometry_rejects_incorrect_parent_bounds(self):
        bad = copy.deepcopy(self.view)
        next(n for n in bad['nodes'] if n['id'] == 'backend')['x'] = 0
        with self.assertRaisesRegex(AssertionError, 'containment'):
            validate_geometry(bad)

    def test_device_notes_clear_two_line_native_stereotype_headers(self):
        for n in self.nodes.values():
            if n['type'] == 'NOTE' and n['parent']:
                p = self.nodes[n['parent']]
                if p['stereotype'] == 'device':
                    self.assertGreaterEqual(n['y'] - p['y'], 70, n['id'])

    def test_notes_do_not_fake_native_nodes_or_artifacts(self):
        for n in self.nodes.values():
            if n['type'] == 'NOTE':
                self.assertEqual(n['text'], n['name'])
            elif n['type'] == 'Node':
                self.assertIn(n.get('stereotype', ''), ['device', 'executionEnvironment', ''])
        self.assertGreaterEqual(len([n for n in self.nodes.values() if n['type'] == 'Artifact']), 6)


if __name__ == '__main__':
    unittest.main()
