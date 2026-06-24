"""Verify staging MySQL schema for the job-posting pipeline and emit JSON evidence."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Any


QUALITY_COLUMNS = [
    "extraction_strategy",
    "quality_score",
    "quality_status",
    "quality_report_json",
    "model_versions_json",
    "fallback_eligible",
    "fallback_reason",
    "reviewed_at",
]
RUNTIME_SETTING_COLUMNS = ["setting_key", "value_json", "updated_by"]


@dataclass(frozen=True)
class SchemaCheck:
    name: str
    expected: int
    actual: int

    @property
    def passed(self) -> bool:
        return self.actual >= self.expected

    def to_dict(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "expected": self.expected,
            "actual": self.actual,
            "passed": self.passed,
        }


def expected_checks() -> list[str]:
    checks = [f"application_case_extraction.{column}" for column in QUALITY_COLUMNS]
    checks.append("application_case_extraction.chk_case_extraction_quality_status")
    checks.append("ai_runtime_setting.table")
    checks.extend(f"ai_runtime_setting.{column}" for column in RUNTIME_SETTING_COLUMNS)
    return checks


def verification_query() -> str:
    parts: list[str] = []
    for column in QUALITY_COLUMNS:
        parts.append(
            "SELECT 'application_case_extraction.{column}' AS check_name, COUNT(*) AS actual "
            "FROM information_schema.columns "
            "WHERE table_schema = DATABASE() "
            "AND table_name = 'application_case_extraction' "
            "AND column_name = '{column}'".format(column=column)
        )
    parts.append(
        "SELECT 'application_case_extraction.chk_case_extraction_quality_status' AS check_name, COUNT(*) AS actual "
        "FROM information_schema.table_constraints "
        "WHERE table_schema = DATABASE() "
        "AND table_name = 'application_case_extraction' "
        "AND constraint_name = 'chk_case_extraction_quality_status'"
    )
    parts.append(
        "SELECT 'ai_runtime_setting.table' AS check_name, COUNT(*) AS actual "
        "FROM information_schema.tables "
        "WHERE table_schema = DATABASE() "
        "AND table_name = 'ai_runtime_setting'"
    )
    for column in RUNTIME_SETTING_COLUMNS:
        parts.append(
            "SELECT 'ai_runtime_setting.{column}' AS check_name, COUNT(*) AS actual "
            "FROM information_schema.columns "
            "WHERE table_schema = DATABASE() "
            "AND table_name = 'ai_runtime_setting' "
            "AND column_name = '{column}'".format(column=column)
        )
    return "\nUNION ALL\n".join(parts) + ";"


def parse_rows(stdout: str) -> dict[str, int]:
    rows: dict[str, int] = {}
    for line in stdout.splitlines():
        if not line.strip():
            continue
        parts = line.rstrip("\n").split("\t")
        if len(parts) != 2:
            continue
        try:
            rows[parts[0]] = int(parts[1])
        except ValueError:
            rows[parts[0]] = 0
    return rows


def evaluate_rows(rows: dict[str, int], database: str) -> dict[str, Any]:
    checks = [SchemaCheck(name, 1, int(rows.get(name, 0))) for name in expected_checks()]
    return {
        "ok": all(check.passed for check in checks),
        "database": database,
        "checks": [check.to_dict() for check in checks],
    }


def run_mysql(args: argparse.Namespace) -> str:
    mysql = shutil.which(args.mysql_bin)
    if mysql is None:
        raise RuntimeError(f"mysql CLI not found: {args.mysql_bin}")
    env = os.environ.copy()
    password = os.environ.get(args.password_env)
    if password:
        env["MYSQL_PWD"] = password
    command = [
        mysql,
        "--batch",
        "--raw",
        "--skip-column-names",
        "--host",
        args.host,
        "--port",
        str(args.port),
        "--user",
        args.user,
        args.database,
        "--execute",
        verification_query(),
    ]
    completed = subprocess.run(command, check=False, capture_output=True, text=True, env=env)
    if completed.returncode != 0:
        stderr = completed.stderr.strip()
        stdout = completed.stdout.strip()
        details = stderr or stdout or "mysql command failed without output"
        raise RuntimeError(f"mysql command failed with exit code {completed.returncode}: {details}")
    return completed.stdout


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default=os.environ.get("DB_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("DB_PORT", "3306")))
    parser.add_argument("--database", default=os.environ.get("DB_NAME", "team1_db"))
    parser.add_argument("--user", default=os.environ.get("DB_USERNAME", "root"))
    parser.add_argument("--password-env", default="DB_PASSWORD")
    parser.add_argument("--mysql-bin", default="mysql")
    parser.add_argument("--output", type=Path, default=None)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    try:
        rows = parse_rows(run_mysql(args))
        summary = evaluate_rows(rows, args.database)
    except Exception as exc:
        summary = {
            "ok": False,
            "database": args.database,
            "error": str(exc),
            "checks": [SchemaCheck(name, 1, 0).to_dict() for name in expected_checks()],
        }
    encoded = json.dumps(summary, ensure_ascii=False, indent=2)
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded + "\n", encoding="utf-8")
    print(encoded)
    raise SystemExit(0 if summary["ok"] else 1)


if __name__ == "__main__":
    main()
