"""
C 학습 데이터 준비 (D의 ml/interview-finetune/prepare_data.py 와 동일 로직, 도메인 주석만 교체).

assemble_dataset.py 가 만든 messages JSONL(각 줄이 {"messages":[system,user,assistant]})을 받아
train/val 로 나눈다. 이 포맷은 trl SFTTrainer 가 그대로 소비한다.

사용:
    python prepare_data.py --input dataset.jsonl --out-dir data --val-ratio 0.1
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
    parser.add_argument("--input", required=True, help="assemble_dataset.py 산출 JSONL 경로")
    parser.add_argument("--out-dir", default="data")
    parser.add_argument("--val-ratio", type=float, default=0.1)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--tag", default="", help="파일명 태그: train.<tag>.jsonl (빈 값이면 train.jsonl)")
    args = parser.parse_args()
    suffix = f".{args.tag}" if args.tag else ""

    rows = load_jsonl(args.input)
    if not rows:
        raise SystemExit("학습 샘플이 없습니다. 합성 데이터를 더 생성한 뒤 assemble 하세요.")

    random.Random(args.seed).shuffle(rows)
    n_val = max(1, int(len(rows) * args.val_ratio)) if len(rows) > 10 else 0
    val, train = rows[:n_val], rows[n_val:]

    os.makedirs(args.out_dir, exist_ok=True)
    train_name, val_name = f"train{suffix}.jsonl", f"val{suffix}.jsonl"
    write_jsonl(os.path.join(args.out_dir, train_name), train)
    if val:
        write_jsonl(os.path.join(args.out_dir, val_name), val)

    print(f"전체 {len(rows)}건 → train {len(train)} / val {len(val)}")
    print(f"출력: {args.out_dir}/{train_name}" + ("" if not val else f", {args.out_dir}/{val_name}"))


if __name__ == "__main__":
    main()
