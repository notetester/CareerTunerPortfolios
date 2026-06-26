"""semantic skill judge — stage 2: judge 러너 + verdict 스키마/검증.

stage 1(정규화) 잔여 후보를 AI judge 가 의미 판정한다. judge 백엔드는 교체 가능:
  - external : ChatGPT/Claude/Gemini 등 외부 AI 가 judge_chatgpt_packet.md 를 보고 낸 verdict JSONL 을 수집·검증.
  - mock     : 모델/네트워크 없이 파이프라인(빌더→consensus)을 점검하는 결정론 더미 judge.
               normalizer 힌트만으로 보수 판정(soft_match→acceptable_gray, 미매칭→needs_policy).
               **실제 판정이 아니다.** CI/스모크 전용.

verdict 스키마(judge 1명당 후보 1줄):
  {candidateId, judge, decision, confidence(0~1), rationale, needsHumanReview}
  decision ∈ {valid_error, acceptable_gray, harness_false_positive, needs_policy}
  (acceptable_same_skill/acceptable_learning_context 는 acceptable_gray 로 합침)

사용:
  python scripts/semantic_skill_judge.py --packet <judge_packet.jsonl> --mock --out <verdicts_mock.jsonl>
  python scripts/semantic_skill_judge.py --validate <verdicts_chatgpt.jsonl>   # 외부 verdict 검증
"""
import argparse
import json
import os
import sys

DECISIONS = {"valid_error", "acceptable_gray", "harness_false_positive", "needs_policy"}
ACCEPTABLE_ALIASES = {"acceptable_same_skill", "acceptable_learning_context", "acceptable"}


def load_packet(path):
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                out.append(json.loads(line))
    return out


def canon_decision(d):
    d = (d or "").strip()
    if d in ACCEPTABLE_ALIASES:
        return "acceptable_gray"
    return d


def validate_verdict(v):
    """verdict 1건 구조 검증. 오류 문자열 리스트(빈 리스트=정상)."""
    errs = []
    if not v.get("candidateId"):
        errs.append("candidateId 누락")
    dec = canon_decision(v.get("decision"))
    if dec not in DECISIONS:
        errs.append(f"decision '{v.get('decision')}' 비정상")
    c = v.get("confidence")
    if not isinstance(c, (int, float)) or not (0.0 <= c <= 1.0):
        errs.append(f"confidence 범위 오류({c})")
    if not isinstance(v.get("needsHumanReview", False), bool):
        errs.append("needsHumanReview 는 bool")
    return errs


def normalize_verdict(v, default_judge="external"):
    return {
        "candidateId": v.get("candidateId"),
        "judge": v.get("judge") or default_judge,
        "decision": canon_decision(v.get("decision")),
        "confidence": float(v.get("confidence", 0.0)),
        "rationale": v.get("rationale", ""),
        "needsHumanReview": bool(v.get("needsHumanReview", False)),
        # mock(결정론 더미) verdict 식별 플래그 — consensus/리포트가 실측으로 오인하지 않게 보존.
        "synthetic": bool(v.get("synthetic", False)),
    }


def validate_verdict_file(path):
    verdicts, all_errs = [], []
    for ln, line in enumerate(open(path, encoding="utf-8"), 1):
        line = line.strip()
        if not line:
            continue
        try:
            v = json.loads(line)
        except json.JSONDecodeError as e:
            all_errs.append(f"{ln}행 JSON 오류: {e}")
            continue
        errs = validate_verdict(v)
        if errs:
            all_errs += [f"{ln}행 {v.get('candidateId')}: {e}" for e in errs]
        else:
            verdicts.append(normalize_verdict(v))
    return verdicts, all_errs


def mock_judge(candidates, judge_name="mock"):
    """결정론 더미 judge — normalizer 힌트만 사용(실판정 아님).

    soft_match(allowed 부분문자열 존재) → acceptable_gray(단, mock 은 단정 금지).
    매칭 0건(no_match) → needs_policy(범위밖일 수 있으나 단정 금지).
    부분 나열(partial_list) → needs_policy.

    ★ mock 은 절대 '검토 불요(needsHumanReview=False)'로 단정하지 않는다(reports/55). soft_match 를
      acceptable_gray·hr=False 로 내리면 그 whitewash 가 consensus 까지 전파된다. 모든 mock verdict 에
      needsHumanReview=True + synthetic=True 를 달아 실측으로 오인되지 않게 한다.
    """
    verdicts = []
    for c in candidates:
        st = (c.get("normalizer") or {}).get("status")
        method = (c.get("normalizer") or {}).get("method")
        if st == "soft_match":
            dec, conf, why = "acceptable_gray", 0.6, "allowed 스킬이 부분문자열로 포함(여분 토큰) — mock 추정"
        else:
            dec, conf, why = "needs_policy", 0.4, f"결정론 미해소({method}) — 의미 판정 필요"
        verdicts.append({
            "candidateId": c["candidateId"], "judge": judge_name,
            "decision": dec, "confidence": conf, "rationale": why,
            "needsHumanReview": True,   # mock 은 검토 불요로 단정하지 않음(whitewash 전파 차단)
            "synthetic": True,          # 실판정 아님 — consensus/리포트 식별용
        })
    return verdicts


def main():
    ap = argparse.ArgumentParser(description="semantic skill judge — judge 러너/검증")
    ap.add_argument("--packet", help="judge_packet.jsonl")
    ap.add_argument("--mock", action="store_true", help="결정론 더미 judge(파이프라인 점검 전용)")
    ap.add_argument("--judge-name", default="mock")
    ap.add_argument("--out", help="verdict 출력 JSONL")
    ap.add_argument("--validate", help="외부 verdict JSONL 검증(스키마·decision·confidence)")
    args = ap.parse_args()

    if args.validate:
        verdicts, errs = validate_verdict_file(args.validate)
        print(f"[validate] {len(verdicts)} verdict 정상, 오류 {len(errs)}건")
        for e in errs:
            print("  -", e)
        sys.exit(1 if errs else 0)

    if not (args.packet and args.mock):
        ap.error("판정은 --packet 와 --mock(외부 judge 는 --validate 로 수집)")
    cands = load_packet(args.packet)
    verdicts = mock_judge(cands, args.judge_name)
    out = args.out or os.path.join(os.path.dirname(args.packet), "verdicts_mock.jsonl")
    with open(out, "w", encoding="utf-8") as f:
        for v in verdicts:
            f.write(json.dumps(v, ensure_ascii=False) + "\n")
    print(f"[mock judge] {len(verdicts)} verdict → {out}")


if __name__ == "__main__":
    main()
