import { create } from "zustand";
import * as notificationApi from "../api/notificationApi";
import { toast } from "../components/toast";
import {
  isNotificationChannelEnabled,
  isNotificationSenderEnabled,
  normalizeNotificationRules,
} from "../types/preferences";
import { typeMeta } from "../types/notification";
import type { Notification, NotificationCategory } from "../types/notification";
import type { NotificationPreference } from "../api/notificationApi";

interface NotificationState {
  notifications: Notification[];
  unreadCount: number;
  loading: boolean;
  error: string | null;
  filter: NotificationCategory;
  preference: NotificationPreference | null;
  preferenceFetchedAt: number;
  /** 이미 토스트로 알린 최대 알림 id (이보다 큰 미읽음만 새로 띄운다) */
  lastNotifiedId: number;

  fetchNotifications: () => Promise<void>;
  fetchUnreadCount: () => Promise<void>;
  pollNotifications: () => Promise<void>;
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
  preference: null,
  preferenceFetchedAt: 0,
  lastNotifiedId: 0,

  fetchNotifications: async () => {
    set({ loading: true, error: null });
    try {
      const notifications = await notificationApi.getNotifications();
      const unreadCount = await notificationApi.getUnreadCount();
      // 최초/수동 로드는 토스트를 띄우지 않고 기준선만 끌어올린다(로드 시 과거 알림 폭주 방지).
      const maxId = notifications.reduce((m, n) => Math.max(m, n.id), get().lastNotifiedId);
      set({ notifications, unreadCount, loading: false, lastNotifiedId: maxId });
    } catch (error) {
      set({
        notifications: [],
        unreadCount: 0,
        loading: false,
        error: error instanceof Error ? error.message : "알림을 불러오지 못했습니다.",
      });
    }
  },

  pollNotifications: async () => {
    try {
      let preference = get().preference;
      if (!preference || Date.now() - get().preferenceFetchedAt > 60_000) {
        try {
          const loaded = await notificationApi.getNotificationPreferences();
          preference = { ...loaded, rules: normalizeNotificationRules(loaded.rules) };
          set({ preference, preferenceFetchedAt: Date.now() });
        } catch {
          preference = null;
        }
      }
      const notifications = await notificationApi.getNotifications();
      const unreadCount = await notificationApi.getUnreadCount();
      const prevId = get().lastNotifiedId;
      // 직전 폴링 이후 새로 도착한 미읽음 알림 중, 즉시(urgent) 타입만 토스트로 띄운다.
      // 몰아보기(urgent:false, 예: NEW_TICKET/NEW_USER)는 토스트 없이 뱃지 카운트에만 반영된다.
      const fresh = notifications
        .filter((n) => n.id > prevId && !n.isRead)
        .sort((a, b) => a.id - b.id);
      fresh
        .filter((n) => typeMeta(n.type).urgent !== false)
        .filter((n) => isNotificationChannelEnabled(preference, n.type, "webToast"))
        .filter((n) => isNotificationSenderEnabled(preference, n.type, n.senderRelation))
        .forEach((n) => {
        toast.notify({
          type: n.type,
          category: n.category,
          title: n.title,
          message: n.message,
          link: n.link,
          actorName: n.actorName,
        });
      });
      const maxId = notifications.reduce((m, n) => Math.max(m, n.id), prevId);
      set({ notifications, unreadCount, lastNotifiedId: maxId });
    } catch {
      // 폴링 실패는 조용히 무시(다음 주기 재시도)
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
