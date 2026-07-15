# E Correction LLM Evaluation Summary

> **보존 상태:** 이 문서는 2026-06-27 seed360 후보 비교 기록이다. 당시의 Qwen3-8B 주력 후보
> 결론은 폐기되었으며, 현재 서비스 기준은
> [`careertuner-e-correction-3b:delivery-s-f16-20260708`](../model-card.md)이다. 아래 수치는 과거
> 후보의 재현 근거로만 사용하고 현재 라우팅·배포 판단에 사용하지 않는다.

## 당시 로컬 후보 평가

평가일: 2026-06-27

| 모델 | Base | 학습 데이터 | 전체 평가 | 하드케이스 평가 | 리포트 |
| --- | --- | --- | ---: | ---: | --- |
| `correction-lora-seed360-hardfailfix-3b` | `Qwen/Qwen2.5-3B-Instruct` | 320 train / 40 val | 39/40 | 19/20 | `eval.seed360.*.soft.jsonl` |
| `correction-lora-seed360-qwen3-8b` | `Qwen/Qwen3-8B` | 320 train / 40 val | 39/40 | 20/20 | `eval.qwen3-8b.*.soft.jsonl` |

## Task별 Qwen3-8B 결과

전체 40개 평가:

- `SELF_INTRO_CORRECTION`: 10/10
- `INTERVIEW_ANSWER_CORRECTION`: 10/10
- `RESUME_EXPRESSION_IMPROVEMENT`: 10/10
- `PORTFOLIO_DESCRIPTION_IMPROVEMENT`: 9/10

하드케이스 20개 평가:

- `SELF_INTRO_CORRECTION`: 5/5
- `INTERVIEW_ANSWER_CORRECTION`: 5/5
- `RESUME_EXPRESSION_IMPROVEMENT`: 5/5
- `PORTFOLIO_DESCRIPTION_IMPROVEMENT`: 5/5

## 실패 및 경고

Qwen3-8B 전체 평가 실패 1건:

- `e-correction-0133`
- task: `PORTFOLIO_DESCRIPTION_IMPROVEMENT`
- 문제: 출력 JSON에 `target_role`, `job_context`, `user_profile_facts`, `constraints` extra key가 포함됨
- 해석: 첨삭 내용 자체보다 JSON schema 엄격성 문제
- 대응: 서버에서 extra key 차단, retry 또는 fallback 처리

Qwen3-8B 경고:

- `preserved_meaning_mismatch`: 전체 평가 4건, 하드케이스 4건
- 처리: 값 타입이 boolean이면 WARN으로 분리하고, 실제 서비스에서는 보조 지표로 사용

## 업로드 제외 대상

다음 파일은 git에 포함하지 않는다.

- `ml/correction-llm/out/`
- LoRA adapter, checkpoint, optimizer, tokenizer 산출물
- `.venv-ai/`
- 기타 개인 로컬 실행 산출물

## 현재 해석

이 평가만 놓고 보면 Qwen3-8B가 하드케이스 1건 앞섰지만, 이것만으로 운영 모델 승격을 정당화하지
않는다. 현재는 팀 공통 3B 기준, 고정 서비스 태그, strict schema 검증, repair와 provider fallback을
포함한 전체 런타임 계약으로 배포를 판단한다. Qwen3-8B와 남아 있는 과거 태그는 서비스 라우팅에서
제외하며, 최신 운영 판단과 알려진 한계는 [model-card](../model-card.md)를 따른다.
