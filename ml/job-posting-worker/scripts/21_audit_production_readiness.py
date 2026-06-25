"""Audit production readiness evidence for the full job-posting pipeline."""

from __future__ import annotations

import argparse
import importlib.util
import json
import shutil
from dataclasses import dataclass
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_REPO_ROOT = SCRIPT_DIR.parents[2]
DEFAULT_PRODUCTION_TARGET_COUNT = 43


@dataclass(frozen=True)
class AuditCheck:
    name: str
    passed: bool
    message: str
    evidence: str | None = None
    blocking: bool = True

    def to_dict(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "passed": self.passed,
            "message": self.message,
            "evidence": self.evidence,
            "blocking": self.blocking,
        }


def read_json(path: Path) -> dict[str, Any] | None:
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8", errors="replace"))


def has_module(module_name: str) -> bool:
    return importlib.util.find_spec(module_name) is not None


def check_release_readiness(path: Path) -> AuditCheck:
    payload = read_json(path)
    return AuditCheck(
        "release readiness target-file gate",
        bool(payload and payload.get("ok")),
        "production release readiness must pass for the configured target count.",
        str(path),
    )


def check_inventory(path: Path, target_count: int) -> list[AuditCheck]:
    payload = read_json(path)
    if payload is None:
        return [AuditCheck("real regression inventory", False, "missing real regression inventory artifact", str(path))]
    ready = int(payload.get("readyJobPostingCount", 0))
    unique_ready = int(payload.get("uniqueReadyJobPostingCount", ready))
    duplicate_extra = int(payload.get("duplicateReadyExtraCount", 0))
    missing_ocr = int(payload.get("missingOcrJobPostingCount", 0))
    non_job = int(payload.get("nonJobReferenceCount", 0))
    return [
        AuditCheck(
            "real regression target count",
            unique_ready >= target_count,
            f"unique ready real job-posting count must be >= {target_count}; current={unique_ready}",
            str(path),
        ),
        AuditCheck(
            "real regression duplicate check",
            duplicate_extra == 0,
            f"exact duplicate ready postings must be 0; current={duplicate_extra}",
            str(path),
        ),
        AuditCheck(
            "real regression OCR backfill",
            missing_ocr == 0,
            f"real job-posting candidates missing OCR text must be 0; current={missing_ocr}",
            str(path),
        ),
        AuditCheck(
            "real regression non-job references excluded",
            non_job >= 0,
            f"non-job reference documents are tracked separately; current={non_job}",
            str(path),
        ),
    ]


def check_stabilization(path: Path, target_count: int) -> AuditCheck:
    payload = read_json(path)
    total = int(payload.get("total", 0)) if payload else 0
    passed = bool(payload and payload.get("passed") and total >= target_count)
    return AuditCheck(
        "real stabilization production gate",
        passed,
        f"latest real stabilization summary must pass with at least {target_count} files; current={total}",
        str(path),
    )


def check_regression_set(path: Path, target_count: int) -> AuditCheck:
    payload = read_json(path)
    selected = int(payload.get("selectedCount", 0)) if payload else 0
    return AuditCheck(
        "real regression set manifest",
        bool(payload and payload.get("ok") and selected >= target_count),
        f"prepared real regression set manifest must be ok with at least {target_count} files; current={selected}",
        str(path),
    )


def check_worker_drills(path: Path) -> AuditCheck:
    payload = read_json(path)
    return AuditCheck(
        "worker operational drills",
        bool(payload and payload.get("ok") and int(payload.get("failed", 1)) == 0),
        "worker operational drills must pass with zero failures.",
        str(path),
    )


def check_json_ok(path: Path, name: str, message: str) -> AuditCheck:
    payload = read_json(path)
    return AuditCheck(name, bool(payload and payload.get("ok")), message, str(path))


