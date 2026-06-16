"""
면접 평가 학습 데이터 준비.

백엔드 `GET /api/admin/interview/training/export` 가 내려주는 JSONL(OpenAI chat 포맷:
각 줄이 {"messages": [system, user, assistant]})을 받아 train/val 로 나눈다.
이 포맷은 OpenAI 파인튜닝뿐 아니라 오픈모델 SFT(LLaMA-Factory/trl)에서도 그대로 쓴다.

사용:
    python prepare_data.py --input export.jsonl --out-dir data --val-ratio 0.1
"""

import argparse
import json
import os
import random


def load_jsonl(path):
    rows = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            obj = json.loads(line)
            # messages 키가 있는 정상 샘플만 사용한다.
            if isinstance(obj, dict) and isinstance(obj.get("messages"), list):
                rows.append(obj)
    return rows


def write_jsonl(path, rows):
    with open(path, "w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, help="백엔드 export JSONL 경로")
    parser.add_argument("--out-dir", default="data")
    parser.add_argument("--val-ratio", type=float, default=0.1)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    rows = load_jsonl(args.input)
    if not rows:
        raise SystemExit("학습 샘플이 없습니다. 백엔드에서 평가를 더 쌓은 뒤 export 하세요.")

    random.Random(args.seed).shuffle(rows)
    n_val = max(1, int(len(rows) * args.val_ratio)) if len(rows) > 10 else 0
    val, train = rows[:n_val], rows[n_val:]

    os.makedirs(args.out_dir, exist_ok=True)
    write_jsonl(os.path.join(args.out_dir, "train.jsonl"), train)
    if val:
        write_jsonl(os.path.join(args.out_dir, "val.jsonl"), val)

    print(f"전체 {len(rows)}건 → train {len(train)} / val {len(val)}")
    print(f"출력: {args.out_dir}/train.jsonl" + ("" if not val else f", {args.out_dir}/val.jsonl"))


if __name__ == "__main__":
    main()
