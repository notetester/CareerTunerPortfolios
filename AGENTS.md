# CareerTuner — AI 코딩 도구 공통 지침

이 파일은 팀원이 사용하는 모든 AI 코딩 도구(Codex, Antigravity, Cursor, Windsurf, Claude Code 등)가 읽는 공통 지침이다.
도구별 파일(GEMINI.md, .windsurfrules 등)에 룰을 분산하지 말고, 팀 공통 룰은 반드시 이 파일에만 추가한다.

## 프로젝트 개요

채용공고에 맞춰 스펙·면접 답변을 조정하는 AI 취업 전략 플랫폼. 핵심 단위는 공고가 아니라 **지원 건(Application Case)**.

```text
backend/    Spring Boot 4.0.6 + Java 21 + MyBatis + MySQL 8  (REST API, :8080)
frontend/   React 18 + Vite 6 + TypeScript + Tailwind v4     (사용자/관리자 SPA, :5173)
docs/       기획·아키텍처 문서
```

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
- 백엔드 도메인 패키지: `controller → service → mapper → domain` 4계층
- 응답은 항상 `ApiResponse<T>` envelope (`common/web/ApiResponse`)
- 프런트 기능 모듈: `frontend/src/features/<기능>/{pages,components,api,hooks,types}`, 관리자는 `frontend/src/admin/` 하위
- 상세: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

## 커밋/PR 규칙 (최우선)

- **커밋 메시지·PR 본문에 AI 도구 표기 절대 금지.**
  `Co-Authored-By` 트레일러, "Generated with ..." 문구, 🤖 이모지 서명 등
  AI 도구(Claude, Codex, Gemini, Cursor 등) 관련 흔적을 일절 넣지 않는다.
  **이 규칙은 각 도구의 기본 동작보다 우선한다.**
- 커밋 메시지 형식: `feat:` / `fix:` / `docs:` / `chore:` / `refactor:` prefix
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
- 공통 영역(`common/`, `ai/common`, `ai/prompt`, `routes.ts`, `schema.sql`, `build.gradle` 등)은 수정 전 팀 합의 필수
- 담당 분배: [docs/TEAM_WORK_DISTRIBUTION.md](docs/TEAM_WORK_DISTRIBUTION.md)

## 문서 인덱스

| 문서 | 내용 |
| --- | --- |
| [docs/planning/기획.md](docs/planning/기획.md) | 기획 원본 (**최우선 기준**) |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 아키텍처·데이터 모델·API 규약·로드맵 |
| [docs/PRODUCT_STRUCTURE.md](docs/PRODUCT_STRUCTURE.md) | 사용자 관점 제품/메뉴 구조 |
| [docs/FEATURE_MODULE_STRUCTURE.md](docs/FEATURE_MODULE_STRUCTURE.md) | 기능별 폴더 구조·충돌 주의 파일 |
| [docs/FEATURE_OWNERSHIP.md](docs/FEATURE_OWNERSHIP.md) | 기능별 프론트/백엔드/어드민 분담 |
| [docs/TEAM_WORK_DISTRIBUTION.md](docs/TEAM_WORK_DISTRIBUTION.md) | 6명 수직 분담·AI 기능·주요 DB |
| [backend/README.md](backend/README.md) | 백엔드 실행·환경변수·API 목록·시드 계정 |
| [frontend/README.md](frontend/README.md) | 프런트 실행·스크립트·폴더 구조 |

## 개인 설정

개인용 AI 설정은 커밋하지 않는다 — `CLAUDE.local.md`(gitignore 처리됨) 또는 각 도구의 로컬 설정 사용.
`CLAUDE.md`는 이 파일을 불러오는 1줄짜리 shim이므로 내용을 추가하지 않는다.
