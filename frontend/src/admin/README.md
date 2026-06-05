# CareerTuner Admin Frontend

관리자 화면은 별도 Vite 앱이 아니라 현재 `frontend` 앱 안에서 관리한다.

```text
frontend/src/admin/
 ├─ components/           관리자 전용 레이아웃, 네비게이션, 공통 UI
 ├─ features/<feature>/   기능별 관리자 화면, API, 훅, 타입
 ├─ hooks/                관리자 전용 공통 훅
 ├─ lib/                  관리자 전용 유틸
 ├─ pages/                관리자 라우트 페이지
 └─ routes.ts             /admin/** 라우트 정의
```

공통 UI 프리미티브와 API 클라이언트는 가능한 한 `frontend/src/app/components/ui`와
`frontend/src/app/lib`를 공유한다. 별도 관리자 앱 분리는 배포/보안/릴리즈 경계가 실제 요구사항이 될 때만 재검토한다.

관리자 기능별 세부 폴더와 담당 범위는 `docs/FEATURE_MODULE_STRUCTURE.md`를 기준으로 한다.
