import { api } from "@/app/lib/api";
import { isNativeApp } from "@/platform/capacitor";
import type { Notification, NotificationType, SenderRelation } from "../types/notification";
import { TYPE_TO_CATEGORY } from "../types/notification";
import type { NotificationRulePreference } from "../types/preferences";

interface BackendNotification {
  id: number;
  type: string;
  targetType?: string;
  targetId?: number;
  senderRelation?: string;
  title: string;
  message?: string;
  link?: string;
  read: boolean;
  createdAt: string;
  actor?: {
    id: number | null;
    name: string;
    avatarUrl?: string | null;
  };
}

interface NotificationPageResponse {
  notifications: BackendNotification[];
  total: number;
  page: number;
  size: number;
  hasNext: boolean;
}

function toNotification(n: BackendNotification): Notification {
  const type = n.type as NotificationType;
  return {
    id: n.id,
    type,
    category: TYPE_TO_CATEGORY[type] ?? "notice",
    title: n.title,
    message: n.message,
    link: n.link,
    targetType: n.targetType,
    targetId: n.targetId,
    senderRelation: n.senderRelation as SenderRelation | undefined,
    actorId: n.actor?.id ?? null,
    actorName: n.actor?.name,
    isRead: n.read,
    createdAt: n.createdAt,
  };
}

function destinationQuery(separator: "?" | "&"): string {
  const platform = isNativeApp() ? "MOBILE" : "WEB";
  return `${separator}platform=${platform}`;
}

/** 알림 목록 */
export async function getNotifications(page = 0, size = 20): Promise<Notification[]> {
  const res = await api<NotificationPageResponse>(
    `/notifications?page=${page}&size=${size}${destinationQuery("&")}`,
    {},
  );
  return res.notifications.map(toNotification);
}

/** 안 읽은 알림 수 */
export async function getUnreadCount(): Promise<number> {
  return api<number>(`/notifications/unread-count${destinationQuery("?")}`, {});
}

/** 읽음 처리 */
export async function markAsRead(id: number): Promise<void> {
  await api<void>(`/notifications/${id}/read`, { method: "PATCH" });
}

/** 전체 읽음 */
export async function markAllAsRead(): Promise<void> {
  await api<void>(`/notifications/read-all${destinationQuery("?")}`, { method: "POST" });
}

/** 알림 단건 삭제 */
export async function deleteNotification(id: number): Promise<void> {
  await api<void>(`/notifications/${id}`, { method: "DELETE" });
}

/** 알림 전체 삭제(비우기) */
export async function deleteAllNotifications(): Promise<void> {
  await api<void>(`/notifications${destinationQuery("?")}`, { method: "DELETE" });
}

// ───── 알림 설정 ─────

export interface NotificationPreference {
  pushEnabled: boolean;
  emailEnabled: boolean;
  categories: Record<string, boolean>;
  rules: Record<string, NotificationRulePreference>;
  /** 알림 해제 채팅방에서도 언급으로 간주할 키워드 목록 */
  keywords: string[];
  quietHoursStart: string | null;
  quietHoursEnd: string | null;
  pushDeviceRegistered: boolean;
}

export interface NotificationPreferenceUpdate {
  pushEnabled?: boolean;
  emailEnabled?: boolean;
  categories?: Record<string, boolean>;
  rules?: Record<string, NotificationRulePreference>;
  keywords?: string[];
  quietHoursStart?: string | null;
  quietHoursEnd?: string | null;
}

export function getNotificationPreferences(): Promise<NotificationPreference> {
  return api<NotificationPreference>("/notifications/preferences", { method: "GET" });
}

export function updateNotificationPreferences(update: NotificationPreferenceUpdate): Promise<NotificationPreference> {
  return api<NotificationPreference>("/notifications/preferences", {
    method: "PUT",
    body: JSON.stringify(update),
  });
}
