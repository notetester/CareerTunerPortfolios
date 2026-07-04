"""E correction model LoRA/QLoRA fine-tuning."""

from __future__ import annotations

import argparse

import torch
from datasets import load_dataset
from peft import AutoPeftModelForCausalLM, LoraConfig
from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig
from trl import SFTConfig, SFTTrainer

DEFAULT_BASE = "Qwen/Qwen2.5-3B-Instruct"


def build_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-model", default=DEFAULT_BASE)
    parser.add_argument(
        "--resume-adapter",
        default=None,
        help="Continue training an existing LoRA adapter instead of creating a new one.",
    )
    parser.add_argument("--train", required=True)
    parser.add_argument("--eval", default=None)
    parser.add_argument("--output", default="out/correction-lora-smoke-3b")
    parser.add_argument("--epochs", type=float, default=1.0)
    parser.add_argument("--batch-size", type=int, default=1)
    parser.add_argument("--grad-accum", type=int, default=8)
    parser.add_argument("--lr", type=float, default=2e-4)
    parser.add_argument("--max-seq-len", type=int, default=2048)
    parser.add_argument("--no-4bit", action="store_true", help="Disable 4bit quantization.")
    return parser.parse_args()


def main() -> None:
    args = build_args()

    tokenizer = AutoTokenizer.from_pretrained(args.resume_adapter or args.base_model)
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

    if args.resume_adapter:
        model = AutoPeftModelForCausalLM.from_pretrained(
            args.resume_adapter,
            is_trainable=True,
            quantization_config=quant,
            torch_dtype=torch.bfloat16,
            device_map="auto",
        )
        lora = None
    else:
        model = AutoModelForCausalLM.from_pretrained(
            args.base_model,
            quantization_config=quant,
            torch_dtype=torch.bfloat16,
            device_map="auto",
        )
        lora = LoraConfig(
            r=16,
            lora_alpha=32,
            lora_dropout=0.05,
            bias="none",
            task_type="CAUSAL_LM",
            target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
        )

    data_files = {"train": args.train}
    if args.eval:
        data_files["eval"] = args.eval
    dataset = load_dataset("json", data_files=data_files)

    sft_config = SFTConfig(
        output_dir=args.output,
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        gradient_accumulation_steps=args.grad_accum,
        learning_rate=args.lr,
        max_length=args.max_seq_len,
        logging_steps=5,
        save_strategy="epoch",
        eval_strategy="no",
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
    print(f"LoRA adapter saved: {args.output}")
    if args.eval:
        metrics = trainer.evaluate()
        print(f"Final evaluation: {metrics}")


if __name__ == "__main__":
    main()
