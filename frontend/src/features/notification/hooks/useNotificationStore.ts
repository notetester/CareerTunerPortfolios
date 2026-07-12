import { create } from "zustand";
import * as notificationApi from "../api/notificationApi";
import { toast } from "../components/toast";
import {
  normalizeNotificationRules,
  selectToastNotifications,
} from "../types/preferences";
import { typeMeta } from "../types/notification";
import type { Notification, NotificationCategory } from "../types/notification";
import type { NotificationPreference } from "../api/notificationApi";

interface NotificationState {
  /** 현재 화면에 바인딩된 계정. null이면 인증 확인 중이거나 로그아웃 상태다. */
  accountId: number | null;
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
  deleteNotification: (id: number) => Promise<void>;
  deleteAll: () => Promise<void>;
  setFilter: (category: NotificationCategory) => void;

  /** 알림 설정 로드(끄기 버튼 상태 표시용) */
  fetchPreference: () => Promise<void>;
  /** 푸시 알림 on/off 즉시 토글 */
  setPushEnabled: (enabled: boolean) => Promise<void>;

  /** 현재 필터 기준 목록 */
  filtered: () => Notification[];
}

let notificationRequestGeneration = 0;
let notificationListRequestSequence = 0;
let notificationUnreadRequestSequence = 0;
let notificationPreferenceRequestSequence = 0;

function isCurrentNotificationGeneration(generation: number): boolean {
  return generation === notificationRequestGeneration;
}

function isCurrentListRequest(generation: number, sequence: number): boolean {
  return isCurrentNotificationGeneration(generation)
    && sequence === notificationListRequestSequence;
}

function isCurrentUnreadRequest(generation: number, sequence: number): boolean {
  return isCurrentNotificationGeneration(generation)
    && sequence === notificationUnreadRequestSequence;
}

function isCurrentPreferenceRequest(generation: number, sequence: number): boolean {
  return isCurrentNotificationGeneration(generation)
    && sequence === notificationPreferenceRequestSequence;
}

