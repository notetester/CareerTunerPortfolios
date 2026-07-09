"""A-only baseline 오프라인 결정론 평가기(R3-like observer) + human/judge review packet.

역할: 실측 run(results/*.result.json)의 출력 텍스트에서 **unsupported possession claim 후보**를
결정론으로 탐지하고, 카테고리·판단밴드별 집계와 검토 패킷을 만든다.
- 후보 판정: 문장에 보유 표현(OWNERSHIP)이 있고 결핍·부정 표현(LACK)이 없으며 forbiddenOwned 스킬을 언급.
- 라틴 스킬명은 단어 경계 매칭(Java 가 JavaScript 에 오탐되지 않게), 한글 명칭은 포함 매칭.
- 이 평가기는 **후보**만 표시한다 — true unsupported 확정은 judge/human rubric v2 절차(reports/75~76)로만 한다.
- 개인정보·원문 prompt 미포함. 패킷 문장 발췌는 200자 절단.

실행: python scripts/evaluate_a_only_baseline_outputs.py --fixture <jsonl> --run <run_dir> --out <eval_dir>
"""
from __future__ import annotations

import argparse
import json
import re
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from career_json_repair import is_repairable  # noqa: E402  (backend truncation 수리 미러)

OWNERSHIP = ["보유", "갖춤", "갖추고", "갖추어", "강점", "경험 있", "경험을 보유",
             "활용 가능", "숙련", "기반이 있", "능숙", "능통", "익숙"]
LACK = ["부족", "없", "미보유", "부재", "않", "못", "결여", "갖추지", "보유하지", "미흡", "전무", "필요",
        "아니라면", "아니라", "아니며", "아닌", "아닙니"]  # negated-ownership 오탐 보정(reports/80 EA-A-028)
CJK_EXTRA_RE = re.compile(r"[一-鿿]")  # 한자(중국어 누출 신호)
LATIN_RE = re.compile(r"^[A-Za-z0-9 .+#/-]+$")


def string_leaves(node: Any) -> list[str]:
    out: list[str] = []
    if isinstance(node, str):
        if node.strip():
            out.append(node)
    elif isinstance(node, dict):
        for value in node.values():
            out.extend(string_leaves(value))
    elif isinstance(node, list):
        for item in node:
            out.extend(string_leaves(item))
    return out


def mentions(sentence_lower: str, skill: str) -> bool:
    if LATIN_RE.match(skill):
        return re.search(r"(?<![A-Za-z0-9])" + re.escape(skill.lower()) + r"(?![A-Za-z0-9])", sentence_lower) is not None
    return skill.lower() in sentence_lower


def audit_output(parsed: Any, forbidden: list[str]) -> list[dict[str, Any]]:
    hits: list[dict[str, Any]] = []
    for text in string_leaves(parsed):
        for sentence in re.split(r"[.!?。\n]", text):
            if not sentence.strip():
                continue
            if not any(p in sentence for p in OWNERSHIP) or any(p in sentence for p in LACK):
                continue
            lower = sentence.lower()
            for skill in forbidden:
                if mentions(lower, skill):
                    hits.append({"skill": skill, "sentence": sentence.strip()[:200]})
    return hits


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixture", required=True, type=Path)
    parser.add_argument("--run", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args(argv)

    fixture = {row["caseId"]: row for row in
               (json.loads(line) for line in args.fixture.read_text(encoding="utf-8").splitlines() if line.strip())}
    per_case: list[dict[str, Any]] = []
    packet: list[dict[str, Any]] = []
    by_category: dict[str, Counter] = {}

    for result_path in sorted((args.run / "results").glob("*.result.json")):
        result = json.loads(result_path.read_text(encoding="utf-8"))
        case_id = result["caseId"]
        row = fixture[case_id]
        raw_text = ""
        if result.get("rawOutputPath"):
            raw_text = (args.run / result["rawOutputPath"]).read_text(encoding="utf-8")
        parsed = result.get("parsedOutput")
        json_ok = parsed is not None
        hits = audit_output(parsed, row["expected"]["forbiddenOwned"]) if json_ok else []
        empty = not raw_text.strip()
        cjk = bool(CJK_EXTRA_RE.search(raw_text))
        label = ("EMPTY_OUTPUT" if empty else "PARSE_FAIL" if not json_ok
                 else "CANDIDATE_UNSUPPORTED_POSSESSION" if hits else "PASS_OFFLINE")
        # 병렬 지표: raw 파싱 실패지만 production 경로(truncation 수리, #229)로 살아나는가.
        # 엄격 raw PARSE_FAIL 지표는 유지(모델 품질 측정용)하고, 운영 실효만 별도 관측.
        repairable = label == "PARSE_FAIL" and not empty and is_repairable(raw_text)
        per_case.append({"caseId": case_id, "category": row["category"], "label": label,
                         "candidateCount": len(hits), "jsonParse": json_ok, "cjkLeak": cjk,
                         "repairableParseFail": repairable,
                         "latencyMs": result.get("latencyMs")})
        counter = by_category.setdefault(row["category"], Counter())
        counter[label] += 1
        counter["candidates"] += len(hits)
        if label != "PASS_OFFLINE":
            packet.append({"caseId": case_id, "category": row["category"], "label": label,
                           "forbiddenOwned": row["expected"]["forbiddenOwned"], "hits": hits,
                           "rubric": "v2 (reports/75) — TRUE_UNSUPPORTED_OWNERSHIP 만 위험으로 확정, "
                                     "MISSING_SKILL_STATEMENT/JOB_REQUIREMENT_ONLY/RISK_WARNING_ONLY/"
                                     "CATALOG_FACT_ONLY/UNCLEAR 는 분리"})

    labels = Counter(c["label"] for c in per_case)
    parse_fail_total = labels.get("PARSE_FAIL", 0)
    repairable_total = sum(1 for c in per_case if c["repairableParseFail"])
    summary = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "run": str(args.run), "cases": len(per_case), "variant": "A_lora_only",
        "labels": dict(labels),
        # 엄격 raw PARSE_FAIL 중 production truncation 수리(#229)로 살아나는 수(병렬 관측 지표).
        "parseFailTotal": parse_fail_total,
        "repairableParseFailTotal": repairable_total,
        "productionEffectiveParseFail": parse_fail_total - repairable_total,
        "candidateUnsupportedTotal": sum(c["candidateCount"] for c in per_case),
        "byCategory": {k: dict(v) for k, v in sorted(by_category.items())},
        "note": "후보(candidate)는 결정론 관찰값이며 true unsupported 확정은 judge/human rubric v2 절차로만 한다.",
        "perCase": per_case,
    }
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "a_only_baseline_eval.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    with (args.out / "a_only_baseline_review_packet.jsonl").open("w", encoding="utf-8", newline="\n") as f:
        for item in packet:
            f.write(json.dumps(item, ensure_ascii=False) + "\n")

    print(f"[evaluate_a_only_baseline_outputs] cases={len(per_case)} labels={dict(labels)} "
          f"parseFail={parse_fail_total} repairable={repairable_total} "
          f"prodEffectiveParseFail={parse_fail_total - repairable_total} "
          f"candidates={summary['candidateUnsupportedTotal']} packet={len(packet)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
