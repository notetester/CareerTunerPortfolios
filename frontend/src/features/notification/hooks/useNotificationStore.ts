import { create } from "zustand";
import * as notificationApi from "../api/notificationApi";
import type { Notification, NotificationCategory } from "../types/notification";

interface NotificationState {
  notifications: Notification[];
  unreadCount: number;
  loading: boolean;
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
  filter: "all",

  fetchNotifications: async () => {
    set({ loading: true });
    const notifications = await notificationApi.getNotifications();
    const unreadCount = notifications.filter((n) => !n.isRead).length;
    set({ notifications, unreadCount, loading: false });
  },

  fetchUnreadCount: async () => {
    const unreadCount = await notificationApi.getUnreadCount();
    set({ unreadCount });
  },

  markAsRead: async (id) => {
    await notificationApi.markAsRead(id);
    set({
      notifications: get().notifications.map((n) =>
        n.id === id ? { ...n, isRead: true } : n,
      ),
      unreadCount: Math.max(0, get().unreadCount - 1),
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
