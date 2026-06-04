# CareerTuner — Frontend

React 18 + Vite + TypeScript + Tailwind CSS v4 (shadcn/ui). PC 웹과 모바일 웹을 모두 대상으로 하는 반응형 SPA이며, 이후 PWA → Capacitor 순으로 모바일 앱으로 확장한다.

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
| `npm run build` | 프로덕션 빌드 (`dist/`) |
| `npm run preview` | 빌드 결과 미리보기 |
| `npm run typecheck` | 타입 검사 (`tsc --noEmit`) |

## 구조

```text
src/
 ├─ main.tsx              앱 진입점
 ├─ app/
 │   ├─ App.tsx           RouterProvider
 │   ├─ routes.ts         라우트 정의
 │   ├─ pages/            화면 (Home, Dashboard, Applications, ApplicationDetail,
 │   │                     AIInterview, Analysis, Community, Pricing, Profile, Login)
 │   └─ components/
 │       ├─ ui/           shadcn/ui 프리미티브
 │       ├─ layout/       Header, Footer, Root
 │       └─ figma/        에셋 헬퍼
 └─ styles/               Tailwind v4 + 디자인 토큰(theme.css)
```

> 이 코드는 Figma Make 디자인 초안을 기반으로 하며, 백엔드 API 연동·상태관리(`store`)·기능별 모듈(`features`)은 이후 단계에서 `docs/planning/기획.txt`의 구조에 맞춰 붙인다.

## 경로 별칭

`@/*` → `src/*` (vite.config.ts와 tsconfig.json에 동일하게 설정됨).

## 모바일 / PWA 로드맵

1. **현재** — 반응형 웹 + `manifest.webmanifest`(PWA-ready)
2. **2차** — `vite-plugin-pwa`로 서비스워커·오프라인 캐시·아이콘 추가
3. **3차** — Capacitor로 Android/iOS 패키징 (`android/`, `ios/`는 frontend 내부에서 관리)
