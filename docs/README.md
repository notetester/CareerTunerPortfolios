# CareerTuner 문서 안내

이 폴더는 제품 목표, 시스템 경계, 운영 절차, 검증 기록을 연결하는 문서 진입점이다.
현재 구현 여부는 런타임 소스와 각 모듈 `README.md`를 함께 확인한다. 기획 문서에 적힌 목표가
현재 구현을, 과거 감사 기록이 현재 결함을 뜻하지는 않는다.

## 먼저 읽을 문서

| 알고 싶은 것 | 정본 또는 첫 진입점 |
| --- | --- |
| 저장소 소개와 실행 | [루트 README](../README.md) |
| 협업 규칙과 문서 충돌 처리 | [AGENTS.md](../AGENTS.md) |
| 제품 목표와 출시 우선순위 | [제품 기획서](planning/기획.md) |
| 기술 스택, API, 데이터, 시스템 경계 | [아키텍처](ARCHITECTURE.md) |
| 사용자 메뉴와 제품 정보 구조 | [제품 구조](PRODUCT_STRUCTURE.md) |
| 표준 폴더와 공통 파일 규칙 | [기능 모듈 구조](FEATURE_MODULE_STRUCTURE.md) |
| 담당자와 컴포넌트 소유권 | [팀 작업 분배](TEAM_WORK_DISTRIBUTION.md) |
| 소유권 빠른 참조 | [기능 소유권](FEATURE_OWNERSHIP.md) |
| 현재 백엔드·프런트엔드·데스크톱 구현 | [백엔드](../backend/README.md) · [프런트엔드](../frontend/README.md) · [데스크톱](../desktop/README.md) |
| 배포, 환경, 장애 대응 | [운영 문서](operations/README.md) |
| 시연 기준과 재검증 증거 | [검증 문서](verification/README.md) |
| 개인 차단·허용 범위와 집행 지점 | [개인 차단/허용 정책](PERSONAL_BLOCK_POLICY.md) |
| AI/ML 모듈 | [ML 안내](../ml/README.md) · [AI 종합 기술 보고서](ai-reports/areas/shared-ai/portfolio/careertuner-self-ai-model-deep-dive.md) · [AI 저장소 경계](AI_REPOSITORY_BOUNDARIES.md) |

문서·아키텍처·AI/ML·릴리즈처럼 범위가 넓은 작업은 Obsidian 서브모듈을 받은 뒤
`docs/obsidian-vault/AI_CONTEXT_MAP.md`, `wiki/index.md`, `graphify-out/GRAPH_REPORT.md` 순서로
후보 문서를 좁힌다.

## 문서 책임과 수명주기

| 분류 | 책임 | 현재 위치 | 변경 원칙 |
| --- | --- | --- | --- |
| 정본(canonical) | 여러 문서가 참조하는 제품·아키텍처·구조·소유권 계약 | `docs/` 루트의 핵심 문서와 `planning/기획.md` | 경로를 안정적으로 유지하고, 런타임 변경과 같은 PR에서 갱신한다. |
| 운영(operations) | 실행, 배포, 환경 전환, 장애 대응, 반복 가능한 runbook | [operations/](operations/README.md), 현재는 일부 문서가 `docs/` 루트와 저장소 루트에 남아 있음 | 명령·환경변수·rollback·검증 시점을 함께 기록한다. 비밀값은 적지 않는다. |
| 기획(planning) | 목표 상태, 정책 초안, 로드맵, UX 원칙 | [planning/](planning/README.md) | 목표와 현재 구현을 명시적으로 구분한다. 구현 완료의 증거로 사용하지 않는다. |
| 검증(verification) | 기준 SHA, 실행 명령, 결과, 재검증 조건 | [verification/](verification/README.md) | “통과”만 적지 않고 기준점과 증거를 남긴다. 변경 영향이 있을 때만 증분 재검증한다. |
| 보관(archive) | 완료된 인계, 일회성 감사, 병합 판단, 대체된 설계 | [archive/](archive/README.md) | 현재 정본이 아님을 문서 상단과 인덱스에 표시하고 원래 날짜를 보존한다. |
| 생성물(generated) | Markdown이나 도구로부터 만든 HTML, SVG, PNG, PDF | 현재 `docs/`와 하위 폴더에 일부 존재 | 생성 원본·생성 명령·수정 금지 여부를 인접 `README.md`에 기록한다. |
| 서브모듈(submodule) | 별도 저장소가 소유하는 장문 보고서, raw artifact, storyboard, 장기 맥락 | `ai-reports/`, `ai-artifacts/`, `storyboard/`, `obsidian-vault/` | 서브모듈 안에서 먼저 commit·push한 뒤 메인 저장소는 포인터만 갱신한다. |