def check_local_tools() -> list[AuditCheck]:
    docker_available = shutil.which("docker") is not None
    paddle_available = has_module("paddleocr") and has_module("paddle")
    return [
        AuditCheck(
            "local docker CLI available",
            docker_available,
            "Docker CLI is needed to produce local worker image/runtime smoke evidence.",
            None,
            blocking=False,
        ),
        AuditCheck(
            "local PaddleOCR runtime available",
            paddle_available,
            "paddleocr and paddlepaddle are needed for local OCR backfill without pre-generated OCR text.",
            None,
            blocking=False,
        ),
    ]


def run_audit(
    repo_root: Path = DEFAULT_REPO_ROOT,
    *,
    target_count: int,
    release_readiness: Path | None = None,
    inventory: Path | None = None,
    stabilization_summary: Path | None = None,
    regression_set: Path | None = None,
    worker_drills: Path | None = None,
    docker_smoke: Path | None = None,
    ocr_runtime: Path | None = None,
    db_migration: Path | None = None,
) -> dict[str, Any]:
    repo_root = repo_root.resolve()
    tmp = repo_root / ".tmp"
    release_readiness = release_readiness or tmp / f"job_posting_release_readiness_{target_count}.json"
    inventory = inventory or tmp / "job_posting_real_regression_inventory.json"
    stabilization_summary = stabilization_summary or tmp / "job_posting_production_stabilization" / "stabilization_summary.json"
    regression_set = regression_set or tmp / "job_posting_real_regression_set" / "manifest.json"
    worker_drills = worker_drills or tmp / "job_posting_worker_drills.json"
    docker_smoke = docker_smoke or tmp / "job_posting_worker_docker_smoke.json"
    ocr_runtime = ocr_runtime or tmp / "job_posting_worker_ocr_runtime.json"
    db_migration = db_migration or tmp / "application_case_pipeline_db_migration.json"

    checks: list[AuditCheck] = [
        check_release_readiness(release_readiness),
        *check_inventory(inventory, target_count),
        check_regression_set(regression_set, target_count),
        check_stabilization(stabilization_summary, target_count),
        check_worker_drills(worker_drills),
        check_json_ok(docker_smoke, "worker Docker runtime smoke", "worker image runtime smoke must pass."),
        check_json_ok(ocr_runtime, "worker OCR runtime smoke", "OCR-capable worker smoke must pass."),
        check_json_ok(db_migration, "staging DB migration evidence", "staging database must prove required pipeline schema is applied."),
        *check_local_tools(),
    ]
    blocking = [item for item in checks if item.blocking and not item.passed]
    return {
        "ready": not blocking,
        "repoRoot": str(repo_root),
        "targetCount": target_count,
        "checks": [item.to_dict() for item in checks],
        "blockingItems": [item.name for item in blocking],
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=DEFAULT_REPO_ROOT)
    parser.add_argument("--target-count", type=int, default=DEFAULT_PRODUCTION_TARGET_COUNT)
    parser.add_argument("--release-readiness", type=Path, default=None)
    parser.add_argument("--inventory", type=Path, default=None)
    parser.add_argument("--stabilization-summary", type=Path, default=None)
    parser.add_argument("--regression-set", type=Path, default=None)
    parser.add_argument("--worker-drills", type=Path, default=None)
    parser.add_argument("--docker-smoke", type=Path, default=None)
    parser.add_argument("--ocr-runtime", type=Path, default=None)
    parser.add_argument("--db-migration", type=Path, default=None)
    parser.add_argument("--output", type=Path, default=None)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    summary = run_audit(
        args.repo_root,
        target_count=args.target_count,
        release_readiness=args.release_readiness,
        inventory=args.inventory,
        stabilization_summary=args.stabilization_summary,
        regression_set=args.regression_set,
        worker_drills=args.worker_drills,
        docker_smoke=args.docker_smoke,
        ocr_runtime=args.ocr_runtime,
        db_migration=args.db_migration,
    )
    encoded = json.dumps(summary, ensure_ascii=False, indent=2)
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded + "\n", encoding="utf-8")
    print(encoded)
    raise SystemExit(0 if summary["ready"] else 1)


if __name__ == "__main__":
    main()
