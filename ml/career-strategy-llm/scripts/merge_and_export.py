"""LoRA 어댑터를 베이스 모델에 병합해 GGUF 변환용으로 export. (자체모델 서빙 1단계)
(D의 ml/interview-finetune/merge_and_export.py 와 동일 로직, C 경로/안내만 교체)

  python merge_and_export.py --adapter out/career-strategy-lora-3b --out out/career-strategy-merged-3b

결과 out/career-strategy-merged-3b 를 llama.cpp 의 convert_hf_to_gguf.py 로 GGUF 변환한다.
tokenizer 도 함께 저장하므로(GGUF 변환에 필요) 추가 작업이 없다.
VRAM ~6GB 사용(3B). 공유 GPU 면 nvidia-smi 로 여유 확인 후 실행. VRAM 경합 시 --cpu(느리지만 VRAM 0).

GGUF 변환/양자화 상세는 reports/00_runbook_4090.md 참고
(convert_hf_to_gguf.py 는 f16/bf16/q8_0 직접 출력; Q4_K_M 은 llama-quantize 바이너리 필요).
"""
import argparse

import torch
from peft import AutoPeftModelForCausalLM
from transformers import AutoTokenizer


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--adapter", default="out/career-strategy-lora-3b")
    p.add_argument("--out", default="out/career-strategy-merged-3b")
    p.add_argument("--cpu", action="store_true", help="GPU 대신 CPU 로 병합(VRAM 0, 느림)")
    args = p.parse_args()

    device = "cpu" if args.cpu else "auto"
    print(f"어댑터 로드: {args.adapter} (device={device})")
    model = AutoPeftModelForCausalLM.from_pretrained(
        args.adapter, dtype=torch.bfloat16, device_map=device)

    print("LoRA 병합 중...")
    merged = model.merge_and_unload()
    merged.save_pretrained(args.out, safe_serialization=True)

    # GGUF 변환은 tokenizer 파일(tokenizer.json 등)도 필요하므로 함께 저장한다.
    AutoTokenizer.from_pretrained(args.adapter).save_pretrained(args.out)
    print(f"병합 완료 → {args.out}")
    print("다음(runbook 참고):")
    print(f"  python llama.cpp/convert_hf_to_gguf.py {args.out} "
          f"--outfile career-strategy-3b-f16.gguf --outtype f16")
    print("  llama-quantize career-strategy-3b-f16.gguf career-strategy-3b-q4_k_m.gguf Q4_K_M")


if __name__ == "__main__":
    main()
