"""Regression checks for the UC-2 UML activity source (standard library only)."""
import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location("diagram_model", Path(__file__).with_name("diagram_model.py"))
model = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(model)


class ActivityTests(unittest.TestCase):
    def setUp(self):
        self.diagrams = model.build_diagrams()

    def test_three_abstraction_levels(self):
        self.assertEqual([d["id"] for d in self.diagrams], ["01-use-case", "02-logical", "03-implementation"])
        self.assertIn("Абстрактное описание", self.diagrams[0]["subtitle"])
        self.assertIn("диаграммой пакетов", self.diagrams[1]["subtitle"])
        self.assertIn("методов", self.diagrams[2]["subtitle"])

    def test_native_uml2_graph_and_geometry(self):
        for diagram in self.diagrams:
            with self.subTest(diagram=diagram["id"]):
                model.validate(diagram)
                kinds = {n["kind"] for n in diagram["nodes"]}
                self.assertTrue({"initial", "final", "action", "decision", "merge"} <= kinds)
                self.assertFalse({"state", "actionState", "transition"} & kinds)
                for node in diagram["nodes"]:
                    self.assertIn(node["lane"], [p["id"] for p in diagram["lanes"]])

    def test_approval_has_verified_and_pollen_guards(self):
        for diagram in self.diagrams:
            text = "\n".join(n["text"] for n in diagram["nodes"])
            self.assertIn("VERIFIED", text)
            self.assertIn("CONFIRMED", text)
            self.assertIn("DRAFT", text)
            self.assertIn("APPROVED", text)
            for forbidden in ("CANCELLED", "EMERGENCY", "CORRECTION"):
                self.assertNotIn(forbidden, text)
            edges = {(e["from"], e["to"]): e for e in diagram["edges"]}
            self.assertIn(("reservation", "approve"), edges)
            self.assertIn("CONFIRMED", edges["reservation", "approve"]["guard"])
            if diagram["id"] == "01-use-case":
                self.assertIn(("verify", "decision"), edges)
            else:
                self.assertIn(("verify", "review-merge"), edges)
                self.assertIn(("review-merge", "decision"), edges)

    def test_all_alternatives_and_transaction_boundary(self):
        for diagram in self.diagrams:
            ids = {n["id"] for n in diagram["nodes"]}
            self.assertTrue({"return", "conditional", "pollen-block", "audit", "notify", "receive"} <= ids)
        text = "\n".join(n["text"] for n in self.diagrams[2]["nodes"])
        for method in ("ApprovalService", "returnForRevision", "PollenService", "findLatestBySeasonPlanId", "AuditLogService", "append", "CommandTransaction", "NotificationOutbox", "enqueue", "SeasonPlanRepository", "save"):
            self.assertIn(method, text)
        self.assertIn("проект", text)

    def test_implementation_has_typed_native_object_outputs(self):
        objects = [n for n in self.diagrams[2]["nodes"] if n["kind"] == "object"]
        self.assertEqual({n["type"] for n in objects}, {"SeasonPlan", "Optional<PollenReservation>", "AuditLogEntry"})
        self.assertEqual(sum(e["kind"] == "object" for e in self.diagrams[2]["edges"]), 3)

    def test_response_does_not_wait_for_notification_delivery(self):
        for diagram in self.diagrams[1:]:
            nodes={n['id']:n for n in diagram['nodes']}
            self.assertEqual(nodes['after-commit']['kind'], 'fork')
            self.assertEqual(nodes['delivery-join']['kind'], 'join')
            edges={(e['from'],e['to']) for e in diagram['edges']}
            self.assertTrue({('after-commit','response'),('after-commit','notify'),('response','delivery-join')} <= edges)
        approval=next(n for n in self.diagrams[2]['nodes'] if n['id']=='approval-command')
        self.assertIn('conditions', approval['text'])

    def test_real_exception_paths_and_explicit_commit(self):
        for d in self.diagrams[1:]:
            nodes={n['id']:n for n in d['nodes']}
            self.assertEqual(nodes['commit']['kind'],'action')
            self.assertEqual(nodes['rollback']['kind'],'handler')
            catches={e['from'] for e in d['edges'] if e['kind']=='exception' and e['to']=='rollback'}
            self.assertTrue({'audit','save','outbox','commit','verify','blocked'} <= catches)
            self.assertFalse(any(e['from']=='blocked' and e['kind']=='control' for e in d['edges']))
            self.assertEqual(nodes['rollback-final']['parent'],'rollback')
            self.assertEqual(nodes['audit']['lane'],'application')
            self.assertEqual(nodes['return-tx']['lane'],'application')

    def test_logical_partitions_match_package_reference(self):
        self.assertEqual([p["id"] for p in self.diagrams[1]["lanes"]], [p["id"] for p in model.PACKAGES])
        for package in model.PACKAGES:
            self.assertTrue(package["paths"])

    def test_invalid_decision_and_disconnected_node_rejected(self):
        diagram = self.diagrams[0]
        diagram["edges"][0]["to"] = "missing"
        with self.assertRaises(ValueError):
            model.validate(diagram)

    def test_alternative_paths_use_merge_not_implicit_join(self):
        for diagram in self.diagrams:
            incoming = {}
            for edge in diagram["edges"]:
                if edge["kind"] == "control":
                    incoming.setdefault(edge["to"], []).append(edge)
            for node in diagram["nodes"]:
                if node["kind"] == "action":
                    self.assertLessEqual(len(incoming.get(node["id"], [])), 1, node["id"])


if __name__ == "__main__":
    unittest.main()
