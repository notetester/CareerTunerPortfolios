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

관리자 기능별 세부 폴더와 담당 범위는 [기능 모듈 구조](../../../docs/FEATURE_MODULE_STRUCTURE.md)를 기준으로 한다.

## 권한 경계

관리자 권한의 최종 판단 주체는 백엔드다. 프런트의 메뉴·버튼 숨김과 라우트 가드는 UX와 불필요한
요청 방지를 위한 1차 경계일 뿐이며, API 보호를 대체하지 않는다. 백엔드는
[`AdminRoleOnly`](../../../backend/src/main/java/com/careertuner/admin/permission/annotation/AdminRoleOnly.java),
[`RequireAdminPermission`](../../../backend/src/main/java/com/careertuner/admin/permission/annotation/RequireAdminPermission.java),
[`AdminPermissionInterceptor`](../../../backend/src/main/java/com/careertuner/admin/permission/web/AdminPermissionInterceptor.java)로
역할과 실효 권한을 다시 검증한다. 프런트는 `GET /api/admin/me/permissions`의 서버 응답을 사용하며,
로그인 세션이나 토큰이 바뀌면 사용자별 권한 캐시를 폐기한다.

프런트 권한 코드, 도메인 CRUD 매트릭스, `/admin/**` 라우트 정책의 정본은
[`auth/adminAccess.ts`](auth/adminAccess.ts)다. 새 관리자 라우트는 이 파일에 정책을 먼저 추가하고
[`routes.ts`](routes.ts)의 `adminRoute()`로 등록한다. 페이지나 사이드바에 별도 권한 문자열 목록을
복제하지 말고 [`auth/useAdminAuthorization.ts`](auth/useAdminAuthorization.ts)의 `can()` 또는
`useAdminDomainAuthorization()`을 사용한다.

모든 관리자 라우트는 [`AdminRouteBoundary`](auth/AdminRouteBoundary.tsx)를 통과한다. 따라서 주소를 직접
입력해도 비로그인 사용자는 원래 경로를 `returnTo`에 보존한 로그인 화면으로 이동하고, 일반 회원·권한 없는
관리자는 403 화면을 본다. 일반 `ADMIN`의 권한 조회가 실패하거나 아직 확정되지 않은 경우에도 화면을 먼저
열지 않고 fail-closed로 차단하며, 비인가 페이지의 lazy import도 시작하지 않는다. `SUPER_ADMIN`만 서버
역할을 기준으로 전체 관리자 화면을 허용한다.

## 검증

프런트 디렉터리에서 관리자 라우트, 메뉴, mock API, CRUD 버튼의 권한 계약을 함께 검사한다.

```bash
cd frontend
npm run test:admin-access
```

검증 구현은 [`scripts/test-admin-access.mjs`](../../scripts/test-admin-access.mjs)에 있고 실행 스크립트는
[`package.json`](../../package.json)의 `test:admin-access`가 정본이다.
