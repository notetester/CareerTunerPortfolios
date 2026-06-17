/**
 * 푸시 알림 등록 어댑터 (docs/planning/모바일 고려.md §5.3, §8.3).
 * - 웹/PWA: 서비스워커 pushManager + VAPID 공개키(VITE_VAPID_PUBLIC_KEY) → web push 구독
 * - 네이티브: 런타임 Capacitor PushNotifications 플러그인(있으면) → FCM/APNs 토큰
 * - 키/플러그인 미설정: 브라우저/OS 권한 요청까지는 동작하고, 구독 생성은 건너뛴다(무해).
 */
import { api } from "@/app/lib/api";
import { isNativeApp, nativePlugin } from "./capacitor";

const VAPID_PUBLIC_KEY = (import.meta.env.VITE_VAPID_PUBLIC_KEY as string | undefined) ?? "";

export type PushRegisterResult =
  | "subscribed"        // 백엔드에 기기 등록 완료
  | "permission-only"   // 권한은 허용됐으나 키/플러그인 없어 구독은 보류
  | "denied"
  | "unsupported";

export function isPushSupported(): boolean {
  if (isNativeApp()) return true;
  return typeof window !== "undefined" && "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;
}

export function pushPermission(): NotificationPermission | "unsupported" {
  if (typeof Notification === "undefined") return "unsupported";
  return Notification.permission;
}

function urlBase64ToUint8Array(base64: string): Uint8Array {
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
      void api<void>("/notifications/push", {
        method: "POST",
        body: JSON.stringify({ kind: "FCM", token }),
      }).then(() => resolve("subscribed")).catch(() => resolve("permission-only"));
    });
    void plugin.register();
  });
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
    if (!isNativeApp() && "serviceWorker" in navigator) {
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
