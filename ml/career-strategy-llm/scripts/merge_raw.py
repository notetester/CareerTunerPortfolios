"""
여러 clean raw([{seed, fit_explain}])를 합쳐 mixed raw 를 만든다(seedId 재부여로 충돌 방지).

여러 직군 셋(예: IT it_mvp + 비IT nonit)을 하나의 통합 데이터셋으로 합칠 때 사용.
seedId 가 셋마다 cseed_0001.. 로 겹치므로 병합 시 mix_0001.. 로 재부여한다.

사용:
    python merge_raw.py --inputs ../data/raw.fit_explain.it_mvp.300.clean.json \
                                 ../data/raw.fit_explain.nonit.120.clean.json \
        --out ../data/raw.fit_explain.mixed.clean.json --reid mix
"""
import argparse
import json
from collections import Counter

from seed_profiles import FAMILY_GROUP  # domainGroup backfill 용


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--inputs", nargs="+", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--reid", default="mix", help="재부여할 seedId 접두사(빈 문자면 유지)")
    args = ap.parse_args()

    merged = []
    for p in args.inputs:
        with open(p, "r", encoding="utf-8") as f:
            rows = json.load(f)
        merged.extend(rows)
        print(f"  + {p}: {len(rows)}")

    for i, row in enumerate(merged, 1):
        seed = row.get("seed")
        if not isinstance(seed, dict):
            continue
        if args.reid:
            seed["id"] = f"{args.reid}_{i:04d}"
        # 구버전 IT baseline 시드는 domainGroup 이 없으므로 jobFamily 로 backfill
        if not seed.get("domainGroup") and seed.get("jobFamily") in FAMILY_GROUP:
            seed["domainGroup"] = FAMILY_GROUP[seed["jobFamily"]]

    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(merged, f, ensure_ascii=False)

    print(f"병합 {len(merged)} -> {args.out}")
    print("domainGroup:", dict(Counter((r.get('seed') or {}).get('domainGroup') for r in merged)))


if __name__ == "__main__":
    main()
