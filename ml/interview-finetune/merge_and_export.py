"""LoRA 어댑터를 베이스 모델에 병합해 GGUF 변환용으로 export. (자체모델 서빙 1단계)

  python merge_and_export.py --adapter out/interview-lora --out out/interview-merged

결과 out/interview-merged 를 llama.cpp 의 convert_hf_to_gguf.py 로 GGUF 변환한다.
tokenizer 도 함께 저장하므로(GGUF 변환에 필요) 추가 작업이 없다 — TRAINING.md 인라인 명령의 함정 제거.
VRAM ~6GB 사용(3B). 공유 GPU 면 nvidia-smi 로 여유 확인 후 실행. VRAM 경합 시 --cpu(느리지만 VRAM 0).
"""
import argparse

import torch
from peft import AutoPeftModelForCausalLM
from transformers import AutoTokenizer


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--adapter", default="out/interview-lora")
    p.add_argument("--out", default="out/interview-merged")
    p.add_argument("--cpu", action="store_true", help="GPU 대신 CPU 로 병합(VRAM 0, 느림)")
    args = p.parse_args()

    device = "cpu" if args.cpu else "auto"
    print(f"어댑터 로드: {args.adapter} (device={device})")
    # test_infer.py 와 동일 시그니처(dtype=) — 4090 환경 transformers 5.x 기준.
    model = AutoPeftModelForCausalLM.from_pretrained(
        args.adapter, dtype=torch.bfloat16, device_map=device)

    print("LoRA 병합 중...")
    merged = model.merge_and_unload()
    merged.save_pretrained(args.out, safe_serialization=True)

    # GGUF 변환은 tokenizer 파일(tokenizer.json 등)도 필요하므로 함께 저장한다.
    AutoTokenizer.from_pretrained(args.adapter).save_pretrained(args.out)
    print(f"병합 완료 → {args.out}")
    print(f"다음: python llama.cpp/convert_hf_to_gguf.py {args.out} "
          f"--outfile interview-3b.gguf --outtype q4_k_m")


if __name__ == "__main__":
    main()
