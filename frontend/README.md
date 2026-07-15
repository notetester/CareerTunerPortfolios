# CareerTuner — Frontend

React 19 + Vite 8 + TypeScript 7 + Tailwind CSS v4(shadcn/ui) 기반의 반응형 SPA다.
같은 소스와 빌드 산출물을 웹·PWA·Capacitor Android/iOS가 공유한다.

관리자 화면도 같은 Vite React 앱 안에서 관리한다. 관리자 전용 라우트와 기능은 `src/admin/` 아래에 두고,
일반 사용자 기능은 `src/features/` 아래에 둔다.

이 문서는 프런트엔드의 **현재 구현·실행 상태**를 설명한다. 목표 UX는
[`../docs/planning/디자인 분석.md`](../docs/planning/디자인%20분석.md), 표준 기능 구조와 소유권은
[`../docs/FEATURE_MODULE_STRUCTURE.md`](../docs/FEATURE_MODULE_STRUCTURE.md)와
[`../docs/TEAM_WORK_DISTRIBUTION.md`](../docs/TEAM_WORK_DISTRIBUTION.md)를 따른다.

## 실행

```bash
npm ci
npm run dev      # http://localhost:5173
```

`/api/*` 요청은 `vite.config.ts`의 프록시를 통해 백엔드(`http://localhost:8080`)로 전달된다. 따라서 개발 시 백엔드도 함께 실행해야 데이터 기능이 동작한다.

## 스크립트

| 명령 | 설명 |
| --- | --- |
| `npm run dev` | 개발 서버 (HMR) — 로컬 백엔드(:8080) 프록시 |
| `npm run dev:mock` | **백엔드 없이** mock 데모 모드로 개발 서버 실행 |
| `npm run dev:tailscale` / `dev:aws` | 환경 모드로 개발 서버 실행 (`.env.<모드>` 로드) |
| `npm run dev:sites` | Codex Sites Worker와 AWS API 프록시를 포함한 개발 서버 실행 |
| `npm run build` | 프로덕션 빌드 (`dist/`) — API 는 상대경로 `/api` |
| `npm run build:mock` | mock 데모 모드 프로덕션 빌드 (웹 데모/APK 용) |
| `npm run build:tailscale` / `build:aws` / `build:domain` | 환경 모드 빌드 — `VITE_API_BASE_URL` 이 빌드에 박힌다 |
| `npm run build:sites` | Codex Sites 배포 산출물(`dist/client`, `dist/server`) 생성 |
| `npm run native:sync -- android` | HTTPS 기본값 검증 후 Capacitor 동기화 (iOS는 `ios`) |
| `npm run app:tailscale` | tailscale 모드 빌드 + 보안 기본값 Android 동기화 (실백엔드 APK 준비) |
| `npm run preview` | 빌드 결과 미리보기 |
| `npm run typecheck` | 타입 검사 (`tsc --noEmit`) |
| `npm run test:admin-access` | 관리자 라우트·탭 권한 경계 회귀 검사 |
| `npm run test:native-oauth` / `test:deep-link-runtime` | 네이티브 OAuth·딥링크 계약 검사 |
| `npm run test:native-config` / `test:mobile-platform` | 네이티브 보안 설정·플랫폼 어댑터 검사 |
| `npm run gen:icons` | PWA/앱 아이콘 생성 (`public/icons/*`) |
| `npm run mobile:sync` | mock 빌드 + HTTPS 기본값 Android 프로젝트 동기화 |
| `npm run mobile:apk` | 디버그 APK 빌드 (`android/.../app-debug.apk`) |
| `npm run ios:sync` | mock 빌드 + iOS 프로젝트 동기화 (Mac 전용) |

모바일 앱(PWA/Android/iOS) 빌드 상세는 [MOBILE_BUILD.md](MOBILE_BUILD.md),
데모·릴리즈 절차는 [../docs/RELEASE.md](../docs/RELEASE.md) 참고.

## 환경 프로파일 / API 베이스

환경(localhost/tailscale/aws/domain)별 호스트는 `.env.<모드>` 파일로 커밋돼 있고
(`VITE_API_BASE_URL` + dev 프록시용 `VITE_PROXY_TARGET`), 정식 정의는
[`../config/environments.json`](../config/environments.json) ·
[`../docs/ENVIRONMENTS.md`](../docs/ENVIRONMENTS.md)를 따른다.

API 베이스는 `src/app/lib/apiBase.ts` 의 `apiBase()` 가 단일 소스다 —
**런타임 오버라이드(네이티브 앱/dev 전용) → `VITE_API_BASE_URL` → 상대경로 `/api`** 순.
네이티브 앱은 설정 → 계정 설정 → "서버 주소" 카드에서 재빌드 없이 백엔드 환경을 전환할 수 있다.

### Codex Sites 백업 프런트

`sites` 모드는 AWS 웹 프런트 장애 시 사용할 보조 SPA다. 브라우저는 같은 출처의 `/api`를 호출하고
`worker/index.ts`가 요청을 고정된 AWS 운영 주소로 전달한다. Worker는 요청자를 `sites` named client로
고정하고 브라우저가 보낸 frontend origin 식별값은 전달하지 않는다. `/__backup/health`는 AWS의 실제
health 응답이 `UP`인지 확인하며 결과를 캐시하지 않는다.

