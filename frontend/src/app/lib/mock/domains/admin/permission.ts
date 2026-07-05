// 관리자 권한/알림 수신 설정 mock (W4: 관리자 권한 집행 계층).
//   - GET /admin/me/permissions           : 실효 권한 목록(사이드바 메뉴 노출 제어)
//   - GET/PATCH /admin/me/notification-categories : 관리자 알림 opt-out 토글
// 데모 계정은 SUPER_ADMIN 가정 — 전체 메뉴가 보이도록 superAdmin=true 로 응답한다.
import type { MockContext, MockRoute } from "../../registry";

const demoPermissions = {
  role: "SUPER_ADMIN",
  superAdmin: true,
  // 백엔드 admin_permission_policy 카탈로그(20260624·20260702 seed)의 대표 코드들
  permissions: [
    "MEMBER_ADMIN",
    "AI_ADMIN",
    "BILLING_ADMIN",
    "CONTENT_ADMIN",
    "CONTENT_MANAGE",
    "AUDIT_ADMIN",
    "POLICY_ADMIN",
    "POLICY_MANAGE",
  ],
};

// 관리자 알림 type → 수신 여부(키 없으면 수신). 백엔드 ADMIN_OPT_OUT_TYPES 와 1:1.
const notificationCategories: Record<string, boolean> = {
  NEW_REPORT: true,
  NEW_TICKET: true,
  NEW_USER: false,
  NEW_COMPANY_APPLICATION: true,
  NEW_JOB_POSTING_REVIEW: true,
};

interface CategoryPatchBody {
  type?: string;
  enabled?: boolean;
}

export const adminPermissionRoutes: MockRoute[] = [
  {
    method: "GET",
    pattern: /^\/admin\/me\/permissions$/,
    handler: () => ({ ...demoPermissions, permissions: [...demoPermissions.permissions] }),
  },
  {
    method: "GET",
    pattern: /^\/admin\/me\/notification-categories$/,
    handler: () => ({ ...notificationCategories }),
  },
  {
    method: "PATCH",
    pattern: /^\/admin\/me\/notification-categories$/,
    handler: ({ body }: MockContext) => {
      const patch = body as CategoryPatchBody | undefined;
      if (patch?.type && typeof patch.enabled === "boolean") {
        notificationCategories[patch.type] = patch.enabled;
      }
      return { ...notificationCategories };
    },
  },
];
