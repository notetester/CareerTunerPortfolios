import { api } from "@/app/lib/api";

export interface AdminNotificationRow {
  id: number;
  userId: number;
  recipientName: string;
  recipientEmail: string;
  type: string;
  title: string;
  message?: string | null;
  read: boolean;
  readAt?: string | null;
  createdAt: string;
}

export function getNotifications(size = 100): Promise<AdminNotificationRow[]> {
  return api<AdminNotificationRow[]>(`/admin/notifications?size=${size}`, { method: "GET" });
}

export interface AdminNotificationStats {
  totalSent: number;
  readCount: number;
  unreadCount: number;
  readRate: number;
  todaySent: number;
  categories: { category: string; sent: number; read: number; rate: number; low: boolean }[];
  trend: { date: string; count: number; today: boolean }[];
}

/** 전체 테이블 집계 통계 (목록 캡과 무관). */
export function getStats(): Promise<AdminNotificationStats> {
  return api<AdminNotificationStats>(`/admin/notifications/stats`, { method: "GET" });
}
