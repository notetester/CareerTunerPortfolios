# CareerTuner — Frontend

React 18 + Vite + TypeScript + Tailwind CSS v4 (shadcn/ui). PC 웹과 모바일 웹을 모두 대상으로 하는 반응형 SPA이며, 이후 PWA → Capacitor 순으로 모바일 앱으로 확장한다.

관리자 화면도 같은 Vite React 앱 안에서 관리한다. 관리자 전용 라우트와 기능은 `src/admin/` 아래에 두고,
일반 사용자 기능은 `src/features/` 아래에 둔다.

이 문서는 프런트엔드의 **현재 구현·실행 상태**를 설명한다. 목표 UX는
[`../docs/planning/디자인 분석.md`](../docs/planning/디자인%20분석.md), 표준 기능 구조와 소유권은
[`../docs/FEATURE_MODULE_STRUCTURE.md`](../docs/FEATURE_MODULE_STRUCTURE.md)와
[`../docs/TEAM_WORK_DISTRIBUTION.md`](../docs/TEAM_WORK_DISTRIBUTION.md)를 따른다.

## 실행

```bash
npm install
npm run dev      # http://localhost:5173
```

`/api/*` 요청은 `vite.config.ts`의 프록시를 통해 백엔드(`http://localhost:8080`)로 전달된다. 따라서 개발 시 백엔드도 함께 실행해야 데이터 기능이 동작한다.

## 스크립트

| 명령 | 설명 |
| --- | --- |
| `npm run dev` | 개발 서버 (HMR) |
| `npm run dev:mock` | **백엔드 없이** mock 데모 모드로 개발 서버 실행 |
| `npm run build` | 프로덕션 빌드 (`dist/`) |
| `npm run build:mock` | mock 데모 모드 프로덕션 빌드 (웹 데모/APK 용) |
| `npm run preview` | 빌드 결과 미리보기 |
| `npm run typecheck` | 타입 검사 (`tsc --noEmit`) |
| `npm run gen:icons` | PWA/앱 아이콘 생성 (`public/icons/*`) |
| `npm run mobile:sync` | mock 빌드 + Android 프로젝트 동기화 (`cap sync`) |
| `npm run mobile:apk` | 디버그 APK 빌드 (`android/.../app-debug.apk`) |
| `npm run ios:sync` | mock 빌드 + iOS 프로젝트 동기화 (Mac 전용) |

모바일 앱(PWA/Android/iOS) 빌드 상세는 [MOBILE_BUILD.md](MOBILE_BUILD.md),
데모·릴리즈 절차는 [../docs/RELEASE.md](../docs/RELEASE.md) 참고.

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
 └─ styles/               Tailwind v4 + 디자인 토큰(theme.css)
```

> 현재 Figma Make 디자인 초안 기반 화면에 인증과 공통 API 클라이언트가 연결되어 있으며,
> 지원 건 등 나머지 기능 연동과 `features/` 구조 이전은 진행 중이다.
> 현재 라우트는 `src/app/routes.ts`, 목표 제품 구조는 `../docs/PRODUCT_STRUCTURE.md`와
> `../docs/FEATURE_MODULE_STRUCTURE.md`를 기준으로 확인한다.
> 목표 내비게이션은 핵심 메뉴 + 큰 기능 드롭다운의 하이브리드 구조이며, 목표 URL 규칙은 주요 작업을 path로,
> 목록 필터·정렬을 query로 표현하는 방식이다. 현재 구현과 목표 규칙의 차이는
> `../docs/planning/디자인 분석.md`, `../docs/PRODUCT_STRUCTURE.md`,
> `../docs/FEATURE_MODULE_STRUCTURE.md`를 따른다.

## 경로 별칭

`@/*` → `src/*` (vite.config.ts와 tsconfig.json에 동일하게 설정됨).

## 모바일 / PWA 로드맵

1. **현재** — 반응형 웹 + `manifest.webmanifest`(PWA-ready)
2. **2차** — `vite-plugin-pwa`로 서비스워커·오프라인 캐시·아이콘 추가
3. **3차** — Capacitor로 Android/iOS 패키징 (`android/`, `ios/`는 frontend 내부에서 관리)
