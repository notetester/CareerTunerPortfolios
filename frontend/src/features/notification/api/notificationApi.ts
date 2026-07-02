import { api } from "@/app/lib/api";
import type { Notification, NotificationType } from "../types/notification";
import { TYPE_TO_CATEGORY } from "../types/notification";
import type { NotificationRulePreference } from "../types/preferences";

interface BackendNotification {
  id: number;
  type: string;
  targetType?: string;
  targetId?: number;
  title: string;
  message?: string;
  link?: string;
  read: boolean;
  createdAt: string;
  actor?: {
    id: number;
    name: string;
    avatarUrl?: string;
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
    actorId: n.actor?.id,
    actorName: n.actor?.name,
    isRead: n.read,
    createdAt: n.createdAt,
  };
}

/** 알림 목록 */
export async function getNotifications(page = 0, size = 20): Promise<Notification[]> {
  const res = await api<NotificationPageResponse>(
    `/notifications?page=${page}&size=${size}`,
    {},
  );
  return res.notifications.map(toNotification);
}

/** 안 읽은 알림 수 */
export async function getUnreadCount(): Promise<number> {
  return api<number>("/notifications/unread-count", {});
}

/** 읽음 처리 */
export async function markAsRead(id: number): Promise<void> {
  await api<void>(`/notifications/${id}/read`, { method: "PATCH" });
}

/** 전체 읽음 */
export async function markAllAsRead(): Promise<void> {
  await api<void>("/notifications/read-all", { method: "POST" });
}

// ───── 알림 설정 ─────

export interface NotificationPreference {
  pushEnabled: boolean;
  emailEnabled: boolean;
  categories: Record<string, boolean>;
  rules: Record<string, NotificationRulePreference>;
  quietHoursStart: string | null;
  quietHoursEnd: string | null;
  pushDeviceRegistered: boolean;
}

export interface NotificationPreferenceUpdate {
  pushEnabled?: boolean;
  emailEnabled?: boolean;
  categories?: Record<string, boolean>;
  rules?: Record<string, NotificationRulePreference>;
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
