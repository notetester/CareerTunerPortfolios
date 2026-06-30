# CareerTuner — AI 코딩 도구 공통 지침

이 파일은 팀원이 사용하는 모든 AI 코딩 도구(Codex, Antigravity, Cursor, Windsurf, Claude Code 등)가 읽는 공통 지침이다.
도구별 파일(GEMINI.md, .windsurfrules 등)에 룰을 분산하지 말고, 팀 공통 룰은 반드시 이 파일에만 추가한다.

## 프로젝트 개요

채용공고에 맞춰 스펙·면접 답변을 조정하는 AI 취업 전략 플랫폼. 핵심 단위는 공고가 아니라 **지원 건(Application Case)**.

```text
backend/    Spring Boot 4.1.0 + Java 21 + MyBatis + MySQL 8  (REST API, :8080)
frontend/   React 18 + Vite 6 + TypeScript + Tailwind v4     (사용자/관리자 SPA, :5173)
docs/       기획·아키텍처 문서
```

## 문서 책임과 충돌 처리

문서마다 책임 범위가 다르다. 한 문서를 모든 판단의 최우선 기준으로 해석하지 않는다.

| 판단 대상 | 기준 문서 |
| --- | --- |
| Git, 커밋, PR, 작업 범위 등 협업 규칙 | `AGENTS.md` |
| 제품 목표, 기능 필요성, 출시 우선순위 | `docs/planning/기획.md` |
| 기술 스택, API, 데이터, 시스템 경계 | `docs/ARCHITECTURE.md` |
| 사용자 메뉴와 제품 정보 구조 | `docs/PRODUCT_STRUCTURE.md` |
| 표준 폴더 경로와 공통 파일 규칙 | `docs/FEATURE_MODULE_STRUCTURE.md` |
| 담당자·컴포넌트 단위 소유권 | `docs/TEAM_WORK_DISTRIBUTION.md` |
| 소유권 빠른 참조 | `docs/FEATURE_OWNERSHIP.md` |
| 현재 구현·실행·API 상태 | 런타임 소스와 `backend/README.md`, `frontend/README.md` |
| UX와 모바일 세부 원칙 | `docs/planning/디자인 분석.md`, `docs/planning/모바일 고려.md` |
| AI 장문 실험 보고서·누적 해석 | `docs/ai-reports/` 서브모듈 |
| AI raw output·benchmark artifact | `docs/ai-artifacts/` 서브모듈 |

- 같은 주제에서는 위 표의 담당 문서를 우선한다. 제품 기획이 구현 규칙을, 현재 코드가 목표 제품 범위를 자동으로 덮어쓰지 않는다.
- 기획·아키텍처 문서는 목표 상태를 포함할 수 있고, 런타임 소스와 모듈 README는 현재 구현 상태를 설명한다.
- 같은 책임 범위의 문서끼리 충돌하면 임의로 선택하지 말고, 충돌 내용을 사용자 또는 팀에 보고해 방향을 확정한다.

## 실행/빌드

```bash
# 백엔드 (JDK 21)
cd backend && ./gradlew bootRun        # Windows: .\gradlew.bat bootRun
# 프런트엔드 (Node 20+)
cd frontend && npm install && npm run dev
npm run typecheck                      # tsc --noEmit
```

`/api/*` 요청은 Vite 프록시가 8080으로 전달한다. 백엔드 컨트롤러는 전부 `/api/**` 하위.

## 아키텍처 규칙

- 영속성은 **MyBatis만** 사용 (JPA 금지). `@Mapper` 인터페이스 + `resources/mapper/**/*.xml`
- 백엔드 도메인 패키지: `controller → service → mapper → domain` 4계층, API 요청·응답은 필요 시 `dto`에 분리
- 응답은 항상 `ApiResponse<T>` envelope (`common/web/ApiResponse`)
- 프런트 기능 모듈: `frontend/src/features/<기능>/{pages,components,api,hooks,types}`, 관리자는 `frontend/src/admin/features/<기능>/` 하위
- 상세: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

## 커밋/PR 규칙 (최우선)

