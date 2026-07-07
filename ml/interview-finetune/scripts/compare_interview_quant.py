"""interview-3b:q4(Q4_K_M) vs interview-3b(F16) 결정론 비교기.

eval_interview_model.py 가 낸 두 per-case 결과 파일(q4, f16)을 케이스 id 로 묶어:
  - per-case delta = q4_score - f16_score
  - mean|q4-f16|
  - agreement@10 (|q4-f16| <= 10 비율)
  - 유형(tag)별 MAE vs expectedScore (q4/f16 각각)
  - 유형별 json_parse_rate, cjk_leak_rate (q4/f16 각각)
을 계산하고 PASS/FAIL verdict 를 낸다.

PASS 기준(제안 — D 비준 대기):
  1. mean|q4-f16| <= 5
  2. agreement@10 >= 0.90
  3. q4 json_parse_rate >= f16 json_parse_rate
  4. q4 MAE(vs golden) <= f16 MAE + 3
  5. no new CJK leaks (q4 cjk 케이스 ⊆ f16 cjk 케이스)

Math core 는 순수 함수(I/O 없음) — 테스트가 파일 없이 직접 검증한다.

  python scripts/compare_interview_quant.py --q4 out/eval/q4.json --f16 out/eval/f16.json \
    --cases eval/interview_golden_cases.jsonl --out out/eval/interview-quant-verdict.json
"""
import argparse
import json
import os

AGREEMENT_THRESHOLD = 10       # |q4-f16| <= 10 이면 일치로 셈
PASS_MEAN_ABS_DIFF = 5.0       # (1)
PASS_AGREEMENT = 0.90          # (2)
PASS_MAE_SLACK = 3.0           # (4) q4 MAE 가 f16 MAE 를 이만큼 넘게 악화하면 FAIL


# ── 순수 함수 math core (I/O 없음) ────────────────────────────────────────────

def mean(values):
    vals = list(values)
    return sum(vals) / len(vals) if vals else 0.0


def per_case_deltas(paired):
    """paired: [{id, q4_score, f16_score, ...}] 중 양쪽 점수 있는 것만 delta.

    반환 [{id, delta, abs_delta}] (q4 또는 f16 점수가 None 인 케이스는 제외 — 점수차 정의 불가)."""
    out = []
    for p in paired:
        q, f = p.get("q4_score"), p.get("f16_score")
        if q is None or f is None:
            continue
        d = q - f
        out.append({"id": p["id"], "delta": d, "abs_delta": abs(d)})
    return out


def mean_abs_diff(paired):
    deltas = per_case_deltas(paired)
    return round(mean([d["abs_delta"] for d in deltas]), 3) if deltas else 0.0


def agreement_at(paired, threshold=AGREEMENT_THRESHOLD):
    """|q4-f16| <= threshold 인 비율. 양쪽 점수 있는 케이스만 분모."""
    deltas = per_case_deltas(paired)
    if not deltas:
        return 0.0
    ok = sum(1 for d in deltas if d["abs_delta"] <= threshold)
    return round(ok / len(deltas), 3)


def mae_vs_golden(rows):
    """rows: [{score, expected}] — score/expected 둘 다 있는 것만 |score-expected| 평균."""
    errs = [abs(r["score"] - r["expected"]) for r in rows
            if r.get("score") is not None and r.get("expected") is not None]
    return round(mean(errs), 3) if errs else None


def rate(flags):
    """flags: bool 리스트 — True 비율."""
    flags = list(flags)
    return round(sum(1 for x in flags if x) / len(flags), 3) if flags else 0.0


def by_tag(paired, side):
    """유형별 {tag: {mae_vs_golden, json_parse_rate, cjk_leak_rate, n}} (side='q4'|'f16')."""
    tags = {}
    for p in paired:
        tag = p.get("tag") or "UNKNOWN"
        tags.setdefault(tag, []).append(p)
    out = {}
    for tag, items in sorted(tags.items()):
        rows = [{"score": p.get(f"{side}_score"), "expected": p.get("expected")} for p in items]
        out[tag] = {
            "n": len(items),
            "mae_vs_golden": mae_vs_golden(rows),
            "json_parse_rate": rate(p.get(f"{side}_json_ok", False) for p in items),
            "cjk_leak_rate": rate(p.get(f"{side}_cjk", False) for p in items),
        }
    return out


def overall_rate(paired, side, field):
    return rate(p.get(f"{side}_{field}", False) for p in paired)


def overall_mae(paired, side):
    rows = [{"score": p.get(f"{side}_score"), "expected": p.get("expected")} for p in paired]
    return mae_vs_golden(rows)


def cjk_leak_ids(paired, side):
    return {p["id"] for p in paired if p.get(f"{side}_cjk")}


