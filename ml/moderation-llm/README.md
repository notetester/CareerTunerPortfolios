# moderation-llm — F 커뮤니티 게시글 검열 자체 모델

CareerTuner F 영역(커뮤니티) 게시글·댓글 검열의 자체 모델 학습 파이프라인이다.
게시글 제목·본문을 입력받아 JSON 한 개로 판정한다.

```json
{"toxic": true, "category": "abuse", "confidence": 0.9}
```

**목표**: abuse(특정 대상을 향한 욕설·인신공격·혐오)와 normal의 이진 구분을 로컬 모델로 안정화.
특히 "비속어가 섞여 있어도 대상이 없으면 normal"(감탄·강조·자기표현)이라는 취준 커뮤니티
맥락 판정이 핵심이다.

**비목표**:

- spam / ad 분류 학습 — 서빙 스키마에는 존재하지만 학습 데이터가 없어 시스템 프롬프트
  zero-shot에 의존한다. 학습된 능력으로 표현하지 않는다.
- confidence 캘리브레이션 — 학습 데이터에서 0.9로 고정한 비정보 필드다. 프로덕션 스키마
  안정성을 위해 유지할 뿐이므로 **임계값으로 사용하지 말 것**.

## 현재 서비스 기준

| 항목 | 현재 값 |
| --- | --- |
| 베이스 모델 | `unsloth/gemma-4-E4B-it-unsloth-bnb-4bit` (4bit) |
| 학습 방식 | QLoRA (r=16, α=32, vision layer 동결), Unsloth + trl SFTTrainer |
| Ollama 태그 | `careertuner-mod` (step 300 중단본, 현 백엔드 기본값) / `careertuner-mod-full` (469 step = 1 epoch 완주본) |
| 백엔드 설정 | `ai.ollama.moderation-model` (env `AI_OLLAMA_MODERATION_MODEL`), 기본 `careertuner-mod` |
| 폴백 | 자체(Ollama) → Claude → OpenAI → mock (`community/moderation/ModerationLlmGateway`) |
| 출력 제약 | Modelfile `num_predict=64` — 짧은 판정 JSON 전용, 장문 생성에 재사용 금지 |

> **라이선스:** 베이스는 Gemma 계열이며 Gemma Terms of Use를 따른다(Apache 2.0 아님).
> 학습 데이터 4종은 각 저장소의 라이선스(아래 표)를 따르며, 원본은 커밋하지 않는다.

## 폴더

```text
ml/moderation-llm/
  README.md
  requirements.txt
  Modelfile          Ollama 등록용 (시스템 프롬프트 + 판정용 파라미터 고정)
  scripts/
    prepare_data.py  공개 데이터셋 4종 clone → dedup → 균형 subsample → train/eval 생성
    train.py         Unsloth QLoRA 학습 (RTX 4090, 1 epoch ≈ 2h)
    export_gguf.py   어댑터 → 16bit merge → GGUF Q4_K_M
    evaluate.py      스톡 gemma4 vs 파인튜닝 태그 정확도 비교 (Ollama API)
  data/              (gitignore) raw clone과 생성 jsonl — prepare_data.py로 재생성
  out/               (gitignore) 어댑터·merged·GGUF 산출물
```

## 데이터 출처와 개인정보 경계

공개 한국어 혐오표현 데이터셋 4종만 사용한다. **CareerTuner 서비스 사용자 데이터와
개인정보는 학습에 사용하지 않았다.** 원본·생성 jsonl은 커밋하지 않으며, seed 고정(42)으로
`prepare_data.py` 재실행 시 동일하게 재현된다.

