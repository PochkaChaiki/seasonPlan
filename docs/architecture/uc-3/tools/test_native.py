import unittest
from xml.etree import ElementTree as ET
from validate_native import check_message, check_activation


class NativeReceiptTest(unittest.TestCase):
    def setUp(self):
        self.spec = ET.Element('message', id='m', **{'from':'a','to':'b','y':'300','kind':'call','operation':'x','text':'f()'})
        self.native = ET.Element('message', id='m', name='f()', type='Message',
            **{'from':'d:a','to':'d:b','fromShape':'d:a','toShape':'d:b','points':'265,300;635,300',
               'operation':'op:x','captionSide':'1','caption':'300,264,320,30'})
        self.lines = {'a': (120,140,290,1000), 'b':(490,140,290,1000)}

    def test_accepts_attached_source_backed_call(self):
        check_message(self.spec,self.native,'d',self.lines)

    def test_activation_has_native_owner_and_persisted_bounds(self):
        spec = ET.Element('activation', id='exec', lifeline='a', x='261', y='300', w='8', h='30')
        actual = ET.Element('shape', id='exec', type='Activation', lifeline='d:a', bounds='261,300,8,30')
        check_activation(spec, actual, 'd')
        for key, value in [('type','NOTE'), ('lifeline','d:b'), ('bounds','200,300,8,30')]:
            bad = ET.Element('shape', actual.attrib)
            bad.set(key,value)
            with self.assertRaises(AssertionError):
                check_activation(spec,bad,'d')

    def test_rejects_detached_arrow_or_wrong_signature(self):
        for key, value in [('toShape','d:a'),('points','265,300;600,300'),
                           ('operation',''),('captionSide','12')]:
            with self.subTest(key=key):
                bad = ET.Element('message',self.native.attrib)
                bad.set(key,value)
                with self.assertRaises(AssertionError):
                    check_message(self.spec,bad,'d',self.lines)


if __name__ == '__main__':
    unittest.main()
