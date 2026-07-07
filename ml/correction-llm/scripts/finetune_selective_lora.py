"""Continue LoRA training while applying loss only to one assistant JSON key."""

from __future__ import annotations

import argparse
import json

import torch
from datasets import DatasetDict, load_dataset
from peft import AutoPeftModelForCausalLM
from transformers import (
    AutoTokenizer,
    BitsAndBytesConfig,
    DataCollatorForSeq2Seq,
    Trainer,
    TrainingArguments,
)


def json_key_span(content: str, key: str) -> tuple[int, int]:
    marker = json.dumps(key, ensure_ascii=False) + ":"
    marker_start = content.rfind(marker)
    if marker_start < 0:
        raise ValueError(f"assistant output is missing JSON key: {key}")
    value_start = marker_start + len(marker)
    _, consumed = json.JSONDecoder().raw_decode(content[value_start:])
    return marker_start, value_start + consumed


def build_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--resume-adapter", required=True)
    parser.add_argument("--train", required=True)
    parser.add_argument("--eval", default=None)
    parser.add_argument("--output", required=True)
    parser.add_argument("--loss-json-key", default="risk_flags")
    parser.add_argument("--epochs", type=float, default=2.0)
    parser.add_argument("--batch-size", type=int, default=1)
    parser.add_argument("--grad-accum", type=int, default=8)
    parser.add_argument("--lr", type=float, default=1e-5)
    parser.add_argument("--max-seq-len", type=int, default=3000)
    return parser.parse_args()


def tokenize_row(row: dict, tokenizer, key: str, max_length: int) -> dict:
    messages = row["messages"]
    assistant_content = messages[-1]["content"]
    relative_start, relative_end = json_key_span(assistant_content, key)
    full_text = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=False)
    content_start = full_text.rfind(assistant_content)
    if content_start < 0:
        raise ValueError("assistant content was not found in the rendered chat template")
    selected_start = content_start + relative_start
    selected_end = content_start + relative_end

    encoded = tokenizer(
        full_text,
        add_special_tokens=False,
        truncation=True,
        max_length=max_length,
        return_offsets_mapping=True,
    )
    labels = [-100] * len(encoded["input_ids"])
    selected_count = 0
    for index, (start, end) in enumerate(encoded.pop("offset_mapping")):
        if start < selected_end and end > selected_start:
            labels[index] = encoded["input_ids"][index]
            selected_count += 1
    encoded["labels"] = labels
    encoded["selected_token_count"] = selected_count
    return encoded


def prepare_dataset(dataset: DatasetDict, tokenizer, key: str, max_length: int) -> DatasetDict:
    prepared = DatasetDict()
    for split, rows in dataset.items():
        tokenized = rows.map(
            lambda row: tokenize_row(row, tokenizer, key, max_length),
            remove_columns=rows.column_names,
            desc=f"Tokenizing selective {split} dataset",
        )
        before = len(tokenized)
        tokenized = tokenized.filter(
            lambda row: row["selected_token_count"] > 0,
            desc=f"Filtering truncated selective {split} rows",
        )
        tokenized = tokenized.remove_columns(["selected_token_count"])
        print(f"{split}: retained {len(tokenized)}/{before} rows with visible {key} tokens")
        if not tokenized:
            raise ValueError(f"no {split} rows retain the selected JSON key within max length")
        prepared[split] = tokenized
    return prepared


def main() -> None:
    args = build_args()
    tokenizer = AutoTokenizer.from_pretrained(args.resume_adapter)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    quant = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.bfloat16,
        bnb_4bit_use_double_quant=True,
    )
    model = AutoPeftModelForCausalLM.from_pretrained(
        args.resume_adapter,
        is_trainable=True,
        quantization_config=quant,
        torch_dtype=torch.bfloat16,
        device_map="auto",
    )
    model.config.use_cache = False
    model.enable_input_require_grads()

    data_files = {"train": args.train}
    if args.eval:
        data_files["eval"] = args.eval
    dataset = prepare_dataset(
        load_dataset("json", data_files=data_files),
        tokenizer,
        args.loss_json_key,
        args.max_seq_len,
    )

    training_args = TrainingArguments(
        output_dir=args.output,
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        per_device_eval_batch_size=args.batch_size,
        gradient_accumulation_steps=args.grad_accum,
        learning_rate=args.lr,
        logging_steps=5,
        save_strategy="epoch",
        eval_strategy="no",
        gradient_checkpointing=True,
        gradient_checkpointing_kwargs={"use_reentrant": False},
        bf16=True,
        report_to="none",
    )
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=dataset["train"],
        eval_dataset=dataset.get("eval"),
        data_collator=DataCollatorForSeq2Seq(
            tokenizer=tokenizer,
            padding=True,
            label_pad_token_id=-100,
        ),
    )
    trainer.train()
    trainer.save_model(args.output)
    tokenizer.save_pretrained(args.output)
    print(f"Selective LoRA adapter saved: {args.output}")
    if args.eval:
        print(f"Final selective evaluation: {trainer.evaluate()}")


if __name__ == "__main__":
    main()
