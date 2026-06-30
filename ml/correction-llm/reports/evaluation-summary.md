# E Correction LLM Evaluation Summary

## 최종 로컬 결과

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

## 결론

Qwen3-8B LoRA가 E 첨삭 주력 후보로 적합하다. Qwen2.5-3B LoRA는 경량 fallback 및 파이프라인 검증 기준으로 유지한다. 서비스 연결 전에는 백엔드에서 JSON schema 검증과 extra key 차단을 반드시 구현해야 한다.
