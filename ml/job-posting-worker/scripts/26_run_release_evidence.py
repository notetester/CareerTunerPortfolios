"""Collect all local production-readiness evidence for the job-posting pipeline."""

from __future__ import annotations

import argparse
import importlib.util
import json
import os
import sys
from pathlib import Path
from typing import Any, Callable


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_REPO_ROOT = SCRIPT_DIR.parents[2]


def load_script(module_name: str, script_name: str):
    spec = importlib.util.spec_from_file_location(module_name, SCRIPT_DIR / script_name)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


STABILIZATION = load_script("release_evidence_stabilization", "16_run_stabilization_check.py")
DRILLS = load_script("release_evidence_drills", "17_run_worker_drills.py")
READINESS = load_script("release_evidence_readiness", "18_check_release_readiness.py")
INVENTORY = load_script("release_evidence_inventory", "20_audit_real_regression_inventory.py")
PRODUCTION = load_script("release_evidence_production", "21_audit_production_readiness.py")
DB_SCHEMA = load_script("release_evidence_db_schema", "22_verify_mysql_pipeline_schema.py")
DOCKER_SMOKE = load_script("release_evidence_docker_smoke", "23_run_worker_docker_smoke.py")
OCR_SMOKE = load_script("release_evidence_ocr_smoke", "24_run_ocr_runtime_smoke.py")
REGRESSION_SET = load_script("release_evidence_regression_set", "25_prepare_real_regression_set.py")
REPORT = load_script("release_evidence_report", "27_summarize_release_evidence.py")


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def collect_step(name: str, evidence: Path | None, producer: Callable[[], dict[str, Any]], ok_key: str = "ok") -> dict[str, Any]:
    try:
        payload = producer()
        ok = bool(payload.get(ok_key))
        return {
            "name": name,
            "ok": ok,
            "evidence": str(evidence) if evidence else None,
            "payload": payload,
        }
    except Exception as exc:  # noqa: BLE001 - evidence collection should continue after one failed step.
        payload = {"ok": False, "error": str(exc)}
        if evidence is not None:
            write_json(evidence, payload)
        return {
            "name": name,
            "ok": False,
            "evidence": str(evidence) if evidence else None,
            "payload": payload,
        }


