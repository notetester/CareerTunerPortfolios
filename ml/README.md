# CareerTuner ML 모듈 안내

이 폴더는 기능 영역별 자체 모델의 재현 가능한 코드, 소형 fixture, validator와 모듈 인접 문서를 둔다.
모델 하나가 전체 제품 AI를 대표하지 않으므로 각 기능의 runtime provider와 fallback은 백엔드 설정을
함께 확인한다.

## 모듈 지도

| 경로 | 영역·역할 | 먼저 읽을 문서 |
| --- | --- | --- |
| `career-strategy-llm/` | C 커리어 전략·적합도 모델과 evidence gate | [README](career-strategy-llm/README.md) · [현재 상태](career-strategy-llm/CURRENT_STATE.md) · [모델 카드](career-strategy-llm/model-card.md) |
| `correction-llm/` | E 답변·자기소개서 첨삭 모델 | [README](correction-llm/README.md) · [모델 카드](correction-llm/model-card.md) |
| `interview-finetune/` | D 면접 질문·답변 평가 텍스트 모델 | [README](interview-finetune/README.md) · [학습 가이드](interview-finetune/TRAINING.md) |
| `interview-nonverbal/` | D 음성·영상 비언어 평가 | [README](interview-nonverbal/README.md) |
| `job-posting-worker/` | B 공고 문서·OCR 추출 worker | [README](job-posting-worker/README.md) · [운영 runbook](../docs/AI_JOB_POSTING_PIPELINE_RUNBOOK.md) |
| `moderation-llm/` | F 커뮤니티 게시글 검열 모델 | [README](moderation-llm/README.md) |
| `ncs-catalog/` | C NCS 직무능력표준 카탈로그 | [README](ncs-catalog/README.md) |

A 영역 프로필 AI 학습 도구는 현재 [docs/ai-training/](../docs/ai-training/README.md)에 있다. 전체 A~F
모델의 장문 기술 설명은 [자체 AI 모델 종합 보고서](../docs/ai-reports/areas/shared-ai/portfolio/careertuner-self-ai-model-deep-dive.md)를
참고하되, 실제 운영 상태는 각 모듈과 백엔드 runtime을 우선한다.

## 저장 경계

- 이 폴더: 재현용 학습·평가·변환·서빙 코드, 소형 입력 fixture, 모듈 README와 model card
- `docs/ai-reports/`: 장문 실험 보고서와 누적 해석
- `docs/ai-artifacts/`: generated request, raw model output, result JSON, manifest, benchmark artifact
- 모델 weight·GGUF·adapter와 비밀값: Git에 커밋하지 않음

`career-strategy-llm/reports/`처럼 기존 호환 경로는 짧은 archive index와 링크만 유지하고, 새 장문
보고서를 메인 저장소에 추가하지 않는다.

## 모듈 문서 필수 정보

각 모듈 README 또는 model card에는 다음을 기록한다.

1. 담당 기능과 비목표
2. base model·라이선스·학습 방식
3. 데이터 출처와 개인정보 경계
4. 실제 학습·평가·서빙 명령
5. runtime 활성 조건과 provider fallback
6. 알려진 한계와 마지막 검증 SHA·날짜

계획이나 과거 실험 결과를 현재 production 활성 상태로 표현하지 않는다. 설정·모델 tag·validator가
바뀌면 관련 문서와 검증 원장을 같은 변경 단위에서 갱신한다.