| 데이터셋 | 출처 | 라벨 사용 방식 |
| --- | --- | --- |
| Curse-detection-data | [2runo/Curse-detection-data](https://github.com/2runo/Curse-detection-data) | 이진 라벨 그대로 |
| K-UnSmile | [smilegate-ai/korean_unsmile_dataset](https://github.com/smilegate-ai/korean_unsmile_dataset) | 혐오 9개 컬럼 중 하나라도 1이면 abuse |
| BEEP! | [kocohub/korean-hate-speech](https://github.com/kocohub/korean-hate-speech) | hate/offensive → abuse |
| K-MHaS | [adlnlp/K-MHaS](https://github.com/adlnlp/K-MHaS) | 라벨 {8}(=해당없음)만 normal |

### 데이터 설계 근거 (v1 → v2 개정)

- dedup 후 풀 ~120k (abuse 50.2% / normal 49.8%) → **15k 균형 subsample**.
  시스템 프롬프트가 ~850토큰으로 시퀀스의 대부분을 차지해 같은 규칙을 반복 학습하는
  구조라, 형식 정렬 SFT엔 10~15k면 충분하다. 52k×3epoch는 20h+ 소요 & 과학습.
- **held-out eval 1.5k** — train과 텍스트 기준 disjoint. loss가 아니라 실제 정확도로
  튜닝을 판단하기 위함.
- 전체 시퀀스 p99 ≈ 1,100토큰 실측 → **seq_length=1024 유지**. 512로 낮추면 시스템
  프롬프트가 잘린다.
- train/serve 토큰 일치가 문구 매끄러움보다 중요 — 학습 데이터의 시스템 프롬프트와
  Modelfile SYSTEM, 백엔드 요청 프롬프트를 동일 문구로 유지한다.

## 재현 절차

RTX 4090 (24GB) 기준. `requirements.txt`의 실측 버전 조합을 사용한다.

```powershell
cd ml/moderation-llm

# 1. 데이터 준비 (공개 데이터셋 clone → train 15k / eval 1.5k 생성)
python scripts/prepare_data.py

# 2. QLoRA 학습 (469 step ≈ 2h @ 4090, 어댑터는 out/adapter)
python scripts/train.py

# 3. 16bit merge + GGUF Q4_K_M 변환
python scripts/export_gguf.py

# 4. Ollama 등록 (생성된 gguf 옆에 Modelfile 복사 후)
copy Modelfile out\gguf\
cd out\gguf
ollama create careertuner-mod-full -f Modelfile

# 5. 스톡 대비 성능 검증 (Ollama 실행 중이어야 함)
cd ..\..
python scripts/evaluate.py --n 300   # 샘플 경향 확인
python scripts/evaluate.py           # 전체 1,500건
```

## 평가 결과

held-out eval 앞 300건(abuse 150 / normal 150), 로컬 Ollama(RTX 4090), 동일 시스템
프롬프트·temperature 0.1 조건에서 스톡 `gemma4`와 파인튜닝 두 태그를 비교했다.
(측정일 2026-07-17, `evaluate.py --n 300`)

| 모델 | 정확도 | abuse 재현율 | abuse 정밀도 | 파싱 실패 | 혼동행렬 (TP/FP/FN/TN) |
| --- | --- | --- | --- | --- | --- |
| `gemma4` (스톡) | 68.7% | 93.3% | 62.5% | 0% | 140/84/10/66 |
| `careertuner-mod` (step 300) | **78.0%** | 91.3% | **72.1%** | 0% | 137/53/13/97 |
| `careertuner-mod-full` (1 epoch) | 71.3% | **96.7%** | 64.2% | 0% | 145/81/5/69 |

- 재현율은 검열 누락(실제 abuse를 놓침), 정밀도는 오검열(normal을 abuse로 판정) 지표다.
  커뮤니티 운영상 오검열(FP)이 사용자 경험을 직접 해치므로 정밀도 하락 없는 재현율 개선을
  기준으로 본다.
- **해석**: 스톡의 문제는 과잉 검열이다(FP 84건 — 비속어 섞인 감탄·강조를 abuse로 판정).
  step 300본은 FP를 84→53으로 줄여 정확도 +9.3%p를 얻었다. 완주본(469 step)은 재현율은
  최고지만 FP가 81건으로 스톡 수준까지 되돌아가, 위 기준(오검열 우선)에서는 step 300본이
  낫다. **현 백엔드 기본 태그 `careertuner-mod`(step 300)를 유지한다.**

## 트러블슈팅 기록 (실측)

학습 과정에서 실제로 막혔던 지점과 해결책. 동일 스택 재현 시 그대로 유효하다.

1. **gemma-4 E4B는 멀티모달** — `FastLanguageModel`(순수 LM 전용)로 로드하면 실패한다.
   `FastModel` + `finetune_vision_layers=False`가 텍스트 전용 파인튜닝의 정공법.
2. **통합 프로세서 vs trl 콜레이터** — gemma-4의 tokenizer는 이미지 처리까지 포함한
   프로세서라, trl 콜레이터가 images 값과 무관하게 이미지 파이프라인을 호출해 에러.
   → 프로세서에서 순수 텍스트 토크나이저(`tokenizer.tokenizer`)만 추출해 트레이너에 전달.
3. **Unsloth fused CE `target_gb is zero` 크래시 (#3827)** — 학습 시작 순간의 자유 VRAM으로
   청크 크기를 정하는데 모델 로드 직후엔 0으로 측정됨. → 학습 직전 `gc.collect()` +
   `torch.cuda.empty_cache()`로 정상화.
4. **`save_steps` 중간저장 pickling 에러** — 첫 run이 step 300 체크포인트 저장 직후 중단됐다.
   이 checkpoint-300을 GGUF로 변환해 등록한 것이 `careertuner-mod` 태그다. 재실행은
   `save_strategy="no"`(종료 후 1회 저장)로 469 step을 완주했고 이것이 `careertuner-mod-full`.
5. **어댑터 merge 실패** — `PeftModel.from_pretrained`로 씌우면 Unsloth가 PEFT로 인식하지
   못해 merge가 안 된다. → `FastModel.from_pretrained(model_name=<어댑터 경로>)` 네이티브 로드.
6. **4bit 상태 GGUF 변환 버그** — Transformers 5.13 `revert_weight_conversion` 문제로 실패.
   → `load_in_4bit=False`로 16bit merge를 먼저 저장한 뒤 GGUF Q4_K_M 변환.

## 알려진 한계

- **spam / ad 미학습** — 스키마에만 존재하고 학습 데이터가 없다. 프롬프트 zero-shot 성능에
  의존하며 별도 검증되지 않았다.
- **confidence는 고정 0.9 학습** — 확신도 정보가 없다. 백엔드에서 임계값 판단에 쓰지 말 것.
- **in-distribution eval만 수행** — eval.jsonl은 학습과 같은 공개 데이터셋 분포의 held-out이다.
  실제 CareerTuner 커뮤니티 게시글 200~300건을 수동 라벨링한 `test_hard.jsonl` 검증은 미완(TODO).
- **단문 댓글 위주 분포** — 원천 데이터가 댓글 단문 중심이라 장문 게시글에 대한 성능은
  검증되지 않았다.
- `careertuner-mod`(백엔드 기본값)는 1 epoch 완주본이 아니라 step 300 중단본이다. 다만
  위 평가에서 완주본보다 정확도·정밀도가 높아 기본 태그로 유지한다. 300건 샘플 결과이므로
  태그 교체 판단 전에는 전체 1,500건 평가로 재확인할 것.

**마지막 검증**: 2026-07-17 — 로컬 Ollama 태그(`careertuner-mod`, `careertuner-mod-full`,
2026-07-08~09 빌드) 대상 `evaluate.py --n 300` 실측. 학습 환경: Windows 11 + RTX 4090,
unsloth 2026.6.9 / torch 2.5.1+cu121 / transformers 5.5.0 / trl 0.8.6.
