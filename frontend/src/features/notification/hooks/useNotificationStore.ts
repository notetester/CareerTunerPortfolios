import { create } from "zustand";
// TODO: 백엔드 연동 시 주석 해제
// import * as notificationApi from "../api/notificationApi";
import { mockNotifications } from "../data/mockNotifications";
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
    // TODO: 백엔드 연동 시 notificationApi.getNotifications() 로 교체
    const notifications = mockNotifications;
    const unreadCount = notifications.filter((n) => !n.isRead).length;
    set({ notifications, unreadCount, loading: false });
  },

  fetchUnreadCount: async () => {
    // TODO: 백엔드 연동 시 notificationApi.getUnreadCount() 로 교체
    const unreadCount = get().notifications.filter((n) => !n.isRead).length;
    set({ unreadCount });
  },

  markAsRead: async (id) => {
    // TODO: 백엔드 연동 시 notificationApi.markAsRead(id) 로 교체
    set({
      notifications: get().notifications.map((n) =>
        n.id === id ? { ...n, isRead: true } : n,
      ),
      unreadCount: Math.max(0, get().unreadCount - 1),
    });
  },

  markAllAsRead: async () => {
    // TODO: 백엔드 연동 시 notificationApi.markAllAsRead() 로 교체
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
