import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "27_summarize_release_evidence.py"


def load_script():
    spec = importlib.util.spec_from_file_location("release_evidence_report", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class ReleaseEvidenceReportTest(unittest.TestCase):
    def test_summarizes_blockers_steps_and_artifacts(self):
        module = load_script()
        payload = {
            "ok": False,
            "targetCount": 43,
            "repoRoot": "D:/dev/FinalProject",
            "blockingItems": ["real regression target count"],
            "productionReadiness": "D:/dev/FinalProject/.tmp/job_posting_production_readiness.json",
            "steps": [
                {
                    "name": "real regression inventory",
                    "ok": False,
                    "evidence": "inventory.json",
                    "payload": {
                        "targetCount": 43,
                        "readyJobPostingCount": 20,
                        "uniqueReadyJobPostingCount": 20,
                        "duplicateReadyExtraCount": 0,
                        "missingOcrJobPostingCount": 2,
                        "nonJobReferenceCount": 4,
                        "additionalReadyNeeded": 23,
                        "ocrBackfillNeeded": ["베이클코드 마케팅 매니저 - 자체공고.pdf"],
                    },
                },
                {
                    "name": "worker operational drills",
                    "ok": True,
                    "evidence": "drills.json",
                    "payload": {"total": 5, "failed": 0},
                },
            ],
        }

        report = module.summarize(payload)

        self.assertIn("# Job Posting Pipeline Release Evidence", report)
        self.assertIn("- status: FAIL", report)
        self.assertIn("- real regression target count", report)
        self.assertIn("| real regression inventory | FAIL | ready=20/43, unique=20, dupExtra=0, missingOcr=2, nonJob=4 | inventory.json |", report)
        self.assertIn("- additional ready postings needed: 23", report)
        self.assertIn("베이클코드 마케팅 매니저 - 자체공고.pdf", report)

    def test_writes_report_file(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "report.md"
            module.write_report({"ok": True, "targetCount": 43, "steps": [], "blockingItems": []}, output)

            text = output.read_text(encoding="utf-8")

        self.assertIn("- status: PASS", text)
        self.assertIn("- none", text)


if __name__ == "__main__":
    unittest.main()