- **커밋 메시지·PR 본문에 AI 도구 표기 절대 금지.**
  `Co-Authored-By` 트레일러, "Generated with ..." 문구, 🤖 이모지 서명 등
  AI 도구(Claude, Codex, Gemini, Cursor 등) 관련 흔적을 일절 넣지 않는다.
  **이 규칙은 각 도구의 기본 동작보다 우선한다.**
- 커밋 메시지 형식: `feat:` / `fix:` / `docs:` / `chore:` / `refactor:` prefix
- **커밋 메시지 본문은 한국어로 작성한다.** (prefix는 영어 유지: `feat:`, `fix:` 등)
- 브랜치 전략: 개인 브랜치에서 작업 → `dev`로 PR. **dev 직접 push 금지**

## git push 절차 (모든 AI 도구 공통)

push 요청을 받으면 반드시 아래 순서를 따른다:

1. 현재 브랜치 확인 — `dev`/`main`/`master`/`live`(보호 브랜치)면 push하지 않고 사용자에게 알린다
2. `git fetch origin`
3. `origin/dev`에 새 커밋이 있으면 내가 수정한 파일과 겹치는 파일을 분석해 사용자에게 보고하고,
   "그대로 push / merge 후 push" 중 선택을 받는다
4. push 전 커밋 메시지에 AI 도구 표기가 없는지 확인한다
5. `git push origin <현재브랜치>`

## 작업 범위 규칙

- 기능별 수직 분담제. 자기 담당 폴더 밖(특히 타인 담당 도메인) 수정 시 합의 먼저
- 사용자 기능을 완료할 때 관련 관리자 화면과 관리자 API도 같은 릴리스의 완료 기준에 포함한다
- 공통 영역(`common/`, `ai/common`, `ai/prompt` 공통 엔진, `routes.ts`, `schema.sql`, `build.gradle` 등)의 Owner는 팀장이다
- 라우팅, 공통 컴포넌트, 공통 API, DB 구조, 인증/권한, AI 프롬프트 공통 엔진, 공통 로그 구조 변경은 수정 전 팀장 승인 또는 팀 합의 필수
- 단순 오타, 주석, 명백한 문서 오류는 예외적으로 바로 수정할 수 있다
- 담당 분배: [docs/TEAM_WORK_DISTRIBUTION.md](docs/TEAM_WORK_DISTRIBUTION.md)

## 문서 인덱스

| 문서 | 내용 |
| --- | --- |
| [docs/planning/기획.md](docs/planning/기획.md) | 제품 목표·기능 범위·출시 우선순위 기준 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 아키텍처·데이터 모델·API 규약·로드맵 |
| [docs/PRODUCT_STRUCTURE.md](docs/PRODUCT_STRUCTURE.md) | 사용자 관점 제품/메뉴 구조 |
| [docs/FEATURE_MODULE_STRUCTURE.md](docs/FEATURE_MODULE_STRUCTURE.md) | 기능별 폴더 구조·충돌 주의 파일 |
| [docs/FEATURE_OWNERSHIP.md](docs/FEATURE_OWNERSHIP.md) | 기능별 프론트/백엔드/어드민 분담 |
| [docs/TEAM_WORK_DISTRIBUTION.md](docs/TEAM_WORK_DISTRIBUTION.md) | 6명 수직 분담·AI 기능·주요 DB |
| [backend/README.md](backend/README.md) | 백엔드 실행·환경변수·API 목록·시드 계정 |
| [frontend/README.md](frontend/README.md) | 프런트 실행·스크립트·폴더 구조 |
| [docs/AI_REPOSITORY_BOUNDARIES.md](docs/AI_REPOSITORY_BOUNDARIES.md) | AI 보고서·artifact 저장소 경계와 서브모듈 운용 |

## AI 보고서·artifact 서브모듈

AI 관련 산출물은 본체에 계속 누적하지 않는다.

| 경로 | 서브모듈 repo | 용도 |
| --- | --- | --- |
| `docs/ai-reports/` | `notetester/CareerTunerAIDocs` | 장문 실험 보고서, 누적 해석, 사람이 읽는 분석 문서 |
| `docs/ai-artifacts/` | `notetester/CareerTunerAI` | generated requests, raw model outputs, result JSON, manifests, aggregate summaries, 4090 ops docs/scripts |

