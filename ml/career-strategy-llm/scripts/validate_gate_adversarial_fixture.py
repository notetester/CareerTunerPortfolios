"""Adversarial gate fixture 검증 — 기존 validate_a_only_baseline_fixture 의 per-row 검사(PII/스키마/
caseId 유일/forbiddenOwned 재계산/overlap)를 그대로 재사용하되, A-only 전용 CATEGORIES 커버리지와
total=60 단정만 제외한다. (gate 픽스처는 카테고리 분포가 다름.)
0=통과, 1=위반.
"""
from __future__ import annotations
import json, re, sys
from pathlib import Path

DEFAULT_FIXTURE = (Path(__file__).resolve().parent.parent
                   / "data" / "evidence_attribution_baseline" / "gate_adversarial_v1.jsonl")
FIXTURE = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_FIXTURE
EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
PHONE_RE = re.compile(r"01[016789][- ]?\d{3,4}[- ]?\d{4}")
RRN_RE = re.compile(r"\b\d{6}[- ]?\d{7}\b")


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
    for row in rows:
        cid = row.get("caseId", "?")
        if cid in seen:
            errors.append(f"[{cid}] caseId 중복")
        seen.add(cid)
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
            errors.append(f"[{cid}] forbiddenOwned 재계산 불일치: got={expected.get('forbiddenOwned')} want={recomputed}")
        overlap = {s.lower() for s in expected.get("allowedOwned", [])} & {s.lower() for s in expected.get("forbiddenOwned", [])}
        if overlap:
            errors.append(f"[{cid}] allowed/forbidden 중첩: {sorted(overlap)}")
        if expected.get("expectedGateStatusForUnsafeClaim") != "REVIEW_REQUIRED":
            errors.append(f"[{cid}] expectedGateStatusForUnsafeClaim != REVIEW_REQUIRED")
    return errors


def main() -> int:
    rows = load_rows(FIXTURE)
    errors = validate_rows(rows)
    from collections import Counter
    bands = Counter(r.get("gateBand") for r in rows)
    print(f"[validate_gate_adversarial] cases={len(rows)} bands={dict(bands)}")
    if errors:
        for e in errors:
            print("  X", e)
        return 1
    print("  OK PII 없음 · 스키마 · caseId 유일 · forbiddenOwned 재계산 일치 · allowed/forbidden 비중첩")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
