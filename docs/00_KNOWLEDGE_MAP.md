# CareerTuner Knowledge Map

이 문서는 CareerTuner repo 전체를 Obsidian Vault로 열었을 때의 첫 진입점이다. 새 규칙을 만드는 문서가 아니라, 이미 흩어져 있는 기준 문서와 구현 문서를 작업 목적별로 연결하는 지도다.

## 먼저 읽을 문서

| 목적 | 기준 문서 |
| --- | --- |
| 협업 규칙, 브랜치, 커밋, PR, 작업 범위 | [AGENTS.md](../AGENTS.md) |
| 프로젝트 개요, 빠른 시작, 주요 문서 링크 | [README.md](../README.md) |
| Obsidian Vault 운영 방식 | [docs/OBSIDIAN.md](OBSIDIAN.md) |
| 제품 목표와 출시 우선순위 | [docs/planning/기획.md](planning/기획.md) |
| 전체 아키텍처, API, 데이터 모델, 시스템 경계 | [docs/ARCHITECTURE.md](ARCHITECTURE.md) |
| 사용자 메뉴와 제품 정보 구조 | [docs/PRODUCT_STRUCTURE.md](PRODUCT_STRUCTURE.md) |
| 기능별 폴더 구조와 충돌 주의 파일 | [docs/FEATURE_MODULE_STRUCTURE.md](FEATURE_MODULE_STRUCTURE.md) |
| 기능별 프론트/백엔드/어드민 분담 | [docs/FEATURE_OWNERSHIP.md](FEATURE_OWNERSHIP.md) |
| 6명 수직 분담, 담당 AI 기능, 주요 DB | [docs/TEAM_WORK_DISTRIBUTION.md](TEAM_WORK_DISTRIBUTION.md) |

## 현재 구현 상태

| 영역 | 문서 | 코드 위치 |
| --- | --- | --- |
| 백엔드 실행, 환경변수, API 목록 | [backend/README.md](../backend/README.md) | [backend/](../backend/) |
| 프런트 실행, 스크립트, 폴더 구조 | [frontend/README.md](../frontend/README.md) | [frontend/](../frontend/) |
| 모바일/PWA/Android/iOS 빌드 | [frontend/MOBILE_BUILD.md](../frontend/MOBILE_BUILD.md) | [frontend/android/](../frontend/android/) |
| 데스크톱 앱 구조와 Windows 배포 | [desktop/README.md](../desktop/README.md) | [desktop/](../desktop/) |
| 데모와 릴리즈 | [docs/RELEASE.md](RELEASE.md) | [.github/workflows/](../.github/workflows/) |
| 배포 환경 | [docs/ENVIRONMENTS.md](ENVIRONMENTS.md), [DEPLOY.md](../DEPLOY.md) | [docker-compose.yml](../docker-compose.yml) |

## 제품/UX 판단

| 목적 | 기준 문서 |
| --- | --- |
| UX/UI 설계 원칙 | [docs/planning/디자인 분석.md](planning/디자인%20분석.md) |
| 반응형 웹, PWA, Capacitor 전략 | [docs/planning/모바일 고려.md](planning/모바일%20고려.md) |
| 개발 환경과 모노레포 추천 구조 | [docs/planning/추천 구조.md](planning/추천%20구조.md) |
| 모바일 앱 정책 초안 | [docs/planning/모바일_앱_정책_초안.md](planning/모바일_앱_정책_초안.md) |
| 데스크톱 앱 컨셉/스택/mockup | [docs/desktop-app-concept.html](desktop-app-concept.html), [docs/desktop-app-stack.html](desktop-app-stack.html), [docs/desktop-app-v2-mockup.html](desktop-app-v2-mockup.html) |

## AI/ML 문서 경계

| 목적 | 기준 문서 |
| --- | --- |
| AI 보고서와 artifact 저장소 경계 | [docs/AI_REPOSITORY_BOUNDARIES.md](AI_REPOSITORY_BOUNDARIES.md) |
| AI 오케스트레이터 설계 | [docs/AI_ORCHESTRATOR.md](AI_ORCHESTRATOR.md) |
| 공고 분석 파이프라인 runbook | [docs/AI_JOB_POSTING_PIPELINE_RUNBOOK.md](AI_JOB_POSTING_PIPELINE_RUNBOOK.md) |
| 자체 LLM 팀 도입 판단 | [docs/planning/자체LLM_팀_도입안.md](planning/자체LLM_팀_도입안.md) |
| 담당별 자체 LLM 운영안 | [docs/planning/담당별_자체LLM_운영안.md](planning/담당별_자체LLM_운영안.md) |
| GPU/4090 운영 정책 | [docs/GPU_CONCURRENCY_POLICY.md](GPU_CONCURRENCY_POLICY.md), [docs/ai-artifacts/HANDOFF_4090.md](ai-artifacts/HANDOFF_4090.md) |

## ML 작업 진입점

