"""Merge an E correction LoRA adapter into its base model for GGUF export."""

from __future__ import annotations

import argparse

import torch
from peft import AutoPeftModelForCausalLM
from transformers import AutoTokenizer


def build_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--adapter", default="out/correction-unified-v2-3b")
    parser.add_argument("--out", default="out/correction-unified-v2-3b-merged")
    parser.add_argument("--cpu", action="store_true", help="Merge on CPU instead of GPU")
    return parser.parse_args()


def main() -> None:
    args = build_args()
    device_map = "cpu" if args.cpu else "auto"
    print(f"loading adapter: {args.adapter} (device_map={device_map})")
    model = AutoPeftModelForCausalLM.from_pretrained(
        args.adapter,
        dtype=torch.bfloat16,
        device_map=device_map,
    )
    print("merging LoRA adapter")
    merged = model.merge_and_unload()
    merged.save_pretrained(args.out, safe_serialization=True)
    AutoTokenizer.from_pretrained(args.adapter).save_pretrained(args.out)
    print(f"merged model saved: {args.out}")
    print("next:")
    print(
        f"  python llama.cpp/convert_hf_to_gguf.py {args.out} "
        "--outfile careertuner-e-correction-3b-unified-v2-f16.gguf --outtype f16"
    )
    print(
        "  llama-quantize careertuner-e-correction-3b-unified-v2-f16.gguf "
        "careertuner-e-correction-3b-unified-v2-q4_k_m.gguf Q4_K_M"
    )


if __name__ == "__main__":
    main()
