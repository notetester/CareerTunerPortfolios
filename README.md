# CareerTuner

채용공고에 맞춰 내 스펙과 면접 답변을 조정하는 **AI 취업 전략·가상 면접 준비 플랫폼**.

공고를 업로드하면 AI가 요구조건·기업 현황을 분석하고, 내 프로필과 비교해 **지원 전략·부족 역량·학습 방향·예상 질문**을 만들어 주며, **AI 모의면접 → 답변 평가·첨삭 → 장기 취업 경향 분석**까지 하나의 "지원 건" 안에서 관리한다.

## 모노레포 구성

```text
CareerTuner/
 ├─ backend/    Spring Boot 4 + MyBatis + MySQL  (REST API · :8080)
 ├─ frontend/   React 18 + Vite + TypeScript      (사용자/관리자 반응형 웹/PWA-ready · :5173)
 ├─ ml/         자체 LLM 파인튜닝/평가 실험 산출물
 └─ docs/       기획 및 아키텍처 문서
```

IntelliJ Ultimate에서 이 루트 폴더를 열면 backend(Spring Boot)와 frontend(npm)를 한 IDE에서 관리할 수 있다.

관리자 프런트엔드는 별도 앱으로 분리하지 않고 `frontend/src/admin/` 아래에서 관리한다.
별도 배포 도메인, 완전히 다른 인증/네트워크 경계, 독립 릴리즈 주기 같은 요구가 생길 때만 팀 결정으로 분리한다.

## 빠른 시작

**백엔드** (JDK 21 필요)
```bash
cd backend
./gradlew bootRun        # Windows: .\gradlew.bat bootRun
# http://localhost:8080/api/health
```

**프런트엔드** (Node 20+)
```bash
cd frontend
npm install
npm run dev
# http://localhost:5173  (/api 요청은 8080으로 프록시)
```

**데이터베이스** — MySQL 8에 스키마 적용
```bash
mysql -u <user> -p <database> < backend/src/main/resources/db/schema.sql
mysql -u <user> -p <database> < backend/src/main/resources/db/data.sql
```

## 데모 / 릴리즈

백엔드 없이 동작하는 **mock 데모 빌드**(로그인에 아무 값이나 입력 → 데모 계정 "김데모")를 세 채널로 배포한다.

