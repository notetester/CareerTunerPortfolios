import { mockNotifications } from "../data/mockNotifications";
import type { Notification } from "../types/notification";

// TODO: 백엔드 연동 시 mock → api() 호출로 교체
// import { api } from "@/app/lib/api";

const delay = (ms = 300) => new Promise((r) => setTimeout(r, ms));

/** 알림 목록 */
export async function getNotifications(): Promise<Notification[]> {
  await delay();
  return mockNotifications;
}

/** 안 읽은 알림 수 */
export async function getUnreadCount(): Promise<number> {
  await delay(100);
  return mockNotifications.filter((n) => !n.isRead).length;
}

/** 읽음 처리 */
export async function markAsRead(id: number): Promise<void> {
  await delay(200);
  const noti = mockNotifications.find((n) => n.id === id);
  if (noti) noti.isRead = true;
}

/** 전체 읽음 */
export async function markAllAsRead(): Promise<void> {
  await delay(200);
  mockNotifications.forEach((n) => { n.isRead = true; });
}
