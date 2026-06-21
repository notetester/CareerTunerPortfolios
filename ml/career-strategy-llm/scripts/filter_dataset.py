"""
C_FIT_EXPLAIN raw 데이터 품질 필터.

validate_dataset.check_row 로 실패행을 제거하고, 근접 중복(동일 fitSummary)을 제거해
깨끗한 raw 를 만든다. 이후 assemble_dataset.py 가 messages JSONL 로 조립한다.

사용:
    python filter_dataset.py --raw ../data/raw.fit_explain.300.json --out ../data/raw.fit_explain.300.clean.json
"""
import argparse
import json
import re

from validate_dataset import check_row


def _norm_text(t):
    return re.sub(r"\s+", " ", (t or "").strip()).lower()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--raw", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    with open(args.raw, "r", encoding="utf-8") as f:
        rows = json.load(f)

    kept, dropped_fail, dropped_dup = [], 0, 0
    seen_summary, seen_id = set(), set()
    reasons = {}
    for row in rows:
        sid = (row.get("seed") or {}).get("id")
        problems, _warn, _info = check_row(row)
        if problems:
            dropped_fail += 1
            for p in problems:
                k = p.split(":")[0]
                reasons[k] = reasons.get(k, 0) + 1
            continue
        summ = _norm_text((row.get("fit_explain") or {}).get("fitSummary"))
        if sid in seen_id or summ in seen_summary:
            dropped_dup += 1
            continue
        seen_id.add(sid)
        seen_summary.add(summ)
        kept.append(row)

    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(kept, f, ensure_ascii=False)

    print(f"입력 {len(rows)} → 유지 {len(kept)} (검증실패 {dropped_fail}, 중복 {dropped_dup}) -> {args.out}")
    if reasons:
        print("실패 사유:", json.dumps(reasons, ensure_ascii=False))


if __name__ == "__main__":
    main()
