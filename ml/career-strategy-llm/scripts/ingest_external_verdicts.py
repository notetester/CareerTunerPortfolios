"""semantic skill judge — 외부 판정자(다른 AI/사람) verdict 수집·정규화.

평가지(judge_chatgpt_packet.md)를 외부 AI/사람에게 돌려 받은 verdict 파일들을
consensus 엔진이 먹을 수 있게 정규화한다:
  - 응답 템플릿이 judge 필드를 "chatgpt" placeholder 로 남기므로 **실제 출처 라벨로 재기입**.
  - .json(배열)/.jsonl 모두 허용.
  - packet 의 candidateId 집합과 대조해 누락/잉여/오타 검출.
  - decision 을 canon_decision 으로 표준화, 스키마 검증.
정규화 결과를 verdicts_ext_<label>.jsonl 로 써서 judge_consensus.py --verdicts 에 그대로 추가.

사용:
  python scripts/ingest_external_verdicts.py --packet <judge_packet.jsonl> --out-dir <dir> \
    --source "gpt-1=PATH.jsonl" --source "gemini=PATH.json" ...
"""
import argparse
import json
import os
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)
from semantic_skill_judge import canon_decision  # noqa: E402

VALID = {"valid_error", "acceptable_gray", "harness_false_positive", "needs_policy"}
# 자기검증 불가 placeholder — 이게 보이면 출처는 운영자 폴더 라벨에만 의존(reports/43 caveat 3)
PLACEHOLDER_IDS = {"chatgpt", "<당신의 식별자>", "<judgeid>", "judge", ""}


def _load(path):
    txt = open(path, encoding="utf-8").read().strip()
    if not txt:
        return []
    if path.lower().endswith(".json"):
        data = json.loads(txt)
        return data if isinstance(data, list) else [data]
    return [json.loads(l) for l in txt.splitlines() if l.strip()]


def ingest_one(label, path, cand_ids, out_dir):
    rows = _load(path)
    seen, norm, problems = set(), [], []
    self_ids = set()
    for r in rows:
        cid = r.get("candidateId")
        if cid not in cand_ids:
            problems.append(f"unknown candidateId: {cid}")
            continue
        sid = str(r.get("judgeId") or r.get("judge") or "").strip()
        self_ids.add(sid.lower())
        if cid in seen:
            problems.append(f"duplicate candidateId: {cid}")
            continue
        seen.add(cid)
        dec = canon_decision(r.get("decision", ""))
        if dec not in VALID:
            problems.append(f"bad decision '{r.get('decision')}' @ {cid}")
            continue
        try:
            conf = float(r.get("confidence", 0.0))
        except (TypeError, ValueError):
            conf = 0.0
            problems.append(f"bad confidence @ {cid}")
        norm.append({
            "candidateId": cid,
            "judge": label,                       # 실제 출처로 재라벨(운영자 폴더 라벨)
            "selfReportedJudgeId": sid or None,   # 파일이 스스로 밝힌 식별자(자기검증용)
            "decision": dec,
            "confidence": conf,
            "rationale": r.get("rationale", ""),
            "needsHumanReview": bool(r.get("needsHumanReview", dec == "needs_policy")),
        })
    missing = sorted(cand_ids - seen)
    if missing:
        problems.append(f"missing {len(missing)} candidates: {[m.split('::')[1] for m in missing]}")
    # 출처 자기검증: 파일이 밝힌 식별자가 placeholder 면 출처는 폴더 라벨에만 의존(reports/43 caveat 3)
    if self_ids and self_ids <= PLACEHOLDER_IDS:
        problems.append(f"judgeId placeholder({sorted(self_ids)}) — 출처 자기검증 불가, 폴더 라벨('{label}')에 의존")
    out_path = os.path.join(out_dir, f"verdicts_ext_{label}.jsonl")
    with open(out_path, "w", encoding="utf-8") as f:
        for r in norm:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    dist = {}
    for r in norm:
        dist[r["decision"]] = dist.get(r["decision"], 0) + 1
    return {"label": label, "n": len(norm), "dist": dist, "problems": problems, "out": out_path}


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--packet", required=True)
    ap.add_argument("--out-dir", required=True)
    ap.add_argument("--source", action="append", required=True, help='"label=path"')
    a = ap.parse_args(argv)

    cand_ids = {json.loads(l)["candidateId"] for l in open(a.packet, encoding="utf-8") if l.strip()}
    os.makedirs(a.out_dir, exist_ok=True)

    print(f"packet candidates: {len(cand_ids)}\n")
    any_problem = False
    cli_args = []
    for s in a.source:
        label, path = s.split("=", 1)
        res = ingest_one(label, path, cand_ids, a.out_dir)
        flag = "  !!" if res["problems"] else ""
        print(f"{res['label']:18s} n={res['n']:2d}  {res['dist']}{flag}")
        for p in res["problems"]:
            any_problem = True
            print(f"     - {p}")
        cli_args.append(f"--verdicts {label}={res['out']}")
    print("\nconsensus 추가 인자:\n  " + " \\\n  ".join(cli_args))
    return 1 if any_problem else 0


if __name__ == "__main__":
    raise SystemExit(main())
