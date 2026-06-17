import { create } from "zustand";
import * as notificationApi from "../api/notificationApi";
import type { Notification, NotificationCategory } from "../types/notification";

interface NotificationState {
  notifications: Notification[];
  unreadCount: number;
  loading: boolean;
  error: string | null;
  filter: NotificationCategory;

  fetchNotifications: () => Promise<void>;
  fetchUnreadCount: () => Promise<void>;
  markAsRead: (id: number) => Promise<void>;
  markAllAsRead: () => Promise<void>;
  setFilter: (category: NotificationCategory) => void;

  /** 현재 필터 기준 목록 */
  filtered: () => Notification[];
}

export const useNotificationStore = create<NotificationState>((set, get) => ({
  notifications: [],
  unreadCount: 0,
  loading: false,
  error: null,
  filter: "all",

  fetchNotifications: async () => {
    set({ loading: true, error: null });
    try {
      const notifications = await notificationApi.getNotifications();
      const unreadCount = await notificationApi.getUnreadCount();
      set({ notifications, unreadCount, loading: false });
    } catch (error) {
      set({
        notifications: [],
        unreadCount: 0,
        loading: false,
        error: error instanceof Error ? error.message : "알림을 불러오지 못했습니다.",
      });
    }
  },

  fetchUnreadCount: async () => {
    try {
      const unreadCount = await notificationApi.getUnreadCount();
      set({ unreadCount });
    } catch (error) {
      set({ error: error instanceof Error ? error.message : "미읽음 알림 수를 불러오지 못했습니다." });
    }
  },

  markAsRead: async (id) => {
    const wasUnread = get().notifications.some((n) => n.id === id && !n.isRead);
    await notificationApi.markAsRead(id);
    set({
      notifications: get().notifications.map((n) =>
        n.id === id ? { ...n, isRead: true } : n,
      ),
      unreadCount: wasUnread ? Math.max(0, get().unreadCount - 1) : get().unreadCount,
    });
  },

  markAllAsRead: async () => {
    await notificationApi.markAllAsRead();
    set({
      notifications: get().notifications.map((n) => ({ ...n, isRead: true })),
      unreadCount: 0,
    });
  },

  setFilter: (filter) => set({ filter }),

  filtered: () => {
    const { notifications, filter } = get();
    if (filter === "all") return notifications;
    return notifications.filter((n) => n.category === filter);
  },
}));
