"""Check release readiness evidence for the job posting extraction pipeline."""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_REPO_ROOT = SCRIPT_DIR.parents[2]


@dataclass(frozen=True)
class ReadinessCheck:
    name: str
    passed: bool
    message: str

    def to_dict(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "passed": self.passed,
            "message": self.message,
        }


def check(condition: bool, name: str, message: str) -> ReadinessCheck:
    return ReadinessCheck(name=name, passed=condition, message=message)


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def json_file(path: Path) -> dict[str, Any]:
    return json.loads(read_text(path))


def contains_all(path: Path, snippets: list[str]) -> bool:
    if not path.exists():
        return False
    text = read_text(path)
    return all(snippet in text for snippet in snippets)


def static_checks(repo_root: Path) -> list[ReadinessCheck]:
    worker_root = repo_root / "ml" / "job-posting-worker"
    checks = [
        check((worker_root / "Dockerfile").exists(), "worker Dockerfile", "worker image build file exists"),
        check((worker_root / "requirements.txt").exists(), "worker requirements", "worker Python dependencies are declared"),
        check((worker_root / "requirements-ocr.txt").exists(), "worker OCR requirements", "optional PaddleOCR dependencies are declared"),
    ]
    for script in (
        "14_extract_document_text.py",
        "15_job_posting_worker_api.py",
        "16_run_stabilization_check.py",
        "17_run_worker_drills.py",
        "18_check_release_readiness.py",
        "19_run_synthetic_stabilization_fixture.py",
        "20_audit_real_regression_inventory.py",
        "21_audit_production_readiness.py",
        "22_verify_mysql_pipeline_schema.py",
        "23_run_worker_docker_smoke.py",
        "24_run_ocr_runtime_smoke.py",
        "25_prepare_real_regression_set.py",
        "26_run_release_evidence.py",
        "27_summarize_release_evidence.py",
        "28_import_real_regression_candidates.py",
    ):
        checks.append(check(
            (worker_root / "scripts" / script).exists(),
            f"worker script {script}",
            f"{script} is tracked in the service module",
        ))

    compose = repo_root / "docker-compose.yml"
    dockerfile = worker_root / "Dockerfile"
    checks.append(check(
        contains_all(compose, [
            "job-posting-worker:",
            "JOB_POSTING_AI_WORKER_BASE_URL",
            "http://job-posting-worker:8091",
            "media_uploads:/app/.uploads:ro",
            "job_posting_ai_cache:/app/.cache/job-posting-ai",
            "JOB_POSTING_AI_CACHE_DIR",
            "condition: service_healthy",
            "JOB_POSTING_WORKER_INSTALL_OCR",
        ]),
        "compose worker service",
        "compose wires backend to the internal worker, shared upload volume, and persistent OCR cache",
    ))
    checks.append(check(
        contains_all(dockerfile, ["INSTALL_OCR", "requirements-ocr.txt", "libgomp1", "JOB_POSTING_AI_CACHE_DIR"]),
        "worker OCR image option",
        "worker image includes the optional PaddleOCR dependencies, native runtime, and writable cache",
    ))
    extraction_script = worker_root / "scripts" / "14_extract_document_text.py"
    checks.append(check(
        contains_all(extraction_script, ["PADDLE_OCR", "paddle_ocr_text", "paddleocr_unavailable"]),
        "worker local OCR execution path",
        "document extraction attempts local PaddleOCR before failing OCR-candidate files",
    ))

    workflow = repo_root / ".github" / "workflows" / "service-pipeline-ci.yml"
    frontend_workflow = repo_root / ".github" / "workflows" / "frontend-ci.yml"
    checks.append(check(
        contains_all(workflow, [
            "./gradlew test",
            "python -m unittest discover -s tests",
            "python scripts/17_run_worker_drills.py",
            "python scripts/19_run_synthetic_stabilization_fixture.py --count 43",
            "python scripts/18_check_release_readiness.py",
            "docker compose config",
            "python ml/job-posting-worker/scripts/23_run_worker_docker_smoke.py",
            "--install-ocr",
            "import paddle, paddleocr, paddlex, fitz, cv2",
        ]),
        "service pipeline CI",
        "CI covers backend tests, worker tests, worker drills, stabilization, compose config, and both worker image variants",
    ))
    checks.append(check(
        contains_all(frontend_workflow, [
            "frontend/**",
            "ApplicationCaseExtractionResponse.java",
            "ReviewJobPostingExtractionRequest.java",
            "ApplicationCaseController.java",
        ]),
        "frontend contract CI paths",
        "frontend CI runs when application extraction API contracts change",
    ))

    schema = repo_root / "backend" / "src" / "main" / "resources" / "db" / "schema.sql"
    quality_patch = repo_root / "backend" / "src" / "main" / "resources" / "db" / "patches" / "20260617_b_application_case_extraction_quality_gate.sql"
    runtime_patch = repo_root / "backend" / "src" / "main" / "resources" / "db" / "patches" / "20260617_c_ai_runtime_setting.sql"
    quality_columns = [
        "extraction_strategy",
        "quality_score",
        "quality_status",
        "quality_report_json",
        "model_versions_json",
        "fallback_eligible",
        "fallback_reason",
        "reviewed_at",
    ]
    checks.append(check(
        contains_all(schema, quality_columns) and contains_all(quality_patch, quality_columns),
        "quality metadata migration",
        "schema and patch both define extraction quality metadata columns",
    ))
    checks.append(check(
        contains_all(schema, ["ai_runtime_setting", "setting_key", "value_json", "updated_by"])
        and contains_all(runtime_patch, ["ai_runtime_setting", "setting_key", "value_json", "updated_by"]),
        "runtime fallback setting migration",
        "schema and patch both define persistent runtime AI fallback settings",
    ))

    auto_pipeline = repo_root / "backend" / "src" / "main" / "java" / "com" / "careertuner" / "applicationcase" / "service" / "ApplicationCaseAutoPipelineService.java"
    extraction_worker = repo_root / "backend" / "src" / "main" / "java" / "com" / "careertuner" / "applicationcase" / "service" / "ApplicationCaseExtractionWorker.java"
    application_service = repo_root / "backend" / "src" / "main" / "java" / "com" / "careertuner" / "applicationcase" / "service" / "ApplicationCaseServiceImpl.java"
    checks.append(check(
        contains_all(auto_pipeline, [
            "MockFitAnalysisAiService",
            "insertJobAnalysis",
            "insertCompanyAnalysis",
            "insertFitAnalysis",
            "insertQuestion",
            "self-rules-v1",
        ])
        and contains_all(extraction_worker, ["autoPipelineService.runAfterExtractionPass"])
        and contains_all(application_service, ["autoPipelineService.runAfterExtractionPass"]),
        "self AI automatic analysis pipeline",
        "PASS extraction and user review continue through self-hosted job/company/fit/interview preparation without OpenAI client dependencies",
    ))

    runbook = repo_root / "docs" / "AI_JOB_POSTING_PIPELINE_RUNBOOK.md"
    checks.append(check(
        contains_all(runbook, [
            "OpenAI is not part of the default path",
            "self-rules-v1",
            "INSTALL_OCR=true",
            "JOB_POSTING_AI_WORKER_BASE_URL=http://job-posting-worker:8091",
            "JOB_POSTING_AI_CACHE_DIR",
            "--min-files 20",
            "--min-files 43",
            "20_audit_real_regression_inventory.py",
            "21_audit_production_readiness.py",
            "22_verify_mysql_pipeline_schema.py",
            "23_run_worker_docker_smoke.py",
            "24_run_ocr_runtime_smoke.py",
            "25_prepare_real_regression_set.py",
            "26_run_release_evidence.py",
            "27_summarize_release_evidence.py",
            "28_import_real_regression_candidates.py",
            "docker compose config",
        ]),
        "pipeline runbook",
        "runbook documents default fallback policy, worker routing, baseline and production gates",
    ))
    return checks


