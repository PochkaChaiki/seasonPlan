import copy
import unittest
import xml.etree.ElementTree as ET
from validate_native import validate_models, validate_svg


class NativeValidationTests(unittest.TestCase):
    def setUp(self):
        self.data = {'classifiers': [dict(id='p.C', name='C', owner='', package='p', kind='class',
            visibility='public', abstract=False, final=False, literals=[],
            attributes=[dict(name='value', type='String', visibility='private', static=False, readonly=True)],
            operations=[dict(name='read', returns='String', visibility='public', static=False, abstract=False,
                constructor=False, parameters=[dict(name='key', type='UUID')])])], 'relationships': []}
        self.report = ET.fromstring('''<native-project><classifier id="p.C" name="C" owner="" package="p"
            kind="class" visibility="public" abstract="false" final="false">
            <attribute name="value" type="String" visibility="private" static="false" readonly="true"/>
            <operation name="read" returns="String" visibility="public" static="false" abstract="false" constructor="false">
            <parameter name="key" type="UUID"/></operation></classifier></native-project>''')

    def test_qualified_owner_captions_do_not_crowd_class_names(self):
        validate_svg(ET.fromstring('<svg><text>Example</text></svg>'))
        with self.assertRaises(AssertionError):
            validate_svg(ET.fromstring('<svg><text>(SeasonPlan source snapshot::com::example)</text></svg>'))

    def test_accepts_exact_native_members(self):
        validate_models(self.data, self.report)

    def test_rejects_parameter_type_or_visibility_loss(self):
        for tag, attr, value in [('parameter', 'type', 'String'), ('attribute', 'visibility', 'public')]:
            report = copy.deepcopy(self.report)
            report.find('.//' + tag).set(attr, value)
            with self.assertRaises(AssertionError):
                validate_models(self.data, report)

    def test_rejects_missing_class_and_synthetic_operation(self):
        report = copy.deepcopy(self.report)
        ET.SubElement(report.find('classifier'), 'operation', name='synthetic')
        with self.assertRaises(AssertionError):
            validate_models(self.data, report)
        with self.assertRaises(AssertionError):
            validate_models(self.data, ET.Element('native-project'))


if __name__ == '__main__':
    unittest.main()