| 영역 | 현재 상태/README | 주요 보고서 위치 |
| --- | --- | --- |
| C: career strategy / fit analysis | [ml/career-strategy-llm/README.md](../ml/career-strategy-llm/README.md), [CURRENT_STATE.md](../ml/career-strategy-llm/CURRENT_STATE.md) | [docs/ai-reports/areas/c-career-strategy/reports/README.md](ai-reports/areas/c-career-strategy/reports/README.md) |
| E: correction LLM | [ml/correction-llm/README.md](../ml/correction-llm/README.md), [model-card.md](../ml/correction-llm/model-card.md) | [ml/correction-llm/reports/evaluation-summary.md](../ml/correction-llm/reports/evaluation-summary.md) |
| Interview fine-tune | [ml/interview-finetune/README.md](../ml/interview-finetune/README.md), [TRAINING.md](../ml/interview-finetune/TRAINING.md) | [ml/interview-finetune/eval/README.md](../ml/interview-finetune/eval/README.md) |
| Nonverbal interview | [ml/interview-nonverbal/README.md](../ml/interview-nonverbal/README.md) | 해당 README |
| Job posting worker | [ml/job-posting-worker/README.md](../ml/job-posting-worker/README.md) | [ml/job-posting-worker/data/real_validation/README.md](../ml/job-posting-worker/data/real_validation/README.md) |

## Submodule 지도

| 경로 | 저장소 성격 | 우선 진입 문서 |
| --- | --- | --- |
| [docs/ai-reports/](ai-reports/) | 장문 실험 보고서, 누적 해석, 사람이 읽는 분석 | [docs/ai-reports/README.md](ai-reports/README.md), [docs/ai-reports/AGENTS.md](ai-reports/AGENTS.md) |
| [docs/ai-artifacts/](ai-artifacts/) | raw output, generated result, benchmark artifact, 4090 ops docs/scripts | [docs/ai-artifacts/README.md](ai-artifacts/README.md), [docs/ai-artifacts/AGENTS.md](ai-artifacts/AGENTS.md) |
| [docs/storyboard/](storyboard/) | A-F/TOTAL 스토리보드, DB/클래스 설계서, PPT/PDF 산출물 | [docs/storyboard/README.md](storyboard/README.md) |

Submodule은 일반 폴더처럼 Obsidian에서 읽히지만 Git ownership은 별도다. 메인 repo에서는 submodule pointer만 추적한다. 프로젝트가 고정한 최신 문맥은 다음 명령으로 맞춘다.

```bash
git pull --ff-only origin dev
git submodule update --init --recursive
```

`git submodule update --remote`는 기본 갱신 명령이 아니다. submodule의 default branch가 메인 repo의 dev 포인터보다 뒤에 있을 수 있으므로, 포인터를 옮기기 전에 [docs/AI_REPOSITORY_BOUNDARIES.md](AI_REPOSITORY_BOUNDARIES.md)의 submodule 작업 순서를 확인한다.

## 작업별 빠른 경로

| 작업 | 먼저 확인 |
| --- | --- |
| 새 기능 구현 | [AGENTS.md](../AGENTS.md), [docs/FEATURE_MODULE_STRUCTURE.md](FEATURE_MODULE_STRUCTURE.md), [docs/FEATURE_OWNERSHIP.md](FEATURE_OWNERSHIP.md), 해당 feature/backend README |
| 공통 API, 라우팅, DB, 인증/권한 변경 | [AGENTS.md](../AGENTS.md), [docs/ARCHITECTURE.md](ARCHITECTURE.md), [docs/TEAM_WORK_DISTRIBUTION.md](TEAM_WORK_DISTRIBUTION.md) |
| 사용자 UX/모바일 화면 | [docs/planning/디자인 분석.md](planning/디자인%20분석.md), [docs/planning/모바일 고려.md](planning/모바일%20고려.md), [frontend/README.md](../frontend/README.md) |
| AI provider/fallback/prompt 작업 | [docs/ARCHITECTURE.md](ARCHITECTURE.md#41-spring-bean--ai-provider-계약), [docs/AI_REPOSITORY_BOUNDARIES.md](AI_REPOSITORY_BOUNDARIES.md), 담당 ML README |
| 릴리즈/배포 | [docs/RELEASE.md](RELEASE.md), [frontend/MOBILE_BUILD.md](../frontend/MOBILE_BUILD.md), [desktop/README.md](../desktop/README.md), [.github/workflows/](../.github/workflows/) |
| 스토리보드/심사 산출물 | [docs/storyboard/README.md](storyboard/README.md), [docs/storyboard/TOTAL/README.md](storyboard/TOTAL/README.md) |

## 유지 원칙

- 이 파일은 큰 내용을 복제하지 않는다. 기준 문서를 빠르게 찾아가기 위한 링크만 둔다.
- 새 장문 보고서, raw output, generated artifact는 기존 저장소 경계를 따른다.
- 새 기능 문서를 만들면 이 지도에는 진입 링크만 추가한다.
- Obsidian 전용 wiki link보다 표준 Markdown 상대 링크를 우선한다. 사람, Codex, Claude Code가 모두 같은 경로를 해석할 수 있어야 한다.