| 채널 | 바로가기 | 만드는 법 |
| --- | --- | --- |
| 웹 데모 | <https://notetester.github.io/CareerTunerDemo/> | `dev` 에 머지하면 자동 배포 |
| Android APK | [Releases](https://github.com/notetester/CareerTuner/releases) 에서 다운로드 → BlueStacks 에 드래그&드롭 | `v*` 또는 `demo-*` 태그 push 시 자동 첨부 |
| iOS (시뮬레이터) | Actions → "Build iOS demo" 수동 실행 | 무서명 빌드, Mac 시뮬레이터에서 실행 |

```bash
# APK 릴리즈 만들기 — 태그만 푸시하면 약 3분 뒤 Releases 에 올라온다
git tag demo-apk-3 && git push origin demo-apk-3
```

상세 절차·주의사항: [docs/RELEASE.md](docs/RELEASE.md) · 모바일 빌드(PWA/Android/iOS): [frontend/MOBILE_BUILD.md](frontend/MOBILE_BUILD.md)

## 기술 스택

Spring Boot 4.1.0 · Java 21 · MyBatis · MySQL 8 · Spring Security · springdoc-openapi
／ React 18 · Vite 6 · TypeScript · Tailwind CSS v4 · shadcn/ui · react-router 7

> 영속성은 **MyBatis만** 사용한다(JPA 미사용).

## 문서

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — 전체 아키텍처/데이터 모델/로드맵
- [docs/PRODUCT_STRUCTURE.md](docs/PRODUCT_STRUCTURE.md) — 사용자 관점의 전체 메뉴/제품 구조
- [docs/FEATURE_MODULE_STRUCTURE.md](docs/FEATURE_MODULE_STRUCTURE.md) — 기능별 폴더 구조/담당 범위/공통 충돌 파일
- [docs/FEATURE_OWNERSHIP.md](docs/FEATURE_OWNERSHIP.md) — 기능별 프론트/백엔드/어드민 분담 구조
- [docs/TEAM_WORK_DISTRIBUTION.md](docs/TEAM_WORK_DISTRIBUTION.md) — 6명 수직분담/담당 AI 기능/주요 DB
- [docs/planning/기획.md](docs/planning/기획.md) — 제품 목표/기능 범위/출시 우선순위
- [docs/planning/자체LLM_팀_도입안.md](docs/planning/자체LLM_팀_도입안.md) — 팀 전체 자체 LLM 도입 근거/결정표/일정
- [docs/planning/담당별_자체LLM_운영안.md](docs/planning/담당별_자체LLM_운영안.md) — A~F 담당별 자체 모델 운영/검증/fallback/산출물 기준
- [docs/planning/디자인 분석.md](docs/planning/디자인%20분석.md) — UX/UI 설계 원칙
- [docs/planning/모바일 고려.md](docs/planning/모바일%20고려.md) — 반응형 웹/PWA/Capacitor 전략
- [docs/planning/추천 구조.md](docs/planning/추천%20구조.md) — 개발 환경과 모노레포 가이드
- [docs/RELEASE.md](docs/RELEASE.md) — 데모·릴리즈 가이드 (웹 데모/APK 태그 릴리즈/iOS)
- [frontend/MOBILE_BUILD.md](frontend/MOBILE_BUILD.md) — 모바일 앱 빌드 (PWA/Android/iOS, 서명 분기)
- [backend/README.md](backend/README.md) · [frontend/README.md](frontend/README.md)
- `docs/obsidian-vault/` — Obsidian/AI 장기 맥락 overlay vault submodule. 메인 repo 루트를 Obsidian Vault로 열고, 새 노트·첨부·템플릿은 이 submodule 아래에 둔다. 받기: `git submodule update --init docs/obsidian-vault`
- `docs/storyboard/` — 담당자별 공용 산출물(UI/UX 스토리보드·DB 설계서·클래스 설계서 등, 별도 repo [CareerTunerDocs](https://github.com/notetester/CareerTunerDocs) · **git 서브모듈** · PPTX/PDF 등). 일반 개발엔 불필요하며 메인 클론 시 빈 폴더. 받기: `git submodule update --init docs/storyboard` ([AGENTS.md](AGENTS.md) 참고)

문서별 책임 범위와 충돌 처리 규칙은 [AGENTS.md](AGENTS.md)의 `문서 책임과 충돌 처리`를 따른다.

## 브랜치 전략

브랜치, 커밋, PR, push 규칙은 [AGENTS.md](AGENTS.md)를 유일한 기준으로 사용한다.

## 기능별 업무분담

기능 담당자는 `frontend/src/features/<기능>`, `frontend/src/admin/features/<기능>`,
`backend/src/main/java/com/careertuner/<도메인>`, `backend/src/main/java/com/careertuner/admin/<도메인>` 영역을 함께 본다.
사용자 기능을 완료할 때 관련 관리자 화면과 관리자 API도 같은 릴리스의 완료 기준에 포함한다.
공통 라우팅, 공통 컴포넌트, 공통 API, DB 구조, 인증/권한, AI 프롬프트 공통 엔진, 공통 로그 구조는 팀장 Owner 영역이므로 수정 전 팀 합의를 거친다.
기능별 프롬프트와 기능별 운영 로그는 각 담당자의 하위 폴더에 둔다.
고객센터/공지사항/FAQ/사용 가이드/문의하기는 `support`, 이용약관/개인정보처리방침/AI 데이터 이용 동의/저작권 정책은 `legal`,
기능 소개/서비스 소개는 `service`, 팀/채용/블로그/보도자료/공식 채널은 `company` 기능군으로 분리했다.
