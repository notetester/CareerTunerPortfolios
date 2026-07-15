# CareerTuner E Correction Model Card

## 목적

`careertuner-e-correction-3b`는 E 담당 첨삭 영역의 통합 자체 모델이다. 하나의 모델이 `task_type`으로 아래 작업을 구분한다.

- `SELF_INTRO_CORRECTION`: 자기소개서 첨삭
- `INTERVIEW_ANSWER_CORRECTION`: 면접 답변 첨삭
- `RESUME_EXPRESSION_IMPROVEMENT`: 이력서 문장 개선
- `PORTFOLIO_DESCRIPTION_IMPROVEMENT`: 포트폴리오 설명 개선

모델의 핵심 목표는 문장을 더 그럴듯하게 만드는 것이 아니라, 원문과 제공된 `user_profile_facts`에 없는 경력, 기술, 수치, 성과를 추가하지 않는 원문 보존형 첨삭이다.

## 모델 정책

- Base model: `Qwen/Qwen2.5-3B-Instruct`
- Base license: Qwen Research License(비상업 연구·평가 중심, 상용 사용 전 별도 허가 필요)
- 현재 운영 태그: `careertuner-e-correction-3b:delivery-s-f16-20260708`
- 구성: 네 첨삭 task를 처리하는 Qwen2.5-3B LoRA 통합 모델 1개
- fallback: 동일 3B repair 1회 → Anthropic → OpenAI

E 첨삭도 팀 공통 Qwen2.5-3B 베이스를 사용한다. Qwen3-8B와 Qwen3-4B는 운영 모델, fallback, 신규 학습 후보에서 제외한다. 서버에 남아 있는 과거 8B 태그는 레거시 정리 대상이며 서비스 라우팅에 사용하지 않는다.

## 학습 데이터

- 전체 raw 데이터: 360개
- 학습 messages 데이터: 320개
- 평가 messages 데이터: 40개
- 별도 하드케이스 raw 평가: 20개
- 데이터 출처: GPT 생성 합성 데이터 + 로컬 스키마 검증
- 개인정보/실사용자 데이터: 포함하지 않음

최종 학습 파일:

- `data/train.seed360.hardfailfix.messages.jsonl`
- `data/val.seed360.mixed40.messages.jsonl`

주요 검증 파일:

- `data/raw.val.seed240.mixed.40.jsonl`
- `data/raw.hardcase.val.20.jsonl`

## 모델 산출물

- seed360 adapter: `out/correction-lora-seed360-hardfailfix-3b`
- unified-v2 adapter: `out/correction-unified-v2-3b`
- 현재 서비스 태그: `careertuner-e-correction-3b:delivery-s-f16-20260708`
- 후보 태그: `careertuner-e-correction-3b:<candidate>`(서비스 태그와 분리)

모델 가중치와 LoRA adapter는 용량이 크므로 본체 git에 포함하지 않는다. 로컬 산출물은 `out/` 아래에 두며 `.gitignore`로 제외한다.

단, 배포 정본 `delivery-s` 어댑터는 **최종 백업 예외**다. 어댑터 실물(`adapter_model.safetensors`, 57.2MB)과
학습·검증 datasets 51종이 `docs/ai-artifacts` 서브모듈에 백업돼 있어, 팀 공통 `Qwen/Qwen2.5-3B-Instruct` 베이스에
이 어댑터를 얹으면(재학습 없이 base+adapter 로드) 배포 정본 E 모델을 **정확 복원**할 수 있다. 위치는
`docs/ai-artifacts/results/2026-07-14-final-model-backup/adapters/e-correction-delivery-s/`다. 정리하면 이 어댑터는
**본체 git 미포함, 서브모듈 백업 존재**다.

## 출력 스키마

모델 출력은 설명문 없이 JSON 객체 하나만 반환한다.

```json
{
  "status": "ok",
  "task_type": "SELF_INTRO_CORRECTION",
  "corrected_text": "...",
  "summary": "...",
  "changes": [
    {
      "before": "...",
      "after": "...",
      "reason": "...",
      "evidence_source": "user_profile_facts"
    }
  ],
  "risk_flags": [],
  "preserved_meaning": true,
  "added_facts": [],
  "recommended_keywords": [],
  "confidence": 0.82
}
```

서버 연동 시 필수 검증:

