"""Reject plausible-looking diagrams with incorrect persisted UML semantics."""
import copy
import unittest
import xml.etree.ElementTree as ET
from deployment_model import model
from validate_native import validate_tree


def fixture(view):
    root = ET.Element('native-project')
    d = ET.SubElement(root, 'diagram', id=view['id'], name=view['name'], type='DeploymentDiagram')
    for n in view['nodes']:
        ET.SubElement(root, 'model', id=n['id'], name=n['name'], type=n['type'], parent=n['parent'] or 'root', stereotype=n['stereotype'])
        ET.SubElement(d, 'shape', id=n['id'], name=n['name'], type=n['type'], parent=n['parent'] or 'root', shapeParent=n['parent'],
                      bounds=','.join(str(n[k]) for k in ('x', 'y', 'w', 'h')), documentation=n['name'] if n['type'] == 'NOTE' else '')
        if n['type'] in ('Artifact', 'DeploymentSpecification'):
            ET.SubElement(root, 'model', id='deploy:'+n['id'], type='Deployment', **{'from': n['parent'], 'to': n['id']})
    for e in view['edges']:
        ET.SubElement(d, 'edge', id=e['id'], name=e['name'], type='Association', **{'from': e['from'], 'to': e['to']},
                      fromShape=e['from'], toShape=e['to'], points=';'.join(','.join(map(str, p)) for p in e['points']),
                      caption=','.join(map(str, e['caption'])), captionSide='1', documentation=e['protocol']+'\nEvidence: '+e['evidence'])
    return root


class NativeValidationTest(unittest.TestCase):
    def setUp(self):
        self.view = model()
        self.tree = fixture(self.view)

    def test_complete_semantic_fixture_passes(self):
        validate_tree(self.view, self.tree)

    def test_rejects_class_styled_as_node(self):
        self.tree.find("model[@id='jvm']").set('type', 'Class')
        with self.assertRaisesRegex(AssertionError, 'model type'):
            validate_tree(self.view, self.tree)

    def test_rejects_missing_execution_environment_stereotype(self):
        self.tree.find("model[@id='jvm']").set('stereotype', '')
        with self.assertRaisesRegex(AssertionError, 'stereotype'):
            validate_tree(self.view, self.tree)

    def test_rejects_deployment_reversed_to_artifact(self):
        self.tree.find("model[@id='deploy:backend']").set('to', 'jvm')
        with self.assertRaisesRegex(AssertionError, 'deployment'):
            validate_tree(self.view, self.tree)

    def test_rejects_only_geometric_artifact_containment(self):
        self.tree.find("diagram/shape[@id='backend']").set('shapeParent', '')
        with self.assertRaisesRegex(AssertionError, 'shape parent'):
            validate_tree(self.view, self.tree)

    def test_rejects_dependency_as_communication_path(self):
        self.tree.find('diagram/edge').set('type', 'Dependency')
        with self.assertRaisesRegex(AssertionError, 'Association'):
            validate_tree(self.view, self.tree)

    def test_rejects_note_identity_in_visible_text(self):
        self.tree.find("diagram/shape[@id='title']").set('documentation', 'sourceId:title')
        with self.assertRaisesRegex(AssertionError, 'note'):
            validate_tree(self.view, self.tree)

    def test_rejects_wrong_native_shape_endpoint(self):
        self.tree.find('diagram/edge').set('toShape', 'jvm')
        with self.assertRaisesRegex(AssertionError, 'endpoint'):
            validate_tree(self.view, self.tree)

    def test_rejects_wrong_caption_interaction_mode(self):
        self.tree.find('diagram/edge').set('captionSide', '12')
        with self.assertRaisesRegex(AssertionError, 'caption'):
            validate_tree(self.view, self.tree)


if __name__ == '__main__':
    unittest.main()
