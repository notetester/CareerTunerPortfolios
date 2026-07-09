/**
 * 푸시 알림 등록 어댑터 (docs/planning/모바일 고려.md §5.3, §8.3).
 * - 웹/PWA: 서비스워커 pushManager + VAPID 공개키(VITE_VAPID_PUBLIC_KEY) → web push 구독
 * - 네이티브: 런타임 Capacitor PushNotifications 플러그인(있으면) → FCM/APNs 토큰
 * - 키/플러그인 미설정: 브라우저/OS 권한 요청까지는 동작하고, 구독 생성은 건너뛴다(무해).
 */
import { api } from "@/app/lib/api";
import { isNativeApp, nativePlugin, platformName } from "./capacitor";
import { toAppPath } from "./deepLink";

// 개발용 기본 VAPID 공개키(비밀 아님) — 백엔드 careertuner.push.vapid.public-key 기본값과 반드시 동일.
// 운영/배포는 VITE_VAPID_PUBLIC_KEY 로 교체하면 코드 변경 없이 적용된다(.env.example 참고).
const DEV_VAPID_PUBLIC_KEY =
  "BIHnlq45n0TUTYx1XCkGjMpap8v_GHYBKqUjrx9O3npe7HL2Nz1TU28u0Kh17q4QjP3w8ZXGJn1RIRQ25SR5Elk";
const VAPID_PUBLIC_KEY =
  (import.meta.env.VITE_VAPID_PUBLIC_KEY as string | undefined) || DEV_VAPID_PUBLIC_KEY;

export type PushRegisterResult =
  | "subscribed"        // 백엔드에 기기 등록 완료
  | "permission-only"   // 권한은 허용됐으나 키/플러그인 없어 구독은 보류
  | "denied"
  | "unsupported";

/** 네이티브 등록 시 발급된 FCM 토큰 저장 키 — disablePush 에서 서버 등록 삭제에 쓴다. */
const FCM_TOKEN_KEY = "ct.fcmToken";

export function isPushSupported(): boolean {
  if (isNativeApp()) return true;
  return typeof window !== "undefined" && "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;
}

export function pushPermission(): NotificationPermission | "unsupported" {
  if (typeof Notification === "undefined") return "unsupported";
  return Notification.permission;
}

