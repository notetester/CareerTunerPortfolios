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
