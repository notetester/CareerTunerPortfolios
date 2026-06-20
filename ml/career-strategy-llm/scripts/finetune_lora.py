"""
C(커리어 전략) 자체 모델 LoRA 파인튜닝 (멀티태스크: C_FIT_EXPLAIN / C_STRATEGY / …).
(D의 ml/interview-finetune/finetune_lora.py 의 검증된 설정을 그대로 가져오고 도메인 기본값만 교체)

베이스(Phase 1 기본): Qwen2.5-3B-Instruct (한국어 양호, RTX 4090 24GB 에서 4bit+LoRA 여유).
7B 비교: --base-model Qwen/Qwen2.5-7B-Instruct 로 같은 데이터셋 재학습(reports/01_model_comparison_plan.md).
입력: prepare_data.py 가 만든 messages 포맷 JSONL (합성 데이터 — assemble_dataset.py 산출).
출력: LoRA 어댑터(output_dir). merge_and_export.py → GGUF(llama.cpp) → 로컬 Ollama 서빙.

목적은 최고 성능이 아니라 "우리가 직접 학습해 붙였다"는 증거 + C 뉴로-심볼릭 설명 품질 확보다.

사용 (공유 RTX 4090):
    pip install -r requirements.txt
    # 3B (Phase 1 기본)
    python finetune_lora.py --train data/train.jsonl --eval data/val.jsonl --output out/career-strategy-lora-3b
    # 7B (Phase 2~3 비교)
    python finetune_lora.py --base-model Qwen/Qwen2.5-7B-Instruct \
        --train data/train.jsonl --eval data/val.jsonl --output out/career-strategy-lora-7b
"""

import argparse

import torch
from datasets import load_dataset
from peft import LoraConfig
from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig
from trl import SFTConfig, SFTTrainer

# Phase 1 기본 = 3B. 7B 비교는 --base-model 로 교체(영구 확정 아님, 비교 후 결정).
DEFAULT_BASE = "Qwen/Qwen2.5-3B-Instruct"


def build_args():
    p = argparse.ArgumentParser()
    p.add_argument("--base-model", default=DEFAULT_BASE,
                   help="Phase 1=Qwen/Qwen2.5-3B-Instruct, 비교=Qwen/Qwen2.5-7B-Instruct")
    p.add_argument("--train", required=True)
    p.add_argument("--eval", default=None)
    p.add_argument("--output", default="out/career-strategy-lora-3b")
    p.add_argument("--epochs", type=float, default=3.0)
    p.add_argument("--batch-size", type=int, default=1)
    p.add_argument("--grad-accum", type=int, default=8)
    p.add_argument("--lr", type=float, default=2e-4)
    p.add_argument("--max-seq-len", type=int, default=2048)
    p.add_argument("--no-4bit", action="store_true", help="4bit 양자화 비활성(VRAM 충분할 때)")
    return p.parse_args()


def main():
    args = build_args()

    tokenizer = AutoTokenizer.from_pretrained(args.base_model)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    quant = None
    if not args.no_4bit:
        quant = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_compute_dtype=torch.bfloat16,
            bnb_4bit_use_double_quant=True,
        )

    model = AutoModelForCausalLM.from_pretrained(
        args.base_model,
        quantization_config=quant,
        torch_dtype=torch.bfloat16,
        device_map="auto",
    )

    # 데이터: messages 포맷을 그대로 로드. SFTTrainer 가 채팅 템플릿을 적용한다.
    data_files = {"train": args.train}
    if args.eval:
        data_files["eval"] = args.eval
    dataset = load_dataset("json", data_files=data_files)

    lora = LoraConfig(
        r=16,
        lora_alpha=32,
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM",
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
    )

    sft_config = SFTConfig(
        output_dir=args.output,
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        gradient_accumulation_steps=args.grad_accum,
        learning_rate=args.lr,
        max_length=args.max_seq_len,
        logging_steps=10,
        save_strategy="epoch",
        eval_strategy="epoch" if args.eval else "no",
        bf16=True,
        report_to="none",
        packing=False,
    )

    trainer = SFTTrainer(
        model=model,
        args=sft_config,
        train_dataset=dataset["train"],
        eval_dataset=dataset.get("eval"),
        peft_config=lora,
        processing_class=tokenizer,
    )

    trainer.train()
    trainer.save_model(args.output)
    tokenizer.save_pretrained(args.output)
    print(f"LoRA 어댑터 저장 완료: {args.output}")
    print("다음: merge_and_export.py → GGUF 변환 → Ollama (reports/00_runbook_4090.md)")


if __name__ == "__main__":
    main()
