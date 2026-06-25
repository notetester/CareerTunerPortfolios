"""semantic skill judge — stage 3: consensus 병합 + 병렬 지표.

여러 judge(예: claude/chatgpt/gemini)의 verdict 를 후보별로 합쳐 합의 결정을 내고,
기존 raw hallucination 지표를 **대체하지 않는 병렬 지표**를 산출한다.

consensus 규칙(보수적):
  - 후보별 judge decision 다수결. 최다 라벨이 finalDecision.
  - 동률(최다 라벨 2개 이상) → needs_policy + needsHumanReview.
  - 아래 중 하나면 needsHumanReview=true:
      · 어떤 judge 든 needsHumanReview=true
      · 어떤 judge 든 needs_policy 투표
      · 다수결이 과반 미달(만장일치/과반 아님)
      · 합의 confidence < CONF_THRESHOLD
  - 합의 confidence = 승리 라벨에 투표한 judge confidence 평균.
  - verdict 0건 후보 → needs_policy + needsHumanReview.

병렬 지표(occurrence=원시 flag 단위, 모델별 분해 포함):
  raw_hallucination_flag_items        — 원시 하니스 카운트(유지)
  stage1_resolved_false_positive      — 결정론 오탐 해소
  normalized_hallucination_count      — 정규화 후 잔여 flag(=judge 대상)
  semantic_hallucination_count        — 합의 valid_error (진짜 범위밖)
  harness_false_positive_count        — stage1 + 합의 harness_false_positive
  acceptable_gray_count / needs_policy_count
  judge_confidence / needs_human_review_candidates

사용:
  python scripts/judge_consensus.py --packet <judge_packet.jsonl> \
    --verdicts claude=<v_claude.jsonl> --verdicts chatgpt=<v_chatgpt.jsonl> \
    --stats <normalization_stats.json> --out-dir <dir>
"""
import argparse
import json
import os
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)
from semantic_skill_judge import canon_decision, normalize_verdict, validate_verdict  # noqa: E402

CONF_THRESHOLD = 0.6
DECISION_LABELS = ["valid_error", "acceptable_gray", "harness_false_positive", "needs_policy"]


def _load_jsonl(path):
    out = []
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if line:
            out.append(json.loads(line))
    return out


def consensus_for_candidate(verdicts):
    """한 후보의 judge verdict 들 → 합의 결정 dict."""
    if not verdicts:
        return {"finalDecision": "needs_policy", "needsHumanReview": True,
                "agreement": "0/0", "confidence": 0.0, "votes": {}, "perJudge": []}
    votes = {}
    for v in verdicts:
        d = canon_decision(v["decision"])
        votes[d] = votes.get(d, 0) + 1
    top = max(votes.values())
    winners = sorted([d for d, n in votes.items() if n == top])
    n = len(verdicts)

    tie = len(winners) > 1
    final = "needs_policy" if tie else winners[0]
    win_conf = [v["confidence"] for v in verdicts if canon_decision(v["decision"]) == final]
    conf = round(sum(win_conf) / len(win_conf), 3) if win_conf else 0.0

    any_hr = any(v.get("needsHumanReview") for v in verdicts)
    any_policy = any(canon_decision(v["decision"]) == "needs_policy" for v in verdicts)
    no_majority = top * 2 <= n            # 과반 미달
    human = bool(tie or any_hr or any_policy or no_majority or conf < CONF_THRESHOLD)

    return {
        "finalDecision": final,
        "needsHumanReview": human,
        "agreement": f"{top}/{n}",
        "confidence": conf,
        "votes": votes,
        "perJudge": [{"judge": v.get("judge"), "decision": canon_decision(v["decision"]),
                      "confidence": v["confidence"], "needsHumanReview": v.get("needsHumanReview", False),
                      "rationale": v.get("rationale", "")} for v in verdicts],
    }


