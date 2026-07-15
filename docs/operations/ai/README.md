# AI 운영 문서 안내

AI provider, 자체 모델 서빙, worker와 공유 GPU의 운영 절차를 찾는 진입점이다. 장문 실험 해석과 raw
결과는 이 폴더에 두지 않는다.

## 현재 문서

| 주제 | 현재 정본·진입점 | 비고 |
| --- | --- | --- |
| AI 오케스트레이션 | [AI_ORCHESTRATOR.md](../../AI_ORCHESTRATOR.md) | 현재 구현과 목표 구상을 구분해서 갱신 |
| 공고 추출 worker | [AI_JOB_POSTING_PIPELINE_RUNBOOK.md](../../AI_JOB_POSTING_PIPELINE_RUNBOOK.md) | 실행 스크립트가 현재 경로를 사용하므로 이동 보류 |
| 공유 4090 동시성 | [gpu-concurrency-policy.md](gpu-concurrency-policy.md) | 측정 근거와 운영 기본값을 함께 관리 |
| 실데이터 provider 전환 | [real-data-runbook.md](../real-data-runbook.md) | 환경·fallback과 함께 확인 |
| 모델별 실행 | [ml/README.md](../../../ml/README.md) | 각 모델 폴더 README와 model card가 진입점 |
| AI 저장소 경계 | [AI_REPOSITORY_BOUNDARIES.md](../../AI_REPOSITORY_BOUNDARIES.md) | 보고서·artifact·main repo 책임 구분 |

## 저장 경계

- 제품 코드, 소형 fixture, validator, runner: 메인 저장소의 해당 모듈
- 사람이 읽는 장문 실험 보고서: `docs/ai-reports/` 서브모듈
- raw output, benchmark artifact, 4090 운영 자산: `docs/ai-artifacts/` 서브모듈
- 장기 맥락과 Graphify/LLM Wiki: `docs/obsidian-vault/` 서브모듈

운영 문서에는 provider 우선순위, timeout, retry, fallback, circuit breaker, 관측 지표, 개인정보 경계를
명시한다. “AI 연결됨”처럼 범위가 모호한 표현 대신 기능·모델·환경별 실제 활성 조건을 적는다.
