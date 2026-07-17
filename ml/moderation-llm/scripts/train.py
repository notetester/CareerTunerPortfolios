# Unsloth는 반드시 다른 라이브러리보다 먼저 import
from unsloth import FastModel, is_bfloat16_supported

import gc
import os
import torch
from datasets import load_dataset
from transformers import TrainerCallback, TrainingArguments
from trl import SFTTrainer

# ---------------------------------------------------------------------------
# 0. 경로 설정 (모듈 루트 기준)
# ---------------------------------------------------------------------------
BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_PATH = os.path.join(BASE, "data", "processed", "train.jsonl")  # 15k 균형 데이터
OUTPUT_DIR = os.path.join(BASE, "out", "adapter")
MAX_SEQ_LENGTH = 1024


# ---------------------------------------------------------------------------
# 메모리 관리 콜백
# ---------------------------------------------------------------------------
class MemoryCleanupCallback(TrainerCallback):
    def on_log(self, args, state, control, **kwargs):
        gc.collect()
        torch.cuda.empty_cache()

    def on_step_end(self, args, state, control, **kwargs):
        if state.global_step % 50 == 0:
            allocated = torch.cuda.memory_allocated() / 1024**3
            reserved = torch.cuda.memory_reserved() / 1024**3
            print(f"  [step {state.global_step}] allocated={allocated:.2f}GB reserved={reserved:.2f}GB")


def main():
    # -----------------------------------------------------------------------
    # 1. 모델 로드
    #    gemma-4는 멀티모달(텍스트+비전+오디오)이라 공식적으로 FastModel로 로드.
    #    (FastLanguageModel은 순수 언어모델 전용 - 이게 초기 시도의 근본 원인이었음)
    # -----------------------------------------------------------------------
    print("=== 모델 로드 중 ===")
    model, tokenizer = FastModel.from_pretrained(
        model_name="unsloth/gemma-4-E4B-it-unsloth-bnb-4bit",
        dtype=None,
        max_seq_length=MAX_SEQ_LENGTH,
        load_in_4bit=True,
        full_finetuning=False,
    )

    # gemma-4는 멀티모달이라 tokenizer가 실제로는 이미지 처리까지 포함된
    # 통합 프로세서임. trl의 콜레이터는 이 프로세서가 있으면 images 값과
    # 무관하게 무조건 이미지 처리 파이프라인을 호출해서 계속 에러가 났음.
    # -> 순수 텍스트 토크나이저만 뽑아서 트레이너에 넘겨 이 호출 자체를 우회.
    if hasattr(tokenizer, "tokenizer"):
        print("  (프로세서에서 순수 텍스트 토크나이저 추출)")
        text_tokenizer = tokenizer.tokenizer
    else:
        text_tokenizer = tokenizer
    # 공식 문서 권장 패턴 그대로 - 이게 텍스트 전용 파인튜닝의 정공법.
    model = FastModel.get_peft_model(
        model,
        finetune_vision_layers=False,      # 텍스트 전용이므로 비전 레이어 학습 안 함
        finetune_language_layers=True,
        finetune_attention_modules=True,
        finetune_mlp_modules=True,
        r=16,
        lora_alpha=32,
        lora_dropout=0,
        bias="none",
        random_state=3407,
        use_gradient_checkpointing="unsloth",  # E4B KV 공유 구조 대응, 필수
    )

    # -----------------------------------------------------------------------
    # 2. 데이터 로드 및 포맷팅
    # -----------------------------------------------------------------------
    print("=== 데이터 로드 중 ===")
    dataset = load_dataset("json", data_files=DATA_PATH, split="train")

    def formatting_func(example):
        messages = [
            {"role": "system", "content": example["instruction"]},
            {"role": "user", "content": example["input"]},
            {"role": "assistant", "content": example["output"]},
        ]
        text = text_tokenizer.apply_chat_template(
            messages, tokenize=False, add_generation_prompt=False
        )
        return {"text": text}

    dataset = dataset.map(formatting_func, num_proc=2)
    print(f"  총 {len(dataset)}건 로드 완료")

    # -----------------------------------------------------------------------
    # 3. 학습 설정
    # -----------------------------------------------------------------------
    trainer = SFTTrainer(
        model=model,
        tokenizer=text_tokenizer,
        train_dataset=dataset,
        dataset_text_field="text",
        max_seq_length=MAX_SEQ_LENGTH,
        dataset_num_proc=2,
        packing=False,
        args=TrainingArguments(
            per_device_train_batch_size=1,
            gradient_accumulation_steps=32,
            warmup_steps=50,
            num_train_epochs=1,
            learning_rate=2e-4,
            fp16=not is_bfloat16_supported(),
            bf16=is_bfloat16_supported(),
            logging_steps=10,
            optim="adamw_8bit",
            weight_decay=0.001,
            lr_scheduler_type="linear",
            seed=3407,
            output_dir=OUTPUT_DIR,
            # 중간 체크포인트 저장(save_steps)은 이 환경에서 pickling 에러로
            # 학습을 중단시킴 -> 저장은 학습 종료 후 1회만 수행.
            # (첫 run이 step 300 저장 직후 죽었고, 그 checkpoint-300이
            #  careertuner-mod 태그의 원본이다. 완주본은 careertuner-mod-full.)
            save_strategy="no",
            report_to="none",
            remove_unused_columns=False,
        ),
        callbacks=[MemoryCleanupCallback()],
    )

    # -----------------------------------------------------------------------
    # 4. 학습 실행
    # -----------------------------------------------------------------------
    print("=== 학습 시작 ===")
    print(f"GPU: {torch.cuda.get_device_name(0)}")

    # Unsloth의 fused cross-entropy 계산이 시작 직후 순간의 "자유 메모리"를
    # 측정해서 청크 크기를 정하는데, 모델/LoRA 로드 직후엔 예약된 메모리 때문에
    # 이 값이 0으로 측정되어 크래시나는 공식 버그(target_gb is zero, #3827)가 있음.
    # -> 학습 시작 직전 강제로 캐시를 비워서 이 순간의 측정치를 정상화.
    gc.collect()
    torch.cuda.empty_cache()
    print(f"초기 VRAM: {torch.cuda.memory_reserved()/1024**3:.2f}GB")

    trainer_stats = trainer.train()

    print("=== 학습 완료 ===")
    print(f"최종 손실: {trainer_stats.training_loss:.4f}")

    # -----------------------------------------------------------------------
    # 5. 어댑터 저장
    # -----------------------------------------------------------------------
    model.save_pretrained(OUTPUT_DIR)
    text_tokenizer.save_pretrained(OUTPUT_DIR)
    print(f"어댑터 저장 완료: {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