function urlBase64ToUint8Array(base64: string): Uint8Array<ArrayBuffer> {
  const padding = "=".repeat((4 - (base64.length % 4)) % 4);
  const b64 = (base64 + padding).replace(/-/g, "+").replace(/_/g, "/");
  const raw = atob(b64);
  const out = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

async function registerNative(): Promise<PushRegisterResult> {
  interface CapPush {
    requestPermissions: () => Promise<{ receive: string }>;
    register: () => Promise<void>;
    addListener: (event: string, cb: (data: { value?: string }) => void) => void;
  }
  const plugin = nativePlugin<CapPush>("PushNotifications");
  if (!plugin) return "permission-only";
  const perm = await plugin.requestPermissions();
  if (perm.receive !== "granted") return "denied";
  return await new Promise<PushRegisterResult>((resolve) => {
    plugin.addListener("registration", (data) => {
      const token = data.value;
      if (!token) { resolve("permission-only"); return; }
      // disablePush 에서 서버 등록을 지울 수 있게 토큰을 보관한다.
      try { localStorage.setItem(FCM_TOKEN_KEY, token); } catch { /* no-op */ }
      void api<void>("/notifications/push", {
        method: "POST",
        body: JSON.stringify({ kind: "FCM", token }),
      }).then(() => resolve("subscribed")).catch(() => resolve("permission-only"));
    });
    void plugin.register();
  });
}

interface CapPushChannel {
  id: string;
  name: string;
  description?: string;
  importance?: number;
  sound?: string;
  vibration?: boolean;
}

interface CapPushInit {
  addListener: (event: string, cb: (data: unknown) => void) => void;
  createChannel?: (channel: CapPushChannel) => Promise<void>;
}

/**
 * Android 알림 채널 4종 — 백엔드 PushMessage(ct_alerts*)의 채널 id 와 정확히 일치해야 한다.
 * FCM 메시지가 androidChannelId 로 채널을 골라 수신자의 소리/진동 설정을 반영한다.
 * 채널 속성은 최초 생성 시 고정되므로(안드로이드 정책) 설정 조합별로 채널을 나눈다.
 */
function createAndroidChannels(plugin: CapPushInit): void {
  if (platformName() !== "android" || !plugin.createChannel) return;
  const channels: CapPushChannel[] = [
    { id: "ct_alerts", name: "알림", description: "소리와 진동이 있는 기본 알림", importance: 4, vibration: true },
    { id: "ct_alerts_sound", name: "알림(소리만)", description: "소리만 울리는 알림", importance: 4, vibration: false },
    { id: "ct_alerts_vibrate", name: "알림(진동만)", description: "진동만 울리는 알림", importance: 4, sound: "", vibration: true },
    { id: "ct_alerts_silent", name: "알림(무음)", description: "소리·진동 없는 조용한 알림", importance: 2, sound: "", vibration: false },
  ];
  for (const channel of channels) {
    void plugin.createChannel(channel).catch(() => {
      /* 채널 생성 실패 시 FCM 이 기본 채널로 폴백 — 무시 */
    });
  }
}

let nativePushInitialized = false;

/**
 * 네이티브 푸시 배선 — 앱 시작 시 1회 호출(App.tsx). 네이티브가 아니면 즉시 반환.
 * 1) Android 알림 채널 4종 생성(소리/진동 제어)
 * 2) 푸시 탭 → 백엔드 FCM 이 넣는 data.url 로 앱 내 이동
 * 3) 포그라운드 수신 → 알림 목록/뱃지 갱신(동적 import 로 순환 의존 회피)
 */
export function initNativePush(navigate: (path: string) => void): void {
  if (nativePushInitialized || !isNativeApp()) return;
  nativePushInitialized = true;

  const plugin = nativePlugin<CapPushInit>("PushNotifications");
  if (!plugin) return;

  createAndroidChannels(plugin);

  // 푸시 알림 탭(백그라운드/종료 상태 포함) → data.url 경로로 이동.
  try {
    plugin.addListener("pushNotificationActionPerformed", (event) => {
      const data = (event as { notification?: { data?: Record<string, unknown> } })?.notification?.data;
      const url = data?.url;
      if (typeof url !== "string" || !url) return;
      const path = url.startsWith("/") ? url : toAppPath(url);
      if (path) navigate(path);
    });
  } catch {
    /* 푸시 탭 이동은 보조라 실패해도 무시 */
  }

  // 포그라운드 수신 — 알림 스토어 폴링을 즉시 트리거해 뱃지/토스트를 갱신한다.
  try {
    plugin.addListener("pushNotificationReceived", () => {
      void import("@/features/notification/hooks/useNotificationStore")
        .then(({ useNotificationStore }) => useNotificationStore.getState().pollNotifications())
        .catch(() => {
          /* no-op */
        });
    });
  } catch {
    /* no-op */
  }
}

/** 권한 요청 + 기기 등록. 사용자가 알림 설정에서 푸시를 켤 때 호출한다. */
export async function enablePush(): Promise<PushRegisterResult> {
  if (!isPushSupported()) return "unsupported";

  if (isNativeApp()) {
    return registerNative();
  }

  // 웹/PWA
  const permission = await Notification.requestPermission();
  if (permission !== "granted") return "denied";

  if (!VAPID_PUBLIC_KEY) {
    // 키 미설정 — 권한은 받았으나 web push 구독은 보류.
    return "permission-only";
  }
  try {
    const reg = await navigator.serviceWorker.ready;
    const sub = await reg.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(VAPID_PUBLIC_KEY),
    });
    const json = sub.toJSON();
    await api<void>("/notifications/push", {
      method: "POST",
      body: JSON.stringify({
        kind: "WEB",
        token: json.endpoint,
        p256dh: json.keys?.p256dh,
        auth: json.keys?.auth,
      }),
    });
    return "subscribed";
  } catch {
    return "permission-only";
  }
}

/** 기기 구독 해제 + 백엔드 등록 삭제. */
export async function disablePush(): Promise<void> {
  try {
    if (isNativeApp()) {
      // 네이티브 — 등록 시 저장해 둔 FCM 토큰으로 서버 등록을 삭제한다.
      let token: string | null = null;
      try { token = localStorage.getItem(FCM_TOKEN_KEY); } catch { /* no-op */ }
      if (token) {
        await api<void>(`/notifications/push?token=${encodeURIComponent(token)}`, { method: "DELETE" }).catch(() => {});
        try { localStorage.removeItem(FCM_TOKEN_KEY); } catch { /* no-op */ }
      }
      return;
    }
    if ("serviceWorker" in navigator) {
      const reg = await navigator.serviceWorker.ready;
      const sub = await reg.pushManager.getSubscription();
      if (sub) {
        const endpoint = sub.endpoint;
        await sub.unsubscribe();
        await api<void>(`/notifications/push?token=${encodeURIComponent(endpoint)}`, { method: "DELETE" }).catch(() => {});
      }
    }
  } catch {
    // 해제 실패는 무시(서버 선호도 off 로 푸시는 막힘).
  }
}