def compute_verdict(paired):
    """paired -> (verdict dict). 순수 함수: 입력 리스트만으로 전 지표+PASS/FAIL 계산."""
    m_abs = mean_abs_diff(paired)
    agree = agreement_at(paired, AGREEMENT_THRESHOLD)
    q4_parse = overall_rate(paired, "q4", "json_ok")
    f16_parse = overall_rate(paired, "f16", "json_ok")
    q4_mae = overall_mae(paired, "q4")
    f16_mae = overall_mae(paired, "f16")
    q4_cjk = cjk_leak_ids(paired, "q4")
    f16_cjk = cjk_leak_ids(paired, "f16")
    new_cjk = sorted(q4_cjk - f16_cjk)

    # 각 기준 판정(None MAE 는 계산 불가 → 보수적으로 그 기준 실패 처리)
    c1 = m_abs <= PASS_MEAN_ABS_DIFF
    c2 = agree >= PASS_AGREEMENT
    c3 = q4_parse >= f16_parse
    if q4_mae is None or f16_mae is None:
        c4 = False
    else:
        c4 = q4_mae <= f16_mae + PASS_MAE_SLACK
    c5 = len(new_cjk) == 0

    criteria = {
        "c1_mean_abs_diff_le_5": {"pass": bool(c1), "value": m_abs, "threshold": PASS_MEAN_ABS_DIFF},
        "c2_agreement_at_10_ge_0.90": {"pass": bool(c2), "value": agree, "threshold": PASS_AGREEMENT},
        "c3_q4_parse_ge_f16_parse": {"pass": bool(c3), "q4": q4_parse, "f16": f16_parse},
        "c4_q4_mae_not_worse_than_f16_plus_3": {
            "pass": bool(c4), "q4_mae": q4_mae, "f16_mae": f16_mae, "slack": PASS_MAE_SLACK},
        "c5_no_new_cjk_leaks": {"pass": bool(c5), "new_cjk_ids": new_cjk},
    }
    verdict = "PASS" if all([c1, c2, c3, c4, c5]) else "FAIL"
    return {
        "verdict": verdict,
        "criterion_note": "임계값은 제안(proposal) — D 오너가 라이브 A/B 로 비준 전까지 확정 아님.",
        "mean_abs_diff": m_abs,
        "agreement_at_10": agree,
        "q4_json_parse_rate": q4_parse,
        "f16_json_parse_rate": f16_parse,
        "q4_mae_vs_golden": q4_mae,
        "f16_mae_vs_golden": f16_mae,
        "new_cjk_leak_ids": new_cjk,
        "criteria": criteria,
        "per_case_deltas": per_case_deltas(paired),
        "by_tag_q4": by_tag(paired, "q4"),
        "by_tag_f16": by_tag(paired, "f16"),
        "paired_case_count": len(paired),
    }


# ── I/O 경계(순수 core 를 파일과 연결) ────────────────────────────────────────

def _index(results):
    """result rows 를 id -> row(마지막 run 승) 로. repeat 이 있으면 첫 run 을 대표로 쓴다."""
    by = {}
    for r in results:
        cid = r.get("id")
        if cid not in by:  # 첫 등장(run 0) 을 대표로
            by[cid] = r
    return by


def load_result(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def load_golden(path):
    exp = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            c = json.loads(line)
            exp[c["id"]] = {"expected": c.get("expectedScore"), "tag": c.get("questionType")}
    return exp


def build_paired(q4_data, f16_data, golden):
    """두 결과 + 골든 -> paired 리스트(compute_verdict 입력)."""
    q4 = _index(q4_data.get("results", []))
    f16 = _index(f16_data.get("results", []))
    paired = []
    for cid in q4:
        if cid not in f16:
            continue
        g = golden.get(cid, {})
        qr, fr = q4[cid], f16[cid]
        paired.append({
            "id": cid,
            "tag": g.get("tag") or qr.get("questionType"),
            "expected": g.get("expected") if g else qr.get("expectedScore"),
            "q4_score": qr.get("parsed_score"),
            "f16_score": fr.get("parsed_score"),
            "q4_json_ok": bool(qr.get("json_ok")),
            "f16_json_ok": bool(fr.get("json_ok")),
            "q4_cjk": bool(qr.get("cjk_leak")),
            "f16_cjk": bool(fr.get("cjk_leak")),
        })
    return paired


def main():
    ap = argparse.ArgumentParser(description="interview q4 vs f16 결정론 비교기 → verdict")
    ap.add_argument("--q4", required=True, help="q4 결과 JSON (eval_interview_model.py --out)")
    ap.add_argument("--f16", required=True, help="f16 결과 JSON")
    ap.add_argument("--cases", required=True, help="골든셋 JSONL (expectedScore/questionType 앵커)")
    ap.add_argument("--out", default="out/eval/interview-quant-verdict.json")
    args = ap.parse_args()

    q4_data = load_result(args.q4)
    f16_data = load_result(args.f16)
    golden = load_golden(args.cases)
    paired = build_paired(q4_data, f16_data, golden)
    if not paired:
        raise SystemExit("q4·f16 결과에 공통 케이스가 없습니다.")

    verdict = compute_verdict(paired)
    verdict["q4_model"] = q4_data.get("summary", {}).get("model")
    verdict["f16_model"] = f16_data.get("summary", {}).get("model")

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(verdict, f, ensure_ascii=False, indent=2)

    v = verdict
    print(f"[compare] {v['verdict']}  (q4={v['q4_model']} vs f16={v['f16_model']}, "
          f"cases={v['paired_case_count']})")
    print(f"  mean|q4-f16|={v['mean_abs_diff']} (<= {PASS_MEAN_ABS_DIFF})  "
          f"agreement@10={v['agreement_at_10']} (>= {PASS_AGREEMENT})")
    print(f"  json_parse q4={v['q4_json_parse_rate']} f16={v['f16_json_parse_rate']}  "
          f"MAE q4={v['q4_mae_vs_golden']} f16={v['f16_mae_vs_golden']}")
    print(f"  new_cjk_leaks={v['new_cjk_leak_ids']}  → {args.out}")
    for k, c in v["criteria"].items():
        print(f"    [{'PASS' if c['pass'] else 'FAIL'}] {k}")


if __name__ == "__main__":
    main()
