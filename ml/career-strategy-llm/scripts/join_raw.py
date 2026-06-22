"""
워크플로우(generate_dataset.workflow.js) 출력 + 시드 파일 -> raw.json 조인.

워크플로우 result 는 { items: [{seedId, fit_explain}] } 이고, 시드(점수 사전계산 포함)는
seeds.*.jsonl 에 있다. 이 둘을 seedId 로 조인해 assemble_dataset.py 가 먹는
raw 형식 [{ "seed": {...}, "fit_explain": {...} }] 을 만든다.

--wf-output 은 Workflow 도구가 남기는 .output 파일(JSON: {summary,...,result:{items:[...]}})
또는 {items:[...]} / [{seedId,...}] 형태 모두 지원.

사용:
    python join_raw.py --seeds ../data/seeds.fit_explain.300.jsonl \
        --wf-output <temp .output 경로> --out ../data/raw.fit_explain.300.json
"""
import argparse
import json


def load_seeds(path):
    by_id = {}
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            s = json.loads(line)
            by_id[s["id"]] = s
    return by_id


def extract_items(obj):
    if isinstance(obj, dict) and "result" in obj:
        obj = obj["result"]
    if isinstance(obj, dict) and "items" in obj:
        return obj["items"]
    if isinstance(obj, list):
        return obj
    raise SystemExit("워크플로우 출력에서 items 를 찾지 못했습니다.")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--seeds", required=True)
    ap.add_argument("--wf-output", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    seeds = load_seeds(args.seeds)
    with open(args.wf_output, "r", encoding="utf-8") as f:
        items = extract_items(json.load(f))

    raw, miss_seed, no_fit = [], 0, 0
    seen = set()
    for it in items:
        sid = it.get("seedId")
        fit = it.get("fit_explain")
        if sid in seen:
            continue
        if sid not in seeds:
            miss_seed += 1
            continue
        if not isinstance(fit, dict):
            no_fit += 1
            continue
        seen.add(sid)
        raw.append({"seed": seeds[sid], "fit_explain": fit})

    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(raw, f, ensure_ascii=False)

    print(f"조인 {len(raw)} (시드 {len(seeds)}, items {len(items)}) -> {args.out}")
    if miss_seed:
        print(f"  경고: 시드 매칭 실패 items {miss_seed}개")
    if no_fit:
        print(f"  경고: fit_explain 없음 items {no_fit}개")
    covered = len(seen)
    if covered < len(seeds):
        print(f"  미생성 시드 {len(seeds) - covered}개(teacher 배치 실패 가능) — 필요 시 재생성")


if __name__ == "__main__":
    main()
