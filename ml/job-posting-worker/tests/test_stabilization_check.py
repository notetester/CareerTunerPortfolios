import importlib.util
import sys
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "16_run_stabilization_check.py"


def load_script():
    spec = importlib.util.spec_from_file_location("stabilization_check", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class StabilizationCheckTest(unittest.TestCase):
    def test_evaluates_release_gates(self):
        module = load_script()
        results = [
            {"qualityStatus": "PASS", "metrics": {"sectionKeywordHitCount": 3}},
            {"qualityStatus": "REVIEW_REQUIRED", "metrics": {"sectionKeywordHitCount": 1}},
            {"qualityStatus": "FAILED", "metrics": {"sectionKeywordHitCount": 0}},
        ]

        summary = module.evaluate_results(
            results,
            elapsed_seconds=3.0,
            durations_seconds=[1.0, 1.0, 1.0],
            thresholds=module.StabilizationThresholds(
                pass_or_review_min_rate=0.60,
                failed_max_rate=0.40,
            ),
        )

        self.assertTrue(summary["passed"])
        self.assertEqual(summary["statusCounts"]["PASS"], 1)
        self.assertEqual(summary["passOrReviewRate"], 0.6667)

    def test_failed_rate_gate_fails_when_too_many_failures(self):
        module = load_script()
        results = [
            {"qualityStatus": "PASS", "metrics": {"sectionKeywordHitCount": 3}},
            {"qualityStatus": "FAILED", "metrics": {"sectionKeywordHitCount": 0}},
        ]

        summary = module.evaluate_results(results, elapsed_seconds=1.0)

        self.assertFalse(summary["passed"])
        self.assertFalse(summary["gates"]["failedRate"])

    def test_per_file_gate_uses_slowest_file_not_average(self):
        module = load_script()
        results = [
            {"qualityStatus": "PASS", "metrics": {"sectionKeywordHitCount": 3}},
            {"qualityStatus": "PASS", "metrics": {"sectionKeywordHitCount": 3}},
        ]

        summary = module.evaluate_results(
            results,
            elapsed_seconds=10.0,
            durations_seconds=[1.0, 9.0],
            thresholds=module.StabilizationThresholds(per_file_target_seconds=5.0),
        )

        self.assertEqual(summary["averageSecondsPerFile"], 5.0)
        self.assertEqual(summary["maxSecondsPerFile"], 9.0)
        self.assertFalse(summary["gates"]["perFileTargetSeconds"])

    def test_min_file_gate_fails_when_regression_set_is_too_small(self):
        module = load_script()
        results = [
            {"qualityStatus": "PASS", "metrics": {"sectionKeywordHitCount": 3}},
            {"qualityStatus": "PASS", "metrics": {"sectionKeywordHitCount": 3}},
        ]

        summary = module.evaluate_results(
            results,
            elapsed_seconds=2.0,
            durations_seconds=[1.0, 1.0],
            thresholds=module.StabilizationThresholds(min_file_count=50),
        )

        self.assertFalse(summary["passed"])
        self.assertFalse(summary["gates"]["minFileCount"])


if __name__ == "__main__":
    unittest.main()
