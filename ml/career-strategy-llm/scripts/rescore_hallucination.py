"""HALLUCINATED_SKILL 병렬 지표 재채점(오프라인) — 모델 재실행 불필요.

기존 raw 평가 결과(JSON)의 bad_skills 를 **확장된 stage1 정규화기**로 다시 분류하고,
잔여(judge 대상)는 AI judge consensus(all10)로 semantic 분해해 병렬 지표를 산출한다.
raw 지표는 불변(병렬 추가). 모델별(LoRA/base)로 나눠 보고.

지표(occurrence = 원시 flag 단위):
  raw_hallucination_count          원시 bad_skills 카운트(불변)
  normalized_hallucination_count   확장 정규화 후 잔여(judge 대상)
  harness_false_positive_count     정규화 해소 + consensus harness_false_positive
  acceptable_gray_count            consensus acceptable_gray
  semantic_hallucination_count     consensus valid_error(진짜 범위밖 날조)

Before(현행 스냅샷)는 normalization_stats.json 에서, After 는 현재 정규화기로 새로 계산.
둘 다 동일 consensus(all10)로 semantic 분해 → 정규화 단계만의 변화를 분리해 보여준다.

사용:
  python scripts/rescore_hallucination.py \
    --result "careertuner-c-career-strategy-3b=<LoRA.json>" \
    --result "qwen2.5:3b-instruct=<base.json>" \
    --cases eval/golden_fit_cases.jsonl \
    --consensus <CareerTunerAI>/results/.../consensus_all10.jsonl \
    --before-stats <CareerTunerAI>/results/.../normalization_stats.json \
    --out <out.json>
"""
import argparse
import json
import os
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)
from skill_normalizer import classify_flagged_skill, JUDGE_STATUSES, RESOLVED_FP_STATUSES  # noqa: E402
from judge_packet_builder import load_cases, load_result  # noqa: E402

DECISIONS = ["valid_error", "acceptable_gray", "harness_false_positive", "needs_policy"]


def _cand_id(case_id, flagged):
    return f"{case_id}::{''.join(str(flagged).split()).lower()}"


def load_consensus(path):
    """candidateId → finalDecision."""
    out = {}
    if not path or not os.path.exists(path):
        return out
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if line:
            r = json.loads(line)
            out[r["candidateId"]] = r.get("finalDecision")
    return out


def rescore_model(rows, cases, consensus):
    """한 모델의 bad_skills 를 확장 정규화기로 재분류 + consensus semantic 분해."""
    raw = 0
    resolved_fp = 0            # 정규화로 오탐 해소(occurrence)
    residual = 0               # 정규화 후 잔여(judge 대상, occurrence)
    sem = {d: 0 for d in DECISIONS}   # 잔여의 consensus 분해(occurrence)
    residual_no_consensus = []
    for row in rows:
        cid = row.get("id")
        allowed = ((cases.get(cid) or {}).get("expected") or {}).get("allowedSkills") or []
        for flagged in (row.get("detail") or {}).get("bad_skills") or []:
            raw += 1
            st = classify_flagged_skill(flagged, allowed)["status"]
            if st in RESOLVED_FP_STATUSES:
                resolved_fp += 1
                continue
            if st in JUDGE_STATUSES:
                residual += 1
                dec = consensus.get(_cand_id(cid, flagged))
                if dec in sem:
                    sem[dec] += 1
                else:
                    residual_no_consensus.append(_cand_id(cid, flagged))
    metrics = {
        "raw_hallucination_count": raw,
        "normalized_hallucination_count": residual,
        "harness_false_positive_count": resolved_fp + sem["harness_false_positive"],
        "acceptable_gray_count": sem["acceptable_gray"],
        "semantic_hallucination_count": sem["valid_error"],
        "needs_policy_count": sem["needs_policy"],
        "_normalizer_resolved_fp": resolved_fp,
        "_consensus_split_of_residual": sem,
        "_residual_without_consensus": residual_no_consensus,
    }
    return metrics


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--result", action="append", default=[], help='"model=path"')
    ap.add_argument("--cases", required=True)
    ap.add_argument("--consensus", help="consensus_all10.jsonl (semantic 분해용)")
    ap.add_argument("--before-stats", help="normalization_stats.json (Before 스냅샷)")
    ap.add_argument("--out")
    a = ap.parse_args(argv)

    cases = load_cases(a.cases)
    consensus = load_consensus(a.consensus)

    after_by_model = {}
    for spec in a.result:
        label, path = spec.split("=", 1)
        _model, rows = load_result(path)
        after_by_model[label] = rescore_model(rows, cases, consensus)

    before = {}
    if a.before_stats and os.path.exists(a.before_stats):
        s = json.load(open(a.before_stats, encoding="utf-8"))
        before = {
            "raw_hallucination_count": s.get("raw_hallucination_flag_items"),
            "normalized_hallucination_count": s.get("stage1_residual_to_judge"),
            "semantic_hallucination_count": sum(
                1 for d in consensus.values() if d == "valid_error"),
            "_note": "Before = 현행 정규화기 스냅샷(normalization_stats.json, 전 모델 합산). semantic 은 consensus valid_error.",
        }

    report = {"before_all_models": before, "after_by_model": after_by_model}

    # ── 출력 ──
    print("=== HALLUCINATED_SKILL 재채점 (occurrence 단위) ===")
    if before:
        print(f"[Before · 전 모델 합산] raw={before['raw_hallucination_count']} "
              f"normalized={before['normalized_hallucination_count']} "
              f"semantic(valid_error)={before['semantic_hallucination_count']}")
    for label, m in after_by_model.items():
        print(f"[After · {label}] raw={m['raw_hallucination_count']} "
              f"normalized={m['normalized_hallucination_count']} "
              f"semantic={m['semantic_hallucination_count']} "
              f"harness_fp={m['harness_false_positive_count']} "
              f"gray={m['acceptable_gray_count']} "
              f"(정규화해소={m['_normalizer_resolved_fp']}, 잔여분해={m['_consensus_split_of_residual']})")
        if m["_residual_without_consensus"]:
            print(f"   !! consensus 없는 잔여: {m['_residual_without_consensus']}")

    if a.out:
        with open(a.out, "w", encoding="utf-8") as f:
            json.dump(report, f, ensure_ascii=False, indent=2)
        print(f"  → {a.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