def artifact_checks(stabilization_summary: Path, worker_drills: Path, min_files: int) -> list[ReadinessCheck]:
    checks: list[ReadinessCheck] = []
    if not stabilization_summary.exists():
        checks.append(check(False, "stabilization artifact", f"missing {stabilization_summary}"))
    else:
        summary = json_file(stabilization_summary)
        checks.append(check(
            bool(summary.get("passed")),
            "stabilization gates",
            "latest stabilization summary passed all quality gates",
        ))
        checks.append(check(
            int(summary.get("total", 0)) >= min_files,
            "stabilization min file count",
            f"latest stabilization summary has at least {min_files} files",
        ))
        gates = summary.get("gates", {})
        checks.append(check(
            isinstance(gates, dict) and all(bool(value) for value in gates.values()),
            "stabilization individual gates",
            "all individual stabilization gates are true",
        ))

    if not worker_drills.exists():
        checks.append(check(False, "worker drills artifact", f"missing {worker_drills}"))
    else:
        drills = json_file(worker_drills)
        checks.append(check(
            bool(drills.get("ok")) and int(drills.get("failed", 1)) == 0,
            "worker operational drills",
            "latest worker operational drills passed",
        ))
    return checks


def run_check(
    repo_root: Path = DEFAULT_REPO_ROOT,
    *,
    include_artifacts: bool,
    min_files: int,
    stabilization_summary: Path | None = None,
    worker_drills: Path | None = None,
) -> dict[str, Any]:
    repo_root = repo_root.resolve()
    stabilization_summary = stabilization_summary or repo_root / ".tmp" / "job_posting_stabilization" / "stabilization_summary.json"
    worker_drills = worker_drills or repo_root / ".tmp" / "job_posting_worker_drills.json"
    checks = static_checks(repo_root)
    if include_artifacts:
        checks.extend(artifact_checks(stabilization_summary, worker_drills, min_files))
    return {
        "ok": all(item.passed for item in checks),
        "repoRoot": str(repo_root),
        "includeArtifacts": include_artifacts,
        "minFiles": min_files,
        "checks": [item.to_dict() for item in checks],
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=DEFAULT_REPO_ROOT)
    parser.add_argument("--include-artifacts", action="store_true")
    parser.add_argument("--min-files", type=int, default=20)
    parser.add_argument("--stabilization-summary", type=Path, default=None)
    parser.add_argument("--worker-drills", type=Path, default=None)
    parser.add_argument("--output", type=Path, default=None)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    summary = run_check(
        args.repo_root,
        include_artifacts=args.include_artifacts,
        min_files=args.min_files,
        stabilization_summary=args.stabilization_summary,
        worker_drills=args.worker_drills,
    )
    encoded = json.dumps(summary, ensure_ascii=False, indent=2)
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded + "\n", encoding="utf-8")
    print(encoded)
    raise SystemExit(0 if summary["ok"] else 1)


if __name__ == "__main__":
    main()
