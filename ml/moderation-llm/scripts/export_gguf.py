# train.py를 돌린 것과 같은 환경에서 실행
# 초기 시도(v1) 실패 원인 2개 수정:
#  (1) PeftModel.from_pretrained로 씌우면 Unsloth가 PEFT로 인식 못해 merge 안 됨
#      -> FastModel.from_pretrained에 어댑터 경로를 직접 넘겨 네이티브 로드
#  (2) 4bit 상태로 gguf 저장 시 Transformers 5.13 revert_weight_conversion 버그
#      -> 16bit로 merge 후 변환
from unsloth import FastModel

import os

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ADAPTER    = os.path.join(BASE, "out", "adapter")        # train.py 산출물
OUT_MERGED = os.path.join(BASE, "out", "merged_16bit")
OUT_GGUF   = os.path.join(BASE, "out", "gguf")

print("=== 1. 어댑터를 Unsloth 네이티브로 로드 (베이스+어댑터 함께) ===")
# model_name에 어댑터 폴더를 직접 지정하면 Unsloth가 adapter_config.json을 읽어
# 베이스(gemma-4-E4B-it)를 자동으로 당겨와 어댑터까지 붙인 상태로 로드함.
# load_in_4bit=False -> merge/변환을 위해 16bit로.
model, tokenizer = FastModel.from_pretrained(
    model_name = ADAPTER,
    max_seq_length = 1024,
    load_in_4bit = False,
    full_finetuning = False,
)

print("=== 2. 16bit merge 저장 (GGUF 변환 실패 대비 안전판) ===")
# 먼저 merged 16bit를 남겨둠. 이게 있으면 gguf 변환이 실패해도
# 이 폴더로 다른 경로(길 B: Ollama 직접 등록/외부 llama.cpp)로 갈 수 있음.
model.save_pretrained_merged(
    OUT_MERGED,
    tokenizer,
    save_method = "merged_16bit",
)
print(f"   merged 16bit 저장 완료: {OUT_MERGED}")

print("=== 3. GGUF Q4_K_M 변환 시도 ===")
try:
    model.save_pretrained_gguf(
        OUT_GGUF,
        tokenizer,
        quantization_method = "q4_k_m",
    )
    print(f"=== 완료: {OUT_GGUF} 에 gguf 생성 ===")
except Exception as e:
    print("!!! GGUF 변환 실패 (llama.cpp 빌드 문제일 가능성 큼) !!!")
    print(f"에러: {e}")
    print(f">>> 하지만 {OUT_MERGED} 의 merged 16bit는 저장됨.")
    print(">>> 이 폴더로 길 B(외부 llama.cpp 변환)로 진행하면 됨.")
