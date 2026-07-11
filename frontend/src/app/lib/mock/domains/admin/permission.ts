// 관리자 권한/알림 수신 설정 mock (W4: 관리자 권한 집행 계층).
//   - GET /admin/me/permissions           : 실효 권한 목록(사이드바 메뉴 노출 제어)
//   - GET/PATCH /admin/me/notification-categories : 관리자 알림 opt-out 토글
// static-demo의 관리자 persona는 일반 ADMIN이다. SUPER_ADMIN 전용 화면은 실제 권한처럼 닫는다.
import type { MockContext, MockRoute } from "../../registry";
import { ADMIN_PERMISSION_CODES } from "@/admin/auth/adminAccess";

const demoPermissions = {
  role: "ADMIN",
  superAdmin: false,
  // 일반 관리자 데모는 운영 도메인 권한만 갖고, SUPER 전용 권한은 갖지 않는다.
  permissions: ADMIN_PERMISSION_CODES.filter((code) => !code.startsWith("ADMIN_PERMISSION_")),
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