export const useNotificationStore = create<NotificationState>((set, get) => ({
  accountId: null,
  notifications: [],
  unreadCount: 0,
  loading: false,
  error: null,
  filter: "all",
  preference: null,
  preferenceFetchedAt: 0,
  lastNotifiedId: 0,

  fetchNotifications: async () => {
    if (get().accountId == null) return;
    const generation = notificationRequestGeneration;
    const listSequence = ++notificationListRequestSequence;
    const unreadSequence = ++notificationUnreadRequestSequence;
    set({ loading: true, error: null });
    try {
      const notifications = await notificationApi.getNotifications();
      if (!isCurrentListRequest(generation, listSequence)) return;
      const maxId = notifications.reduce((m, n) => Math.max(m, n.id), get().lastNotifiedId);
      set({ notifications, lastNotifiedId: maxId });
      const unreadCount = await notificationApi.getUnreadCount();
      if (isCurrentUnreadRequest(generation, unreadSequence)) set({ unreadCount });
      if (!isCurrentListRequest(generation, listSequence)) return;
      // 최초/수동 로드는 토스트를 띄우지 않고 기준선만 끌어올린다(로드 시 과거 알림 폭주 방지).
      set({ loading: false });
    } catch (error) {
      if (!isCurrentListRequest(generation, listSequence)) return;
      set({
        loading: false,
        error: error instanceof Error ? error.message : "알림을 불러오지 못했습니다.",
      });
    }
  },

  pollNotifications: async () => {
    if (get().accountId == null) return;
    const generation = notificationRequestGeneration;
    const listSequence = ++notificationListRequestSequence;
    const unreadSequence = ++notificationUnreadRequestSequence;
    try {
      let preference = get().preference;
      if (!preference || Date.now() - get().preferenceFetchedAt > 60_000) {
        const preferenceSequence = ++notificationPreferenceRequestSequence;
        try {
          const loaded = await notificationApi.getNotificationPreferences();
          if (!isCurrentPreferenceRequest(generation, preferenceSequence)) return;
          preference = { ...loaded, rules: normalizeNotificationRules(loaded.rules) };
          set({ preference, preferenceFetchedAt: Date.now() });
        } catch {
          if (!isCurrentPreferenceRequest(generation, preferenceSequence)) return;
          preference = null;
        }
      }
      const notifications = await notificationApi.getNotifications();
      if (!isCurrentListRequest(generation, listSequence)) return;
      const unreadCount = await notificationApi.getUnreadCount();
      if (!isCurrentListRequest(generation, listSequence)) return;
      const prevId = get().lastNotifiedId;
      // 직전 폴링 이후 새로 도착한 미읽음 알림 중, 즉시(urgent) 타입만 토스트로 띄운다.
      // 몰아보기(urgent:false, 예: NEW_TICKET/NEW_USER)는 토스트 없이 뱃지 카운트에만 반영된다.
      // "알림 끄기"(pushEnabled=false)면 selectToastNotifications 가 전부 걸러낸다.
      const fresh = notifications
        .filter((n) => n.id > prevId && !n.isRead)
        .sort((a, b) => a.id - b.id);
      selectToastNotifications(fresh, preference, (type) => typeMeta(type).urgent !== false)
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
      // 토스트를 걸러냈어도 기준선은 끌어올린다 — 알림을 다시 켤 때 밀린 토스트가 폭주하지 않도록.
      const maxId = notifications.reduce((m, n) => Math.max(m, n.id), prevId);
      set({ notifications, lastNotifiedId: maxId });
      if (isCurrentUnreadRequest(generation, unreadSequence)) set({ unreadCount });
    } catch {
      // 폴링 실패는 조용히 무시(다음 주기 재시도)
    }
  },

  fetchUnreadCount: async () => {
    if (get().accountId == null) return;
    const generation = notificationRequestGeneration;
    const unreadSequence = ++notificationUnreadRequestSequence;
    try {
      const unreadCount = await notificationApi.getUnreadCount();
      if (!isCurrentUnreadRequest(generation, unreadSequence)) return;
      set({ unreadCount });
    } catch (error) {
      if (!isCurrentUnreadRequest(generation, unreadSequence)) return;
      set({ error: error instanceof Error ? error.message : "미읽음 알림 수를 불러오지 못했습니다." });
    }
  },

  markAsRead: async (id) => {
    if (get().accountId == null) return;
    const generation = notificationRequestGeneration;
    const wasUnread = get().notifications.some((n) => n.id === id && !n.isRead);
    await notificationApi.markAsRead(id);
    if (!isCurrentNotificationGeneration(generation)) return;
    notificationListRequestSequence += 1;
    notificationUnreadRequestSequence += 1;
    set({
      notifications: get().notifications.map((n) =>
        n.id === id ? { ...n, isRead: true } : n,
      ),
      unreadCount: wasUnread ? Math.max(0, get().unreadCount - 1) : get().unreadCount,
    });
  },

  markAllAsRead: async () => {
    if (get().accountId == null) return;
    const generation = notificationRequestGeneration;
    await notificationApi.markAllAsRead();
    if (!isCurrentNotificationGeneration(generation)) return;
    notificationListRequestSequence += 1;
    notificationUnreadRequestSequence += 1;
    set({
      notifications: get().notifications.map((n) => ({ ...n, isRead: true })),
      unreadCount: 0,
    });
  },

  deleteNotification: async (id) => {
    if (get().accountId == null) return;
    const generation = notificationRequestGeneration;
    const target = get().notifications.find((n) => n.id === id);
    await notificationApi.deleteNotification(id);
    if (!isCurrentNotificationGeneration(generation)) return;
    notificationListRequestSequence += 1;
    notificationUnreadRequestSequence += 1;
    set({
      notifications: get().notifications.filter((n) => n.id !== id),
      unreadCount: target && !target.isRead
        ? Math.max(0, get().unreadCount - 1)
        : get().unreadCount,
    });
  },

  deleteAll: async () => {
    if (get().accountId == null) return;
    const generation = notificationRequestGeneration;
    await notificationApi.deleteAllNotifications();
    if (!isCurrentNotificationGeneration(generation)) return;
    notificationListRequestSequence += 1;
    notificationUnreadRequestSequence += 1;
    set({ notifications: [], unreadCount: 0 });
  },

  fetchPreference: async () => {
    if (get().accountId == null) return;
    const generation = notificationRequestGeneration;
    const preferenceSequence = ++notificationPreferenceRequestSequence;
    try {
      const loaded = await notificationApi.getNotificationPreferences();
      if (!isCurrentPreferenceRequest(generation, preferenceSequence)) return;
      set({
        preference: { ...loaded, rules: normalizeNotificationRules(loaded.rules) },
        preferenceFetchedAt: Date.now(),
      });
    } catch {
      // 설정 로드 실패는 조용히 무시(끄기 버튼은 기본값 표시)
    }
  },

  setPushEnabled: async (enabled) => {
    if (get().accountId == null) return;
    const generation = notificationRequestGeneration;
    const updated = await notificationApi.updateNotificationPreferences({ pushEnabled: enabled });
    if (!isCurrentNotificationGeneration(generation)) return;
    notificationPreferenceRequestSequence += 1;
    set({
      preference: { ...updated, rules: normalizeNotificationRules(updated.rules) },
      preferenceFetchedAt: Date.now(),
    });
  },

  setFilter: (filter) => set({ filter }),

  filtered: () => {
    const { notifications, filter } = get();
    if (filter === "all") return notifications;
    return notifications.filter((n) => n.category === filter);
  },
}));

/** 이전 계정의 목록·뱃지·설정과 진행 중 비동기 응답을 한 번에 폐기한다. */
export function resetNotificationState(accountId: number | null = null): void {
  notificationRequestGeneration += 1;
  notificationListRequestSequence += 1;
  notificationUnreadRequestSequence += 1;
  notificationPreferenceRequestSequence += 1;
  useNotificationStore.setState({
    accountId,
    notifications: [],
    unreadCount: 0,
    loading: false,
    error: null,
    filter: "all",
    preference: null,
    preferenceFetchedAt: 0,
    lastNotifiedId: 0,
  });
}

/** 같은 계정의 /auth/me 재검증은 알림을 비우지 않고, 실제 계정 경계에서만 초기화한다. */
export function ensureNotificationAccount(accountId: number): void {
  if (useNotificationStore.getState().accountId !== accountId) {
    resetNotificationState(accountId);
  }
}