AWS 연결이 정상일 때는 실제 API와 데이터를 그대로 사용한다. 실제 API 요청이 네트워크 오류 또는
502/503/504를 받은 뒤 전체 health도 실패한 경우에만 `outage-demo`로 전환해 기존 mock 데이터를 표시한다.
장애 확인 전에는 선제 전환하지 않으며, 전환 뒤에만 복구 상태를 polling한다. 조회 요청은 확인된 장애에서
같은 요청을 mock으로 대체하지만, 저장 요청은 운영 서버의 처리 여부가 불확실하므로 첫 요청을 실패 처리한다.
사용자가 장애 체험 모드에서 다시 시도한 변경만 mock에 반영한다. 복구되면 페이지를 다시 불러 실제 데이터
모드로 돌아간다. 상단 배너는 장애 데모의 입력·변경사항이 저장되지 않음을 항상 알린다. AutoPrep 스트림도
첫 장애 요청은 실패 처리하고, 다음 재시도부터 저장되지 않는 6단계 시연 시퀀스를 사용한다.

Sites 주소에서는 AWS 연결 여부와 관계없이 결제·환불 실행 UI를 비활성화하고, Worker와 백엔드의
`@SitesFinancialMutation` 정책이 해당 요청을 403으로 차단한다. 소셜 로그인은 AWS가 정상일 때만 사용할 수
있고 `outage-demo` 중에는 차단한다.

이 구성은 **프런트엔드 배포 백업**이며 Spring Boot, MySQL, OCR/AI 작업자를 복제하지 않는다.
AWS API나 DB가 중단되면 실제 조회·저장·AI 처리는 할 수 없고 화면 체험만 유지된다. 완전한 장애 전환에는
별도 백엔드/DB 복제와 데이터 동기화가 추가로 필요하다.

현재 게시 주소는 <https://sites.example.com>이며 2026-07-14 root와
`/__backup/health`의 공개 200·upstream `UP`을 확인했다. 운영 도메인과 별개이고 자동 DNS 전환을 제공하지
않는다. 공개 범위는 Sites 배포 설정이 결정하므로 주소를 다시 게시할 때 접근 정책과 health를 함께 검증한다.

## 구조

```text
src/
 ├─ main.tsx              앱 진입점
 ├─ app/
 │   ├─ App.tsx           RouterProvider
 │   ├─ routes.ts         라우트 정의
 │   ├─ pages/            현재 프로토타입·통합 화면
 │   └─ components/
 │       ├─ ui/           shadcn/ui 프리미티브
 │       ├─ layout/       Header, Footer, Root
 │       └─ figma/        에셋 헬퍼
 ├─ features/             사용자 기능별 모듈
 ├─ admin/                관리자 라우트, 페이지, 기능별 모듈
 ├─ platform/             Capacitor 셸·카메라·푸시·딥링크·햅틱 어댑터
 └─ styles/               Tailwind v4 + 디자인 토큰(theme.css)
```

> 정본 라우트는 `src/app/routes.ts`다. 통합·전환 화면은 `src/app/pages/`, 사용자 기능 모듈은
> `src/features/`, 관리자 기능은 `src/admin/`에 둔다. 목표 제품 구조는
> `../docs/PRODUCT_STRUCTURE.md`와 `../docs/FEATURE_MODULE_STRUCTURE.md`를 기준으로 확인한다.
> 목표 내비게이션은 핵심 메뉴 + 큰 기능 드롭다운의 하이브리드 구조이며, 목표 URL 규칙은 주요 작업을 path로,
> 목록 필터·정렬을 query로 표현하는 방식이다. 현재 구현과 목표 규칙의 차이는
> `../docs/planning/디자인 분석.md`, `../docs/PRODUCT_STRUCTURE.md`,
> `../docs/FEATURE_MODULE_STRUCTURE.md`를 따른다.

AI 기능 화면은 자체 LLM 서버를 브라우저에서 직접 호출하지 않는다. 사용자 화면과 관리자 화면은 백엔드 `/api/**`를 통해
AI 결과, 사용량 로그, fallback 상태를 조회한다. A~F 담당별 자체 모델 운영과 시연 기준은
[`../docs/planning/담당별_자체LLM_운영안.md`](../docs/planning/담당별_자체LLM_운영안.md)를 따른다.

## 경로 별칭

`@/*` → `src/*` (vite.config.ts와 tsconfig.json에 동일하게 설정됨).

## 오픈소스·에셋 고지

UI 컴포넌트와 외부 이미지의 출처·라이선스는 [ATTRIBUTIONS.md](ATTRIBUTIONS.md)에 기록한다. 새 외부
컴포넌트나 에셋을 추가할 때는 구현과 같은 변경에서 해당 고지도 갱신한다.

## 모바일 / PWA 현재 지원 상태

- 반응형 웹과 `vite-plugin-pwa` 서비스 워커·manifest·아이콘을 함께 빌드한다. `/api` 응답은 서비스 워커가 캐시하지 않는다.
- Capacitor Android 프로젝트(`android/`)는 저장소에서 관리하고, 푸시·카메라·딥링크·햅틱 등 네이티브 어댑터가 연결되어 있다.
- iOS 프로젝트는 macOS/CI에서 재생성한다. 서명 없는 시뮬레이터 빌드는 수동 GitHub Actions로 검증할 수 있다.
- 실제 설치·서명·verified App/Universal Link의 완료 조건은 [MOBILE_BUILD.md](MOBILE_BUILD.md)를 따른다.