def run_consensus(candidates, verdicts_by_cand, stats):
    rows = []
    # 모델별 occurrence 분해를 위한 누적기
    occ_by_decision = {d: 0 for d in DECISION_LABELS}
    occ_by_model_decision = {}
    confs, human = [], 0

    for c in candidates:
        cid = c["candidateId"]
        cons = consensus_for_candidate(verdicts_by_cand.get(cid, []))
        rows.append({**{k: c[k] for k in ("candidateId", "caseId", "flaggedText")},
                     "occurrences": c.get("occurrences", []),
                     "normalizerStatus": (c.get("normalizer") or {}).get("status"),
                     **cons})
        confs.append(cons["confidence"])
        if cons["needsHumanReview"]:
            human += 1
        nocc = max(1, len(c.get("occurrences", [])))
        occ_by_decision[cons["finalDecision"]] = occ_by_decision.get(cons["finalDecision"], 0) + nocc
        for o in (c.get("occurrences") or [{"model": "unknown"}]):
            m = o.get("model", "unknown")
            occ_by_model_decision.setdefault(m, {d: 0 for d in DECISION_LABELS})
            occ_by_model_decision[m][cons["finalDecision"]] += 1

    raw = stats.get("raw_hallucination_flag_items", 0)
    resolved = stats.get("stage1_resolved_false_positive", 0)
    residual = stats.get("stage1_residual_to_judge", 0)

    metrics = {
        "raw_hallucination_flag_items": raw,
        "stage1_resolved_false_positive": resolved,
        "normalized_hallucination_count": residual,
        "semantic_hallucination_count": occ_by_decision["valid_error"],
        "harness_false_positive_count": resolved + occ_by_decision["harness_false_positive"],
        "acceptable_gray_count": occ_by_decision["acceptable_gray"],
        "needs_policy_count": occ_by_decision["needs_policy"],
        "unique_candidates": len(candidates),
        "unique_by_final_decision": _unique_by_decision(rows),
        "by_model_occurrences": occ_by_model_decision,
        "judge_confidence": round(sum(confs) / len(confs), 3) if confs else 0.0,
        "needs_human_review_candidates": human,
        "conf_threshold": CONF_THRESHOLD,
    }
    return rows, metrics


def _unique_by_decision(rows):
    out = {d: 0 for d in DECISION_LABELS}
    for r in rows:
        out[r["finalDecision"]] = out.get(r["finalDecision"], 0) + 1
    return out


def _parse_verdict_specs(specs):
    """['claude=path', ...] → {candidateId: [verdict,...]}, 검증 오류 리스트."""
    by_cand, errs = {}, []
    for spec in specs:
        judge, path = (spec.split("=", 1) if "=" in spec else (None, spec))
        for v in _load_jsonl(path):
            e = validate_verdict(v)
            if e:
                errs += [f"{path} {v.get('candidateId')}: {x}" for x in e]
                continue
            nv = normalize_verdict(v, default_judge=judge or "external")
            if judge:
                nv["judge"] = judge
            by_cand.setdefault(nv["candidateId"], []).append(nv)
    return by_cand, errs


def main():
    ap = argparse.ArgumentParser(description="semantic skill judge — consensus 병합 + 병렬 지표")
    ap.add_argument("--packet", required=True)
    ap.add_argument("--verdicts", action="append", default=[], help="judge=path (여러 번)")
    ap.add_argument("--stats", help="normalization_stats.json (raw/resolved/residual)")
    ap.add_argument("--out-dir", required=True)
    args = ap.parse_args()

    candidates = _load_jsonl(args.packet)
    by_cand, errs = _parse_verdict_specs(args.verdicts)
    if errs:
        print(f"[consensus] verdict 검증 오류 {len(errs)}건:")
        for e in errs:
            print("  -", e)
        sys.exit(1)
    stats = json.load(open(args.stats, encoding="utf-8")) if args.stats else {}

    rows, metrics = run_consensus(candidates, by_cand, stats)
    os.makedirs(args.out_dir, exist_ok=True)
    cpath = os.path.join(args.out_dir, "consensus.jsonl")
    with open(cpath, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    mpath = os.path.join(args.out_dir, "semantic_metrics.json")
    with open(mpath, "w", encoding="utf-8") as f:
        json.dump(metrics, f, ensure_ascii=False, indent=2)

    print(f"[consensus] 후보 {len(rows)} · 병렬 지표:")
    print(f"  raw={metrics['raw_hallucination_flag_items']} "
          f"stage1해소={metrics['stage1_resolved_false_positive']} "
          f"정규화후잔여={metrics['normalized_hallucination_count']}")
    print(f"  semantic(valid_error)={metrics['semantic_hallucination_count']} "
          f"harness_FP합계={metrics['harness_false_positive_count']} "
          f"acceptable_gray={metrics['acceptable_gray_count']} needs_policy={metrics['needs_policy_count']}")
    print(f"  unique_by_decision={metrics['unique_by_final_decision']} "
          f"needsHumanReview={metrics['needs_human_review_candidates']} conf={metrics['judge_confidence']}")
    print(f"  → {cpath}\n  → {mpath}")


if __name__ == "__main__":
    main()
