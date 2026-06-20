"""
학습 전 사람 표본검수용 balanced 샘플 마크다운 생성.

mixed raw([{seed, fit_explain}])에서 IT/비IT × APPLY/COMPLEMENT/HOLD 를 고르게 뽑아
검수 포인트와 함께 마크다운으로 출력한다.

사용:
    python make_review_samples.py --raw ../data/raw.fit_explain.mixed.clean.json \
        --out ../reports/04_human_review_samples.md --it 15 --nonit 15
"""
import argparse
import json
import random

from seed_profiles import is_it_group

DECISIONS = ["APPLY", "COMPLEMENT_BEFORE_APPLY", "HOLD"]
REVIEW_POINTS = [
    "입력에 없는 회사/역량/자격증을 만들었는가?",
    "부족역량을 보유역량처럼 말했는가?",
    "HOLD인데 지원 권장처럼 말하는가?",
    "비IT 직군에 IT 표현이 섞였는가?",
    "한국어 문장이 발표/시연에 쓸 만큼 자연스러운가?",
]


def pick(rows, total, seed):
    """decision 별로 고르게 total 개 선택."""
    rng = random.Random(seed)
    by_dec = {d: [r for r in rows if (r.get("seed") or {}).get("applyDecision") == d] for d in DECISIONS}
    for d in by_dec:
        rng.shuffle(by_dec[d])
    per = total // len(DECISIONS)
    picked, idx = [], {d: 0 for d in DECISIONS}
    # 1차: 각 decision 당 per 개
    for d in DECISIONS:
        take = by_dec[d][:per]
        picked += take
        idx[d] = len(take)
    # 부족분 라운드로빈 보충
    di = 0
    while len(picked) < total:
        d = DECISIONS[di % len(DECISIONS)]
        di += 1
        if idx[d] < len(by_dec[d]):
            picked.append(by_dec[d][idx[d]])
            idx[d] += 1
        elif all(idx[x] >= len(by_dec[x]) for x in DECISIONS):
            break
    return picked


def fmt(i, row):
    s, f = row.get("seed") or {}, row.get("fit_explain") or {}
    j = json.dumps(f, ensure_ascii=False, indent=2)
    return f"""### {i}. `{s.get('id')}` · {s.get('domainGroup')} · {s.get('jobTitle')}
- fitScore: **{s.get('fitScore')}** · applyDecision: **{s.get('applyDecision')}** · 경력: {s.get('experienceLevel')} · 회사: {s.get('companyName')}
- matchedSkills: {', '.join(s.get('matchedSkills') or []) or '(없음)'}
- missingRequiredSkills: {', '.join(s.get('missingRequiredSkills') or []) or '(없음)'}
- missingPreferredSkills: {', '.join(s.get('missingPreferredSkills') or []) or '(없음)'}
- assistant 출력:
```json
{j}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--raw", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--it", type=int, default=15)
    ap.add_argument("--nonit", type=int, default=15)
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    with open(args.raw, "r", encoding="utf-8") as fp:
        rows = json.load(fp)
    it_rows = [r for r in rows if is_it_group((r.get("seed") or {}).get("domainGroup"))]
    nonit_rows = [r for r in rows if not is_it_group((r.get("seed") or {}).get("domainGroup"))]

    it_pick = pick(it_rows, args.it, args.seed)
    nonit_pick = pick(nonit_rows, args.nonit, args.seed + 1)

    lines = [
        "# 학습 전 사람 표본검수 샘플 (mixed)",
        "",
        f"> 총 {len(it_pick) + len(nonit_pick)}건 = IT/SW {len(it_pick)} + 비IT {len(nonit_pick)}. "
        "APPLY/COMPLEMENT/HOLD 고르게 포함. 학습 전 teacher 출력을 사람이 검수한다.",
        "",
        "## 검수 포인트(각 샘플 공통)",
        "",
    ]
    for k, p in enumerate(REVIEW_POINTS, 1):
        lines.append(f"{k}. {p}")
    lines += ["", "---", "", "## IT/SW 샘플", ""]
    for i, r in enumerate(it_pick, 1):
        lines.append(fmt(i, r))
    lines += ["---", "", "## 비IT 샘플", ""]
    for i, r in enumerate(nonit_pick, 1):
        lines.append(fmt(i, r))

    with open(args.out, "w", encoding="utf-8") as fp:
        fp.write("\n".join(lines))
    print(f"검수 샘플 {len(it_pick) + len(nonit_pick)}건(IT {len(it_pick)}/비IT {len(nonit_pick)}) -> {args.out}")


if __name__ == "__main__":
    main()
