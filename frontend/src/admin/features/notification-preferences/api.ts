import { api } from "@/app/lib/api";
import type { AdminNotificationCategories } from "./types";

/** 내 관리자 알림 수신 설정 조회. */
export function getAdminNotificationCategories(): Promise<AdminNotificationCategories> {
  return api<AdminNotificationCategories>("/admin/me/notification-categories", { method: "GET" });
}

/** 관리자 알림 type 하나의 수신 여부 변경(변경 후 전체 설정 반환). */
export function updateAdminNotificationCategory(
  type: string,
  enabled: boolean,
): Promise<AdminNotificationCategories> {
  return api<AdminNotificationCategories>("/admin/me/notification-categories", {
    method: "PATCH",
    body: JSON.stringify({ type, enabled }),
  });
}