def run_release_evidence(
    repo_root: Path = DEFAULT_REPO_ROOT,
    *,
    target_count: int,
    raw_dir: Path | None = None,
    ocr_dir: Path | None = None,
    include_runtime_smoke: bool = True,
    include_db_check: bool = True,
    db_host: str = "127.0.0.1",
    db_port: int = 3306,
    db_database: str = "team1_db",
    db_user: str = "root",
    db_password_env: str = "DB_PASSWORD",
    mysql_bin: str = "mysql",
) -> dict[str, Any]:
    repo_root = repo_root.resolve()
    tmp = repo_root / ".tmp"
    raw_dir = (raw_dir or INVENTORY.DEFAULT_RAW_DIR).resolve()
    ocr_dir = (ocr_dir or INVENTORY.DEFAULT_OCR_DIR).resolve()
    worker_drills_path = tmp / "job_posting_worker_drills.json"
    inventory_path = tmp / "job_posting_real_regression_inventory.json"
    regression_manifest_path = tmp / "job_posting_real_regression_set" / "manifest.json"
    regression_raw_dir = tmp / "job_posting_real_regression_set" / "raw"
    regression_ocr_dir = tmp / "job_posting_real_regression_set" / "ocr"
    stabilization_dir = tmp / "job_posting_production_stabilization"
    stabilization_summary_path = stabilization_dir / "stabilization_summary.json"
    stabilization_report_path = stabilization_dir / "document_pipeline_stabilization.md"
    release_readiness_path = tmp / f"job_posting_release_readiness_{target_count}.json"
    docker_smoke_path = tmp / "job_posting_worker_docker_smoke.json"
    ocr_smoke_path = tmp / "job_posting_worker_ocr_runtime.json"
    db_migration_path = tmp / "application_case_pipeline_db_migration.json"
    production_path = tmp / "job_posting_production_readiness.json"

    steps: list[dict[str, Any]] = []

    steps.append(collect_step(
        "worker operational drills",
        worker_drills_path,
        lambda: DRILLS.run_drills(worker_drills_path),
    ))

    def inventory_step() -> dict[str, Any]:
        payload = INVENTORY.audit_inventory(raw_dir, ocr_dir, target_count)
        write_json(inventory_path, payload)
        return {**payload, "ok": bool(payload.get("targetSatisfied"))}

    steps.append(collect_step("real regression inventory", inventory_path, inventory_step))

    def regression_set_step() -> dict[str, Any]:
        payload = REGRESSION_SET.prepare_regression_set(
            repo_root=repo_root,
            raw_dir=raw_dir,
            ocr_dir=ocr_dir,
            raw_output_dir=regression_raw_dir,
            ocr_output_dir=regression_ocr_dir,
            target_count=target_count,
            clean=True,
        )
        write_json(regression_manifest_path, payload)
        return payload

    regression_step = collect_step("real regression set", regression_manifest_path, regression_set_step)
    steps.append(regression_step)

    def stabilization_step() -> dict[str, Any]:
        payload = STABILIZATION.run_check(
            input_dir=regression_raw_dir,
            existing_ocr_dir=regression_ocr_dir,
            output_dir=stabilization_dir,
            report_path=stabilization_report_path,
            thresholds=STABILIZATION.StabilizationThresholds(min_file_count=target_count),
        )
        return payload

    steps.append(collect_step("real stabilization gate", stabilization_summary_path, stabilization_step, ok_key="passed"))

    def release_readiness_step() -> dict[str, Any]:
        payload = READINESS.run_check(
            repo_root,
            include_artifacts=True,
            min_files=target_count,
            stabilization_summary=stabilization_summary_path,
            worker_drills=worker_drills_path,
        )
        write_json(release_readiness_path, payload)
        return payload

    steps.append(collect_step("release readiness target-file gate", release_readiness_path, release_readiness_step))

    if include_runtime_smoke:
        steps.append(collect_step(
            "worker Docker runtime smoke",
            docker_smoke_path,
            lambda: DOCKER_SMOKE.run_smoke(
                repo_root,
                image="careertuner-job-posting-worker:smoke",
                container_name="careertuner-job-posting-worker-smoke",
                host_port=8091,
                install_ocr=False,
                skip_build=False,
                attempts=30,
                delay_seconds=1.0,
            ),
        ))
        write_json(docker_smoke_path, steps[-1]["payload"])
        steps.append(collect_step(
            "worker OCR runtime smoke",
            ocr_smoke_path,
            lambda: OCR_SMOKE.run_smoke(repo_root, lang="en"),
        ))
        write_json(ocr_smoke_path, steps[-1]["payload"])

    if include_db_check:
        db_args = argparse.Namespace(
            host=db_host,
            port=db_port,
            database=db_database,
            user=db_user,
            password_env=db_password_env,
            mysql_bin=mysql_bin,
        )

        def db_step() -> dict[str, Any]:
            try:
                rows = DB_SCHEMA.parse_rows(DB_SCHEMA.run_mysql(db_args))
                payload = DB_SCHEMA.evaluate_rows(rows, db_args.database)
            except Exception as exc:  # noqa: BLE001 - DB evidence should report environment gaps.
                payload = {
                    "ok": False,
                    "database": db_args.database,
                    "error": str(exc),
                    "checks": [
                        DB_SCHEMA.SchemaCheck(name, 1, 0).to_dict()
                        for name in DB_SCHEMA.expected_checks()
                    ],
                }
            payload = {
                **payload,
                "host": db_args.host,
                "port": db_args.port,
                "user": db_args.user,
                "mysqlBin": db_args.mysql_bin,
                "passwordEnv": db_args.password_env,
            }
            write_json(db_migration_path, payload)
            return payload

        steps.append(collect_step("staging DB migration evidence", db_migration_path, db_step))

    def production_step() -> dict[str, Any]:
        payload = PRODUCTION.run_audit(
            repo_root,
            target_count=target_count,
            release_readiness=release_readiness_path,
            inventory=inventory_path,
            stabilization_summary=stabilization_summary_path,
            regression_set=regression_manifest_path,
            worker_drills=worker_drills_path,
            docker_smoke=docker_smoke_path,
            ocr_runtime=ocr_smoke_path,
            db_migration=db_migration_path,
        )
        write_json(production_path, payload)
        return {**payload, "ok": bool(payload.get("ready"))}

    production_step_result = collect_step("production readiness audit", production_path, production_step)
    steps.append(production_step_result)
    blocking_items = production_step_result["payload"].get("blockingItems", [])
    summary = {
        "ok": bool(production_step_result["payload"].get("ready")),
        "repoRoot": str(repo_root),
        "targetCount": target_count,
        "steps": steps,
        "blockingItems": blocking_items,
        "productionReadiness": str(production_path),
    }
    return summary


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=DEFAULT_REPO_ROOT)
    parser.add_argument("--target-count", type=int, default=INVENTORY.DEFAULT_PRODUCTION_TARGET_COUNT)
    parser.add_argument("--raw-dir", type=Path, default=None)
    parser.add_argument("--ocr-dir", type=Path, default=None)
    parser.add_argument("--skip-runtime-smoke", action="store_true")
    parser.add_argument("--skip-db-check", action="store_true")
    parser.add_argument("--db-host", default=os.environ.get("DB_HOST", "127.0.0.1"))
    parser.add_argument("--db-port", type=int, default=int(os.environ.get("DB_PORT", "3306")))
    parser.add_argument("--db-name", default=os.environ.get("DB_NAME", "team1_db"))
    parser.add_argument("--db-user", default=os.environ.get("DB_USERNAME", "root"))
    parser.add_argument("--db-password-env", default="DB_PASSWORD")
    parser.add_argument("--mysql-bin", default="mysql")
    parser.add_argument("--output", type=Path, default=DEFAULT_REPO_ROOT / ".tmp" / "job_posting_release_evidence.json")
    parser.add_argument("--report", type=Path, default=DEFAULT_REPO_ROOT / ".tmp" / "job_posting_release_evidence.md")
    parser.add_argument("--no-report", action="store_true")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    summary = run_release_evidence(
        repo_root=args.repo_root,
        target_count=args.target_count,
        raw_dir=args.raw_dir,
        ocr_dir=args.ocr_dir,
        include_runtime_smoke=not args.skip_runtime_smoke,
        include_db_check=not args.skip_db_check,
        db_host=args.db_host,
        db_port=args.db_port,
        db_database=args.db_name,
        db_user=args.db_user,
        db_password_env=args.db_password_env,
        mysql_bin=args.mysql_bin,
    )
    write_json(args.output, summary)
    if not args.no_report:
        REPORT.write_report(summary, args.report)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    raise SystemExit(0 if summary["ok"] else 1)


if __name__ == "__main__":
    main()
