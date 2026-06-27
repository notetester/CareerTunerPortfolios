"""R2b hard-case **preflight 게이트** — 실모델 A/B 실행(4090) 전에 fixture·불변식을 검증한다.

test_rag_hard_cases.py(unittest)와 같은 불변식을 **CLI 종료코드 게이트**로 제공(0=통과, 1=위반)해
job 트리거 전 단계(workflow/스크립트)에서 한 줄로 차단할 수 있게 한다. 추가로 unittest 에 없는
**retrievedContext text 값-수준 점수/판단 누수 스캔**(scan_text_for_score_leak)을 포함한다.

검사:
  1) fixture synthetic — 이메일/전화/주민번호 패턴 없음
  2) A 에 retrievedContext 없음 · B 는 retrievedContext 유무만 다름
  3) retrievedContext 항목 키가 정확히 sourceType/sourceId/text · 금지키 없음 · text 값 점수/판단 누수 없음
  4) negative_control 존재 + B 의 ctx 빈 배열
  5) mssql_vs_sql: allowed 에 SQL 있고 특정 제품(MSSQL) 없음
  6) fitScore/applyDecision 이 양 variant 불변
  7) 필수 hardType 전부 존재

실행: python rag_poc/scripts/validate_rag_hard_cases.py
"""
import copy
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from build_rag_hard_cases import load_hard_cases, build_hard_pairs  # noqa: E402
from build_retrieved_context import CONTEXT_KEYS, FORBIDDEN_KEYS, scan_text_for_score_leak  # noqa: E402

EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
PHONE_RE = re.compile(r"01[016789][- ]?\d{3,4}[- ]?\d{4}")
RRN_RE = re.compile(r"\b\d{6}[- ]?\d{7}\b")
REQUIRED_HARD_TYPES = {
    "mssql_vs_sql", "fake_product_name", "cert_catalog_grounding",
    "company_research_context", "similar_stack_confusion",
    "data_role_confusion", "negative_control", "score_decision_invariant",
}


def validate():
    cases = load_hard_cases()
    pairs = build_hard_pairs()
    errs = []

    # 1) PII
    blob = json.dumps(cases, ensure_ascii=False)
    for name, rx in (("이메일", EMAIL_RE), ("전화번호", PHONE_RE), ("주민번호", RRN_RE)):
        m = rx.search(blob)
        if m:
            errs.append(f"[PII] {name} 패턴 발견: {m.group(0)!r}")

    allow_keys = set(CONTEXT_KEYS)
    for p in pairs:
        cid = p.get("caseId")
        a = copy.deepcopy(p["variants"]["lora_only"]["input"])
        b = copy.deepcopy(p["variants"]["lora_with_retrieved_context"]["input"])
        # 2) A 에 ctx 없음 · 차이는 ctx 뿐
        if "retrievedContext" in a:
            errs.append(f"[{cid}] A(lora_only)에 retrievedContext 존재")
        b_noctx = dict(b)
        b_noctx.pop("retrievedContext", None)
        if a != b_noctx:
            errs.append(f"[{cid}] A/B 가 retrievedContext 외에도 다름")
        # 3) ctx 항목 키/금지키/값 누수
        for item in b.get("retrievedContext") or []:
            keys = set(item.keys())
            if keys != allow_keys:
                errs.append(f"[{cid}] retrievedContext 키가 {sorted(keys)} (허용 {sorted(allow_keys)})")
            bad = FORBIDDEN_KEYS & keys
            if bad:
                errs.append(f"[{cid}] retrievedContext 금지 키 {bad}")
            leak = scan_text_for_score_leak(item.get("text", ""))
            if leak:
                errs.append(f"[{cid}] retrievedContext text 값 점수/판단 누수: {leak!r}")
        # 6) score/decision 불변
        if a.get("fitScore") != b.get("fitScore"):
            errs.append(f"[{cid}] fitScore 가 A/B 에서 다름")
        if a.get("applyDecision") != b.get("applyDecision"):
            errs.append(f"[{cid}] applyDecision 이 A/B 에서 다름")

    # 4) negative_control
    negs = [c for c in cases if c.get("hardType") == "negative_control"]
    if not negs:
        errs.append("[negative_control] 케이스 없음")
    for c in negs:
        if c.get("retrievedContext") != []:
            errs.append(f"[{c.get('caseId')}] negative_control 인데 ctx 가 비어있지 않음")

    # 5) mssql_vs_sql
    mssql = [c for c in cases if c.get("hardType") == "mssql_vs_sql"]
    if not mssql:
        errs.append("[mssql_vs_sql] 케이스 없음")
    for c in mssql:
        allowed = (c.get("expected") or {}).get("allowedSkills") or []
        if "SQL" not in allowed:
            errs.append(f"[{c.get('caseId')}] allowed 에 일반 SQL 없음(hard-case 불성립)")
        if "MSSQL" in allowed:
            errs.append(f"[{c.get('caseId')}] allowed 에 MSSQL 존재(hard-case 불성립)")

    # 7) 필수 hardType
    missing = REQUIRED_HARD_TYPES - {c.get("hardType") for c in cases}
    if missing:
        errs.append(f"[hardType] 누락: {sorted(missing)}")

    return cases, pairs, errs


def main():
    cases, pairs, errs = validate()
    print(f"[validate_rag_hard_cases] cases={len(cases)} pairs={len(pairs)}")
    if errs:
        print(f"  ✗ 위반 {len(errs)}건:")
        for e in errs:
            print("   -", e)
        return 1
    print("  ✓ 전체 불변식 통과(PII 없음·scope·금지키/값누수 없음·negative_control·MSSQL·score 불변·hardType 8종).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