같은 주제의 문서가 충돌하면 [AGENTS.md의 문서 책임과 충돌 처리](../AGENTS.md#문서-책임과-충돌-처리)를
따른다. 현재 구현과 목표 상태의 차이는 한쪽을 임의로 지우지 말고 각각 `현재`와 `목표`로 표시한다.

## 폴더 지도

| 경로 | 내용 |
| --- | --- |
| [planning/](planning/README.md) | 제품·UX·모바일·AI 도입 계획과 정책 초안 |
| [operations/](operations/README.md) | 운영 문서의 분류 기준과 현재 경로 지도 |
| [verification/](verification/README.md) | 시연 준비도와 기능별 검증 원장 |
| [archive/](archive/README.md) | 완료·대체된 문서의 연월별 보관 기준 |
| [ai-training/](ai-training/README.md) | A 영역 프로필 AI 학습 데이터·실행 가이드 |
| [class-diagrams/](class-diagrams/README.md) | 코드 정본과 대조해야 하는 클래스 다이어그램 스냅샷 |
| [db/](db/README.md) | schema·patch 정본과 대조해야 하는 DB 설계 산출물 |
| [planning/prototypes/](planning/prototypes/README.md) | 팀 공유용 화면·흐름 프로토타입 |
| [AI_REPORT/](AI_REPORT/README.md) | 장문 AI 보고서의 서브모듈 이관 경로를 안내하는 호환 포인터 |
| `ai-reports/` | 사람이 읽는 AI 장문 실험 보고서 서브모듈 |
| `ai-artifacts/` | AI raw output·benchmark artifact 서브모듈 |
| `storyboard/` | A~F·통합 설계 산출물 서브모듈 |
| `obsidian-vault/` | Obsidian/Graphify/LLM Wiki 장기 맥락 서브모듈 |

## 루트 정책·프로토타입 자산

`docs/` 루트에 남은 HTML 자산은 실행 코드의 정본이 아니라 구현 의도와 화면 흐름을 찾기 위한 시안이다.
현재 구현은 표에 연결한 모듈 README와 런타임 소스로 다시 확인한다.

| 자산 | 성격 | 구현·후속 정보를 찾는 경로 |
| --- | --- | --- |
| [PERSONAL_BLOCK_POLICY.md](PERSONAL_BLOCK_POLICY.md) | 개인 차단·허용 범위와 서버 집행 지점의 정책 정본 | [아키텍처](ARCHITECTURE.md)와 백엔드 `privacy` 도메인 |
| [desktop-app-dotnet-concept.html](archive/2026-06/desktop-app-dotnet-concept.html) | C#/.NET 데스크톱 초기 구상 보관본 | [데스크톱 README](../desktop/README.md) |
| [desktop-app-prototype.html](desktop-app-prototype.html) | 초기 인터랙티브 데스크톱 화면 시안 | [데스크톱 README](../desktop/README.md) |
| [desktop-app-stack.html](desktop-app-stack.html) | C++/Qt 기술 선택 설명 | [데스크톱 README](../desktop/README.md)와 `desktop/CMakeLists.txt` |
| [desktop-app-v2-mockup.html](desktop-app-v2-mockup.html) | 현행 데스크톱 셸의 시각 기준 | [데스크톱 README](../desktop/README.md)와 `desktop/qml/` |
| [mobile-app-v2-mockup.html](mobile-app-v2-mockup.html) | 모바일 화면·기기 연동 시안 | [모바일 빌드 안내](../frontend/MOBILE_BUILD.md)와 `frontend/src/platform/` |
| [planning/prototypes/](planning/prototypes/README.md) | 랜딩·오케스트레이터 화면과 흐름 시안의 정본 폴더 | [AI 오케스트레이터](AI_ORCHESTRATOR.md)와 프런트 `AutoPrep` 구현 |

오케스트레이터 흐름 SVG는
[planning/prototypes/orchestrator-flow.svg](planning/prototypes/orchestrator-flow.svg)를 정본으로 사용한다.
루트에 같은 파일을 복제하지 않는다. 새 생성 자산은 가장 가까운 폴더 README에서 원본·생성 방식·현재성을
설명하고 위 표 또는 해당 기능 README에서 발견할 수 있게 연결한다.

## 경로 정리 원칙

문서 링크와 코드 주석, 검증 JSON, 별도 서브모듈이 기존 경로를 참조하므로 한 번에 전부 이름을
바꾸지 않는다. 아래 표는 이번 정리에서 분류한 경로다.

| 이전 경로 | 현재 경로 | 성격 |
| --- | --- | --- |
| `design-progress.md` | [디자인 적용 기록](archive/2026-06/design-system-rollout.md) | 완료된 디자인 적용 기록 | <!-- docs-link-check: ignore -->
| `docs/AI_CAREER_STRATEGY_EVIDENCE_GATE_DESIGN.md` | [AI evidence gate R3-pre 설계](archive/2026-06/c-evidence-gate-r3-pre-design.md) | 후속 구현으로 대체된 설계 초안 | <!-- docs-link-check: ignore -->
| `docs/MERGE_DEV_RECONCILIATION.md` | [dev 병합 조정 기록](archive/2026-07/dev-merge-reconciliation.md) | 일회성 병합 판단 | <!-- docs-link-check: ignore -->
| `docs/F_B인계_공고추출_트랜잭션분리.md` | [B 인계 기록](archive/2026-07/b-job-posting-transaction-handoff.md) | 완료 여부를 확인할 인계 기록 | <!-- docs-link-check: ignore -->
| `docs/F_CHATBOT_AUDIT.md` | [F 챗봇 감사](archive/2026-07/f-chatbot-audit.md) | 시점이 고정된 감사 기록 | <!-- docs-link-check: ignore -->
| `docs/TRIPTOGETHER_PARITY.md` | [TripTogether 이식 패리티](archive/2026-07/triptogether-parity.md) | 완료된 이식 비교 기록 | <!-- docs-link-check: ignore -->
| `docs/C_FEATURE_VERIFICATION.md` | [C 기능 검증 원장](verification/c-feature-verification.md) | 기능별 검증 원장 | <!-- docs-link-check: ignore -->
| `docs/REALDATA_RUNBOOK.md` | [실데이터 runbook](operations/real-data-runbook.md) | 운영 절차 | <!-- docs-link-check: ignore -->
| `docs/GPU_CONCURRENCY_POLICY.md` | [GPU 동시성 정책](operations/ai/gpu-concurrency-policy.md) | AI 운영 정책 | <!-- docs-link-check: ignore -->
| `docs/BRANCHES.md` | [브랜치 명명 규칙](operations/repository/branch-naming.md) | 저장소 운영 규칙 | <!-- docs-link-check: ignore -->
| `docs/AI_REPORT/CAREERTUNER_SELF_AI_MODEL_DEEP_DIVE.md` | [AI 종합 기술 보고서](ai-reports/areas/shared-ai/portfolio/careertuner-self-ai-model-deep-dive.md) | 장문 보고서 서브모듈 이관 | <!-- docs-link-check: ignore -->
| `docs/AI_ORCHESTRATOR.html` | [AI 오케스트레이터 HTML 스냅샷](archive/2026-06/ai-orchestrator-design-snapshot.html) | 현재 문서와 분리한 생성 HTML |

다음 고결합 경로는 1차 정리에서 이동하지 않는다.

- `AGENTS.md`, `README.md`, `SECURITY.md`, `DEPLOY.md`
- `docs/ARCHITECTURE.md`, `PRODUCT_STRUCTURE.md`, `FEATURE_MODULE_STRUCTURE.md`,
  `FEATURE_OWNERSHIP.md`, `TEAM_WORK_DISTRIBUTION.md`
- `docs/planning/기획.md`, `ENVIRONMENTS.md`, `RELEASE.md`, `PERSONAL_BLOCK_POLICY.md`,
  `AI_JOB_POSTING_PIPELINE_RUNBOOK.md`, 루트 `design.md`
- 네 서브모듈 gitlink 경로

이동할 때는 `git mv`와 모든 추적 파일의 경로 참조 갱신을 같은 커밋에 포함하고, Markdown 링크 검사와
경로를 소비하는 테스트를 통과시킨다. 외부 deep link를 고려해 필요하면 구경로에 짧은 호환 안내를
한 릴리스 동안 유지한다.

## 이름 규칙

- 새 폴더와 일반 문서 파일명은 소문자 ASCII `kebab-case`를 사용한다.
- 폴더의 진입 문서만 관례에 따라 `README.md`를 사용한다.
- 한글은 문서 제목과 본문에서 자유롭게 사용하되 새 경로에는 넣지 않는다.
- 날짜가 문서 정체성인 보관 기록은 `archive/YYYY-MM/`에 두고, 파일명에 날짜를 중복하지 않는다.
- `final`, `latest`, `new`, `v2`처럼 시간이 지나면 의미가 흐려지는 이름 대신 주제와 상태를 적는다.
- 이미 널리 참조되는 대문자·한글·공백 경로는 별도 이동 작업 없이 이름만 정리하지 않는다.

한글·공백 경로는 URL percent-encoding, Windows/macOS 파일명 정규화(NFC/NFD), shell quoting에서
차이가 날 수 있다. 제목을 바꾸면 GitHub heading anchor도 달라질 수 있으므로 경로와 제목을 같은 PR에서
무분별하게 동시에 바꾸지 않는다.

## 문서 머리말과 최신성

새로운 장문 문서에는 제목 바로 아래에 다음 정보를 둔다. 기존 문서는 내용 검증과 함께 점진적으로
보강한다.

```text
상태: canonical | active | draft | archived | generated
담당: 팀 또는 기능 영역
마지막 검증: YYYY-MM-DD, commit SHA
다시 검증할 조건: 관련 코드·DB·워크플로·외부 설정 변경
```

- `마지막 수정일`과 `마지막 검증일`을 혼동하지 않는다.
- 현재 수치·버전·API 목록은 가능하면 설정이나 소스에서 생성하거나 검증 명령을 함께 둔다.
- 작업 메모와 raw 결과는 정본에 누적하지 않고, 각각 `archive/` 또는 적절한 artifact 서브모듈로 보낸다.
- 폴더 `README.md`는 목록만 나열하지 않고 책임, 정본, 추가 위치, 보관 기준을 설명한다.