- 필수 키 누락 여부
- extra key 차단
- `status == "ok"`
- `task_type` 일치
- `changes[].evidence_source` enum 검증
- `added_facts` 비어 있음 또는 근거 확인
- `confidence` 숫자 범위 검증
- `preserved_meaning` boolean 타입 검증

## 평가 결과

seed360 평가는 `scripts/test_infer.py`의 soft 모드를 사용했다. 아래 표는 delivery-s 확정 이전 후보 비교이며,
현재 서비스 태그의 raw 평가 산출물은 `docs/ai-artifacts/`에서 관리한다. 장문 후보는 직접 추론, repair 추론,
Ollama strict schema 추론을 모두 통과해야 서비스 태그 교체를 검토할 수 있다.

| 3B 후보 | 평가 | 결과 | 상태 |
| --- | --- | ---: | --- |
| seed360 | soft 전체 40개 | 39/40 | baseline |
| seed360 | soft 하드케이스 20개 | 19/20 | baseline |
| unified-v2 기본 adapter | task별 strict 4개 | 2/4 | 학습 파이프라인 검증용 |
| P1-v3 | 직접 추론 4개 | 1/4 | 배포 거부 |
| P1-v3 | 실패 3건 repair | 0/3 | 배포 거부 |
| P1-v3 | Ollama strict schema 4개 | 2/4 | 배포 거부 |

P1-v3는 train/validation ID 교집합 0건으로 구성했지만 장문 축약과 문단 손실을 해결하지 못했다.
`careertuner-e-correction-3b:p1-v3-candidate`는 서비스 태그로 승격하지 않았다. 확장 데이터 299건과
재학습 계획은 2026-07-04 파일럿 기록이며 현재 `delivery-s` 운영 기준과 구분한다.

## 서비스 런타임 계약

- 기본 provider는 `openai`이며 `CAREERTUNER_CORRECTION_AI_PROVIDER=self`와 자체 endpoint가 모두 있어야
  자동 경로에서 자체 모델이 활성화된다. 사용자가 `CAREERTUNER`를 명시 선택하면 설정된 endpoint를 직접 시도한다.
- 자체 모델은 temperature `0`, 최대 출력 `3072` tokens, 시도당 `20s`, 전체 `30s`, 최대 2회가 기본값이다.
- 첫 출력의 JSON 계약이 깨지면 같은 3B 모델에 오류와 이전 출력을 전달해 repair하고, 그래도 실패하면
  Anthropic, OpenAI 순서로 degrade한다. Mock 성공 결과는 만들지 않는다.
- 코드 정본은 `CorrectionAiProperties`, `CorrectionAiClient`, `SelfLlmCorrectionProvider`다.

## 한계

- 합성 데이터 기반이라 실제 사용자 문체와 분포 차이가 있을 수 있다.
- 평가셋도 합성 데이터 중심이므로 별도 사람 작성 unseen 테스트가 필요하다.
- LoRA 산출물은 본체 git에 포함하지 않는다. 배포 정본 `delivery-s` 어댑터만 `docs/ai-artifacts` 서브모듈에 백업돼 있고(본체 git 미포함, 서브모듈 백업 존재), 그 외 후보 어댑터는 4090 로컬에만 존재한다.
- `preserved_meaning`은 판단 경계가 애매한 경우가 있어 최종 서비스에서는 보조 지표로 다룬다.
- 현재 3B 후보는 자기소개서·포트폴리오 장문에서 분량 축약과 문단 손실이 발생할 수 있다.
- 확장 데이터 학습 전 stage100 검증에서 JSON 파싱, `risk_flags`, 길이, CJK 누출 실패가 확인됐다.

## 후속 개선 과제

1. 사람 작성 unseen 테스트를 추가하고 네 task별 충분한 표본으로 평가한다.
2. 중·장문 축약과 문단 손실을 보강한 3B 후보를 별도 태그로 학습한다.
3. 직접 추론, 동일 3B repair, Ollama strict schema 게이트를 모두 통과시킨다.
4. 현 `delivery-s`와 동일한 평가표로 회귀가 없는지 확인한 뒤에만 고정 서비스 태그를 변경한다.
5. 레거시 Qwen3-8B 태그와 로컬 산출물은 서비스 라우팅과 분리해 정리한다.
