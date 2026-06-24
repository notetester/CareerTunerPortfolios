import importlib.util
import sys
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "17_run_worker_drills.py"


def load_script():
    spec = importlib.util.spec_from_file_location("job_posting_worker_drills", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def valid_payload() -> dict:
    meta = {
        "strategy": "TEXT_DIRECT",
        "qualityScore": 92,
        "qualityStatus": "PASS",
        "metrics": {"textLength": 800},
        "warnings": [],
        "sectionHints": ["company", "role"],
        "modelVersions": {"documentExtractionContract": "self_ai_v1"},
        "fallbackEligible": False,
        "generatedAt": "2026-06-17T00:00:00+00:00",
    }
    return {"text": "Company: Acme\nRole: Backend Engineer", "meta": meta, **meta}


class WorkerDrillsContractTest(unittest.TestCase):
    def test_accepts_complete_worker_contract(self):
        module = load_script()

        self.assertEqual([], module.contract_errors(valid_payload()))

    def test_rejects_missing_meta_contract_fields(self):
        module = load_script()
        payload = valid_payload()
        del payload["meta"]["modelVersions"]

        errors = module.contract_errors(payload)

        self.assertIn("meta.modelVersions must be dict", errors)

    def test_rejects_meta_top_level_mismatch(self):
        module = load_script()
        payload = valid_payload()
        payload["meta"]["qualityStatus"] = "REVIEW_REQUIRED"

        errors = module.contract_errors(payload)

        self.assertIn("meta.qualityStatus must match top-level qualityStatus", errors)


if __name__ == "__main__":
    unittest.main()
