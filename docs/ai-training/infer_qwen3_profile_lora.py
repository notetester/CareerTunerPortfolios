"""
학습된 LoRA 어댑터를 사용해 프로필 AI 결과를 테스트하는 스크립트.

기본 동작:
- 학습 데이터 첫 번째 샘플의 system/user 메시지를 입력으로 사용합니다.
- 모델이 반환한 텍스트에서 JSON 객체를 추출해 출력합니다.

사용 예:
python docs/ai-training/infer_qwen3_profile_lora.py ^
  --adapter-dir docs/ai-training/output/qwen3-profile-lora
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig


DEFAULT_MODEL_NAME = "Qwen/Qwen3-4B-Instruct-2507"
DEFAULT_ADAPTER_DIR = "docs/ai-training/output/qwen3-profile-lora"
DEFAULT_DATASET_PATH = "docs/ai-training/profile_ai_training_samples_500.jsonl"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run CareerTuner profile AI LoRA inference.")
    parser.add_argument("--model-name", default=DEFAULT_MODEL_NAME)
    parser.add_argument("--adapter-dir", default=DEFAULT_ADAPTER_DIR)
    parser.add_argument("--dataset", default=DEFAULT_DATASET_PATH)
    parser.add_argument("--sample-index", type=int, default=0)
    parser.add_argument("--max-new-tokens", type=int, default=900)
    parser.add_argument("--temperature", type=float, default=0.2)
    return parser.parse_args()


def load_sample_messages(dataset_path: Path, sample_index: int) -> list[dict[str, str]]:
    lines = [line for line in dataset_path.read_text(encoding="utf-8").splitlines() if line.strip()]
    if sample_index < 0 or sample_index >= len(lines):
        raise IndexError(f"sample-index must be between 0 and {len(lines) - 1}")

    row = json.loads(lines[sample_index])
    messages = row["messages"]
    return [
        {"role": "system", "content": messages[0]["content"]},
        {"role": "user", "content": messages[1]["content"]},
    ]


def extract_json(text: str) -> dict[str, Any]:
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end < start:
        raise ValueError(f"Model output does not contain a JSON object:\n{text}")
    return json.loads(text[start : end + 1])


def main() -> None:
    args = parse_args()
    adapter_dir = Path(args.adapter_dir)
    if not adapter_dir.exists():
        raise FileNotFoundError(f"Adapter directory not found: {adapter_dir}")

    tokenizer = AutoTokenizer.from_pretrained(adapter_dir, trust_remote_code=True)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    compute_dtype = torch.bfloat16 if torch.cuda.is_available() and torch.cuda.is_bf16_supported() else torch.float16
    quantization_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=compute_dtype,
        bnb_4bit_use_double_quant=True,
    )

    base_model = AutoModelForCausalLM.from_pretrained(
        args.model_name,
        quantization_config=quantization_config,
        device_map="auto",
        trust_remote_code=True,
    )
    model = PeftModel.from_pretrained(base_model, adapter_dir)
    model.eval()

    messages = load_sample_messages(Path(args.dataset), args.sample_index)
    prompt = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
    inputs = tokenizer(prompt, return_tensors="pt").to(model.device)

    with torch.no_grad():
        output_ids = model.generate(
            **inputs,
            max_new_tokens=args.max_new_tokens,
            temperature=args.temperature,
            do_sample=args.temperature > 0,
            pad_token_id=tokenizer.eos_token_id,
        )

    generated_ids = output_ids[0][inputs["input_ids"].shape[-1] :]
    generated_text = tokenizer.decode(generated_ids, skip_special_tokens=True)
    parsed = extract_json(generated_text)

    print(json.dumps(parsed, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
