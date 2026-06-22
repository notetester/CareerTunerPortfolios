import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "19_run_synthetic_stabilization_fixture.py"


def load_script():
    spec = importlib.util.spec_from_file_location("synthetic_stabilization_fixture", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class SyntheticStabilizationFixtureTest(unittest.TestCase):
    def test_generates_passing_min_file_gate_summary(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            summary = module.run(
                count=5,
                output_dir=root / "out",
                report_path=root / "report.md",
            )

        self.assertTrue(summary["passed"])
        self.assertEqual(summary["total"], 5)
        self.assertTrue(summary["gates"]["minFileCount"])
        self.assertEqual(summary["failedRate"], 0.0)


if __name__ == "__main__":
    unittest.main()