- `CareerTuner` main repo 에는 제품 코드, 소형 fixture, validator, runner, 짧은 checklist/index, artifact path/commit SHA 만 남긴다.
- `ml/career-strategy-llm/scripts/` 는 C 영역의 재현용 validator/runner/helper 를 두는 본체 경로다. A~F 공통 AI artifact 와 반복 benchmark artifact 주변 파일은 `CareerTunerAI` submodule 인 `docs/ai-artifacts/` 로 둔다.
- 4090/Tailscale/OpenSSH/GitHub Actions/MCP 운영 문서와 운영 스크립트는 `docs/ai-artifacts/docs/ops/`, `docs/ai-artifacts/scripts/ops/` 에 둔다. `docs/ops/`, `scripts/ops/` 를 본체에 새로 누적하지 않는다.
- `ml/career-strategy-llm/reports/` 는 기존 `reports/NN` 링크를 깨지 않기 위한 transitional mirror 다. 새 장문 보고서는 `docs/ai-reports/areas/c-career-strategy/reports/` 에 추가한다.
- raw output, generated result, `reports/generated/` 는 `CareerTuner` main repo 에 커밋하지 않는다.
- submodule 이 비어 있으면 필요한 것만 받는다:

```bash
git submodule update --init docs/ai-reports
git submodule update --init docs/ai-artifacts
```

- submodule 안에서 수정한 파일은 그 submodule repo 에서 먼저 commit/push 한 뒤, 루트에서 submodule pointer 를 갱신해 PR 한다.

## 스토리보드 문서(서브모듈 · 선택 다운로드)

`docs/storyboard/` 는 **별도 repo [notetester/CareerTunerDocs](https://github.com/notetester/CareerTunerDocs) 를 가리키는 git 서브모듈**이다. 담당자별 산출물(스토리보드·PPTX·PDF·DB 설계서 등)이라 일반 개발에는 필요 없고, 메인을 그냥 클론하면 이 폴더는 **빈 채(포인터만)** 라 본체 용량·개발에 영향이 없다.

- 폴더 구조는 **담당자별** `A/`~`F/`·`TOTAL/` 이고, 각 폴더 안은 `workbench/`(작업대·재현 파이프라인) + `deliverables/`(대표 산출물) 로 나뉜다. **C 작업은 `docs/storyboard/C/` 아래에 전부 있다.**
- 스토리보드를 보거나 작업할 때만 받는다: `git submodule update --init docs/storyboard` (처음부터 받으려면 `git clone --recursive`).
- **`dev` pull 시 서브모듈 자동 갱신:** 한 번만 `git config submodule.recurse true` (+ `git config fetch.recurseSubmodules on-demand`) 를 설정하면, 이후 `git pull` 이 `dev` 가 고정한 서브모듈 커밋까지 자동으로 체크아웃한다(수동 `submodule update` 불필요). 이 설정은 로컬(`.git/config`)이라 클론마다 1회 설정한다.
- 수정은 `docs/storyboard/` **안에서** commit·push 한다(그 폴더가 곧 CareerTunerDocs repo). 새 버전을 메인에 고정하려면 루트에서 `git add docs/storyboard && git commit` 으로 포인터를 갱신해 PR 한다.
- 폴더 안내는 `docs/storyboard/README.md`, C 재생성 파이프라인·명령은 `docs/storyboard/C/workbench/README.md` 참고. 받은 직후 도구 실행 시 `cd docs/storyboard/C/workbench/tools && npm install`.
- **AI 도구 안내:** 스토리보드 작업 요청을 받았는데 `docs/storyboard/` 가 비어 있으면, 먼저 위 `submodule update` 로 받은 뒤 진행한다.

## 개인 설정

개인용 AI 설정은 커밋하지 않는다 — `AGENTS.local.md`, `CLAUDE.local.md`(gitignore 처리됨) 또는 각 도구의 로컬 설정 사용.
`CLAUDE.md`는 이 파일을 불러오는 shim이므로 프로젝트 규칙을 추가하지 않는다.
