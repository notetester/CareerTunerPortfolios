"""Validate the RAG hard-case benchmark fixture.

This is intentionally offline-only: no Ollama, API key, or external service is
required. It checks the fixture schema used by reports/67 and reports/69.
"""

from __future__ import annotations

import json
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Any

REQUIRED_BUCKETS = ("userEvidence", "jobRequirements", "catalogFacts", "companyContext")
EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
PHONE_RE = re.compile(r"01[016789][- ]?\d{3,4}[- ]?\d{4}")
RRN_RE = re.compile(r"\b\d{6}[- ]?\d{7}\b")


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open(encoding="utf-8") as handle:
        for line_no, raw_line in enumerate(handle, 1):
            line = raw_line.strip()
            if not line or line.startswith("#"):
                continue
            try:
                value = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{line_no} JSON parse error: {exc}") from exc
            if not isinstance(value, dict):
                raise ValueError(f"{path}:{line_no} row must be an object")
            value["_lineNo"] = line_no
            rows.append(value)
    return rows


def _list_at(row: dict[str, Any], dotted_path: str) -> list[Any] | None:
    current: Any = row
    for part in dotted_path.split("."):
        if not isinstance(current, dict) or part not in current:
            return None
        current = current[part]
    return current if isinstance(current, list) else None


def validate_rows(rows: list[dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    seen: set[str] = set()
    categories: Counter[str] = Counter()

    blob = json.dumps(rows, ensure_ascii=False)
    for label, pattern in (("email", EMAIL_RE), ("phone", PHONE_RE), ("resident-id", RRN_RE)):
        match = pattern.search(blob)
        if match:
            errors.append(f"[PII] {label} pattern found: {match.group(0)!r}")

    for index, row in enumerate(rows, 1):
        prefix = f"[row {index}]"
        case_id = row.get("caseId")
        if not isinstance(case_id, str) or not case_id.strip():
            errors.append(f"{prefix} missing caseId")
            case_id = f"<missing:{index}>"
        elif case_id in seen:
            errors.append(f"[{case_id}] duplicate caseId")
        seen.add(str(case_id))

        category = row.get("category")
        if not isinstance(category, str) or not category.strip():
            errors.append(f"[{case_id}] missing category")
        else:
            categories[category] += 1

        for field in ("intent", "profile", "job", "expected", "evidenceBuckets"):
            if field not in row:
                errors.append(f"[{case_id}] missing {field}")

        profile_skills = _list_at(row, "profile.skills")
        job_required = _list_at(row, "job.requiredSkills")
        if not profile_skills:
            errors.append(f"[{case_id}] profile.skills must exist and be non-empty")
        if not job_required:
            errors.append(f"[{case_id}] job.requiredSkills must exist and be non-empty")

        expected = row.get("expected") if isinstance(row.get("expected"), dict) else {}
        must_not = expected.get("mustNotClaimOwned")
        if not isinstance(must_not, list) or not must_not:
            errors.append(f"[{case_id}] expected.mustNotClaimOwned must be non-empty")
        if expected.get("expectedGateStatusForUnsafeClaim") != "REVIEW_REQUIRED":
            errors.append(f"[{case_id}] expectedGateStatusForUnsafeClaim must be REVIEW_REQUIRED")

        buckets = row.get("evidenceBuckets")
        if not isinstance(buckets, dict):
            errors.append(f"[{case_id}] evidenceBuckets must be an object")
            continue
        for bucket in REQUIRED_BUCKETS:
            if bucket not in buckets:
                errors.append(f"[{case_id}] evidenceBuckets.{bucket} missing")
                continue
            items = buckets[bucket]
            if not isinstance(items, list):
                errors.append(f"[{case_id}] evidenceBuckets.{bucket} must be a list")
                continue
            for item_index, item in enumerate(items, 1):
                if not isinstance(item, dict):
                    errors.append(f"[{case_id}] {bucket}[{item_index}] must be an object")
                    continue
                if not item.get("sourceId"):
                    errors.append(f"[{case_id}] {bucket}[{item_index}] missing sourceId")
                if not item.get("sourceType"):
                    errors.append(f"[{case_id}] {bucket}[{item_index}] missing sourceType")
                if not item.get("text"):
                    errors.append(f"[{case_id}] {bucket}[{item_index}] missing text")

    if len(rows) < 12:
        errors.append(f"[fixture] expected at least 12 rows, got {len(rows)}")
    if len(categories) < 12:
        errors.append(f"[fixture] expected at least 12 categories, got {len(categories)}")

    return errors


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print("usage: python scripts/validate_rag_hardcase_fixture.py <fixture.jsonl>")
        return 2

    path = Path(argv[1])
    rows = load_jsonl(path)
    errors = validate_rows(rows)
    categories = Counter(str(row.get("category")) for row in rows)

    print(f"[validate_rag_hardcase_fixture] path={path}")
    print(f"  cases={len(rows)} categories={len(categories)}")
    if errors:
        print(f"  FAIL errors={len(errors)}")
        for error in errors:
            print(f"   - {error}")
        return 1
    print("  OK fixture schema valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
