"""A-only baseline fixture preflight 게이트 (0=통과, 1=위반).

검사: PII 없음(이메일/전화/주민번호), 필수 필드/타입, caseId 유일, 카테고리 커버리지(7종×각 8+, 총 60),
forbiddenOwned 재계산 일치(요구·우대 중 미보유), allowed/forbidden 비중첩.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

FIXTURE = Path(__file__).resolve().parent.parent / "data" / "evidence_attribution_baseline" / "a_only_baseline_v1.jsonl"
EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
PHONE_RE = re.compile(r"01[016789][- ]?\d{3,4}[- ]?\d{4}")
RRN_RE = re.compile(r"\b\d{6}[- ]?\d{7}\b")
CATEGORIES = {
    "confusion_pair": 12, "requirement_as_owned": 8, "cert_requirement_as_owned": 8,
    "company_stack_as_experience": 8, "decision_apply": 8, "decision_complement": 8, "decision_hold": 8,
}


def load_rows(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def validate_rows(rows: list[dict]) -> list[str]:
    errors: list[str] = []
    blob = json.dumps(rows, ensure_ascii=False)
    for name, rx in (("이메일", EMAIL_RE), ("전화번호", PHONE_RE), ("주민번호", RRN_RE)):
        m = rx.search(blob)
        if m:
            errors.append(f"[PII] {name} 패턴 발견: {m.group(0)!r}")

    seen: set[str] = set()
    counts: dict[str, int] = {}
    for row in rows:
        cid = row.get("caseId", "?")
        if cid in seen:
            errors.append(f"[{cid}] caseId 중복")
        seen.add(cid)
        cat = row.get("category")
        counts[cat] = counts.get(cat, 0) + 1
        for key in ("caseId", "category", "intent", "profile", "job", "expected"):
            if key not in row:
                errors.append(f"[{cid}] 필수 키 누락: {key}")
        profile, job, expected = row.get("profile", {}), row.get("job", {}), row.get("expected", {})
        for key in ("skills", "certificates", "desiredJob"):
            if key not in profile:
                errors.append(f"[{cid}] profile.{key} 누락")
        for key in ("companyName", "jobTitle", "requiredSkills", "preferredSkills", "duties"):
            if key not in job:
                errors.append(f"[{cid}] job.{key} 누락")
        owned = {s.lower() for s in profile.get("skills", [])} | {c.lower() for c in profile.get("certificates", [])}
        recomputed = [s for s in job.get("requiredSkills", []) + job.get("preferredSkills", [])
                      if s.lower() not in owned]
        if recomputed != expected.get("forbiddenOwned"):
            errors.append(f"[{cid}] forbiddenOwned 재계산 불일치")
        overlap = {s.lower() for s in expected.get("allowedOwned", [])} & {s.lower() for s in expected.get("forbiddenOwned", [])}
        if overlap:
            errors.append(f"[{cid}] allowed/forbidden 중첩: {sorted(overlap)}")
        if expected.get("expectedGateStatusForUnsafeClaim") != "REVIEW_REQUIRED":
            errors.append(f"[{cid}] expectedGateStatusForUnsafeClaim != REVIEW_REQUIRED")

    for cat, want in CATEGORIES.items():
        if counts.get(cat, 0) != want:
            errors.append(f"[category] {cat}: {counts.get(cat, 0)} != {want}")
    if len(rows) != sum(CATEGORIES.values()):
        errors.append(f"[total] {len(rows)} != {sum(CATEGORIES.values())}")
    return errors


def main() -> int:
    rows = load_rows(FIXTURE)
    errors = validate_rows(rows)
    print(f"[validate_a_only_baseline_fixture] cases={len(rows)}")
    if errors:
        for e in errors:
            print("  ✗", e)
        return 1
    print("  ✓ PII 없음 · 스키마 · caseId 유일 · 카테고리 커버리지 · forbiddenOwned 일치")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
