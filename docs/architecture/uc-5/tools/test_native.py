"""Native receipt regression tests; intentionally reject plausible-looking wrong UML."""
import unittest
import xml.etree.ElementTree as ET
from validate_native import check_link, check_message


class NativeContract(unittest.TestCase):
    def test_link_must_touch_both_intended_participants(self):
        spec=ET.fromstring('<link id="L" from="a" to="b" points="100,50;300,50"/>')
        native=ET.fromstring('<link id="L" from="d:a" to="d:b" fromShape="d:a" toShape="d:b" points="100,50;300,50"/>')
        check_link(spec,native,'d',{'a':(0,0,100,100),'b':(300,0,100,100)})
        native.set('points','100,50;250,50')
        with self.assertRaises(AssertionError):
            check_link(spec,native,'d',{'a':(0,0,100,100),'b':(300,0,100,100)})

    def test_numbered_message_must_belong_to_correct_native_link(self):
        spec=ET.fromstring('<message id="M" from="a" to="b" number="1.2" link="L" text="save()" operation="save" guard="valid" iteration="false"/>')
        native=ET.fromstring('<message id="M" from="d:a" to="d:b" number="1.2" link="L" name="save()" operation="op:save" guard="valid" iteration="false" captionSide="1" shapeType="CommunicationMessage"/>')
        check_message(spec,native,'d')
        for field,value in [('number','2'),('link','wrong'),('operation',''),('guard',''),('to','d:a')]:
            broken=ET.fromstring(ET.tostring(native));broken.set(field,value)
            with self.assertRaises(AssertionError):check_message(spec,broken,'d')


if __name__=='__main__':unittest.main()
