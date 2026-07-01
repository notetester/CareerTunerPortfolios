# CareerTuner E Correction Model Card

## 목적

`careertuner-e-correction`은 E 담당 첨삭 영역의 통합 자체 모델이다. 하나의 모델이 `task_type`으로 아래 작업을 구분한다.

- `SELF_INTRO_CORRECTION`: 자기소개서 첨삭
- `INTERVIEW_ANSWER_CORRECTION`: 면접 답변 첨삭
- `RESUME_EXPRESSION_IMPROVEMENT`: 이력서 문장 개선
- `PORTFOLIO_DESCRIPTION_IMPROVEMENT`: 포트폴리오 설명 개선

모델의 핵심 목표는 문장을 더 그럴듯하게 만드는 것이 아니라, 원문과 제공된 `user_profile_facts`에 없는 경력, 기술, 수치, 성과를 추가하지 않는 원문 보존형 첨삭이다.

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

## 모델 후보

### 경량 baseline

- Base model: `Qwen/Qwen2.5-3B-Instruct`
- Adapter: `out/correction-lora-seed360-hardfailfix-3b`
- 역할: 경량 fallback, 파이프라인 검증 기준

### 주력 후보

- Base model: `Qwen/Qwen3-8B`
- Adapter: `out/correction-lora-seed360-qwen3-8b`
- 역할: E 첨삭 주력 품질 후보

모델 가중치와 LoRA adapter는 용량이 크므로 git에 포함하지 않는다. 로컬 산출물은 `out/` 아래에 두며 `.gitignore`로 제외한다.

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

평가 기준은 `scripts/test_infer.py`의 soft 모드다. `preserved_meaning` 기대값 불일치는 WARN으로 기록하고, 타입 오류는 FAIL로 처리한다.

| 모델 | 전체 40개 | 하드케이스 20개 | 주요 실패 |
| --- | ---: | ---: | --- |
| Qwen2.5-3B LoRA | 39/40 | 19/20 | 위험 케이스 `risk_flags` 누락 1건 |
| Qwen3-8B LoRA | 39/40 | 20/20 | 전체 평가 1건에서 입력 필드가 extra key로 출력됨 |

Qwen3-8B는 하드케이스에서 더 안정적이었고, E 첨삭 운영안의 주력 후보에 부합한다. 다만 extra key가 1건 발생했으므로 백엔드 JSON 스키마 검증과 fallback 또는 retry가 필요하다.

## 한계

- 합성 데이터 기반이라 실제 사용자 문체와 분포 차이가 있을 수 있다.
- 평가셋도 합성 데이터 중심이므로 별도 사람 작성 unseen 테스트가 필요하다.
- LoRA 산출물은 로컬 파일로만 존재하며 git에는 포함하지 않는다.
- `preserved_meaning`은 판단 경계가 애매한 경우가 있어 최종 서비스에서는 보조 지표로 다룬다.

## 다음 단계

1. 사람 작성 unseen 테스트 20개를 추가로 평가한다.
2. Qwen3-8B adapter를 merge/GGUF 변환해 Ollama 등록을 검증한다.
3. Spring Boot `correction` 도메인에 self provider를 추가한다.
4. 서버에서 JSON schema, extra key, added facts, confidence를 검증한다.
5. 실패 시 OpenAI 또는 경량 3B fallback으로 전환한다.
