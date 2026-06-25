"""
CareerTuner A파트 프로필 AI용 QLoRA 학습 스크립트.

이 스크립트는 docs/ai-training/profile_ai_training_samples_500.jsonl 데이터를 읽어서
Qwen3-4B-Instruct 계열 모델에 LoRA 어댑터만 학습합니다.

중요:
- 원본 모델 전체를 다시 저장하지 않고, adapter 파일만 저장합니다.
- Windows 네이티브 환경에서 bitsandbytes가 실패하면 WSL2 Ubuntu 또는 Linux 환경에서 실행하세요.
- 500개 샘플은 1차 QLoRA 학습 기본 데이터셋입니다. 이후 품질을 더 높이려면 사람 검수 데이터를 추가해 1000개 이상으로 늘리는 것을 권장합니다.
"""

from __future__ import annotations

import argparse
import inspect
import json
from pathlib import Path
from typing import Any

import torch
from datasets import Dataset
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
    DataCollatorForLanguageModeling,
    Trainer,
    TrainingArguments,
)


DEFAULT_MODEL_NAME = "Qwen/Qwen3-4B-Instruct-2507"
DEFAULT_DATASET_PATH = "docs/ai-training/profile_ai_training_samples_500.jsonl"
DEFAULT_OUTPUT_DIR = "docs/ai-training/output/qwen3-profile-lora"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train CareerTuner profile AI LoRA adapter.")
    parser.add_argument("--model-name", default=DEFAULT_MODEL_NAME)
    parser.add_argument("--dataset", default=DEFAULT_DATASET_PATH)
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--max-seq-length", type=int, default=2048)
    parser.add_argument("--epochs", type=float, default=3.0)
    parser.add_argument("--learning-rate", type=float, default=2e-4)
    parser.add_argument("--batch-size", type=int, default=1)
    parser.add_argument("--gradient-accumulation-steps", type=int, default=8)
    parser.add_argument("--eval-ratio", type=float, default=0.1)
    parser.add_argument("--test-ratio", type=float, default=0.1)
    parser.add_argument("--seed", type=int, default=42)
    return parser.parse_args()


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        raise FileNotFoundError(f"Dataset not found: {path}")

    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as file:
        for line_no, line in enumerate(file, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                rows.append(json.loads(line))
            except json.JSONDecodeError as exc:
                raise ValueError(f"Invalid JSONL at line {line_no}: {exc}") from exc
    if not rows:
        raise ValueError(f"Dataset is empty: {path}")
    return rows


def render_chat_text(tokenizer: AutoTokenizer, row: dict[str, Any]) -> str:
    messages = row.get("messages")
    if not isinstance(messages, list) or len(messages) < 3:
        raise ValueError("Each dataset row must have system, user, assistant messages.")

    # Qwen 계열 tokenizer는 chat_template을 제공하므로, 모델이 실제 대화 형식을 그대로 학습하게 합니다.
    if getattr(tokenizer, "chat_template", None):
        return tokenizer.apply_chat_template(
            messages,
            tokenize=False,
            add_generation_prompt=False,
        )

    # chat_template이 없는 모델로 바꿨을 때를 위한 최소 fallback입니다.
    return "\n".join(f"{message['role']}: {message['content']}" for message in messages)


def split_dataset(rows: list[dict[str, Any]], eval_ratio: float, test_ratio: float, seed: int) -> tuple[Dataset, Dataset | None, Dataset | None]:
    dataset = Dataset.from_list(rows)
    if len(dataset) < 10:
        return dataset, None, None

    test_size = max(1, int(len(dataset) * test_ratio)) if test_ratio > 0 else 0
    eval_size = max(1, int(len(dataset) * eval_ratio)) if eval_ratio > 0 else 0

    if test_size == 0 and eval_size == 0:
        return dataset, None, None

    train_pool = dataset
    test_dataset = None
    if test_size > 0:
        split = train_pool.train_test_split(test_size=test_size, seed=seed)
        train_pool = split["train"]
        test_dataset = split["test"]

    eval_dataset = None
    if eval_size > 0 and len(train_pool) > eval_size:
        split = train_pool.train_test_split(test_size=eval_size, seed=seed)
        train_pool = split["train"]
        eval_dataset = split["test"]

    return train_pool, eval_dataset, test_dataset


def build_training_arguments(args: argparse.Namespace, has_eval_dataset: bool, use_bf16: bool) -> TrainingArguments:
    kwargs: dict[str, Any] = {
        "output_dir": args.output_dir,
        "num_train_epochs": args.epochs,
        "per_device_train_batch_size": args.batch_size,
        "per_device_eval_batch_size": 1,
        "gradient_accumulation_steps": args.gradient_accumulation_steps,
        "learning_rate": args.learning_rate,
        "warmup_ratio": 0.03,
        "lr_scheduler_type": "cosine",
        "logging_steps": 1,
        "save_strategy": "epoch",
        "report_to": "none",
        "seed": args.seed,
        "bf16": use_bf16,
        "fp16": torch.cuda.is_available() and not use_bf16,
        "gradient_checkpointing": True,
        "optim": "paged_adamw_8bit" if torch.cuda.is_available() else "adamw_torch",
    }

    strategy_name = "epoch" if has_eval_dataset else "no"
    signature = inspect.signature(TrainingArguments)
    if "eval_strategy" in signature.parameters:
        kwargs["eval_strategy"] = strategy_name
    else:
        kwargs["evaluation_strategy"] = strategy_name

    return TrainingArguments(**kwargs)


def main() -> None:
    args = parse_args()
    dataset_path = Path(args.dataset)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    rows = read_jsonl(dataset_path)

    tokenizer = AutoTokenizer.from_pretrained(args.model_name, trust_remote_code=True)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token
    tokenizer.padding_side = "right"

    rendered_rows = [{"text": render_chat_text(tokenizer, row)} for row in rows]
    train_dataset, eval_dataset, test_dataset = split_dataset(
        rendered_rows,
        eval_ratio=args.eval_ratio,
        test_ratio=args.test_ratio,
        seed=args.seed,
    )

    if test_dataset is not None:
        test_output = output_dir / "heldout_test_samples.jsonl"
        with test_output.open("w", encoding="utf-8") as file:
            for row in test_dataset:
                file.write(json.dumps(row, ensure_ascii=False) + "\n")

    compute_dtype = torch.bfloat16 if torch.cuda.is_available() and torch.cuda.is_bf16_supported() else torch.float16
    use_bf16 = compute_dtype == torch.bfloat16

    quantization_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=compute_dtype,
        bnb_4bit_use_double_quant=True,
    )

    model = AutoModelForCausalLM.from_pretrained(
        args.model_name,
        quantization_config=quantization_config,
        device_map="auto",
        trust_remote_code=True,
    )
    model.config.use_cache = False
    model = prepare_model_for_kbit_training(model)

    lora_config = LoraConfig(
        r=16,
        lora_alpha=32,
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM",
        target_modules=[
            "q_proj",
            "k_proj",
            "v_proj",
            "o_proj",
            "gate_proj",
            "up_proj",
            "down_proj",
        ],
    )
    model = get_peft_model(model, lora_config)
    model.print_trainable_parameters()

    def tokenize(batch: dict[str, list[str]]) -> dict[str, Any]:
        return tokenizer(
            batch["text"],
            max_length=args.max_seq_length,
            truncation=True,
            padding=False,
        )

    tokenized_train = train_dataset.map(tokenize, batched=True, remove_columns=["text"])
    tokenized_eval = eval_dataset.map(tokenize, batched=True, remove_columns=["text"]) if eval_dataset is not None else None

    training_args = build_training_arguments(args, has_eval_dataset=tokenized_eval is not None, use_bf16=use_bf16)
    data_collator = DataCollatorForLanguageModeling(tokenizer=tokenizer, mlm=False)

    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=tokenized_train,
        eval_dataset=tokenized_eval,
        data_collator=data_collator,
    )
    trainer.train()

    trainer.save_model(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)

    metadata = {
        "baseModel": args.model_name,
        "adapterDir": str(output_dir),
        "dataset": str(dataset_path),
        "sampleCount": len(rows),
        "trainCount": len(train_dataset),
        "evalCount": len(eval_dataset) if eval_dataset is not None else 0,
        "testCount": len(test_dataset) if test_dataset is not None else 0,
        "maxSeqLength": args.max_seq_length,
        "epochs": args.epochs,
        "learningRate": args.learning_rate,
    }
    (output_dir / "training_metadata.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"Training complete. LoRA adapter saved to: {output_dir}")


if __name__ == "__main__":
    main()
